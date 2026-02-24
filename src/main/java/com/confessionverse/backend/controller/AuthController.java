package com.confessionverse.backend.controller;

import com.confessionverse.backend.dto.UserDTO;
import com.confessionverse.backend.dto.requestDTO.ForgotPasswordRequest;
import com.confessionverse.backend.dto.requestDTO.ResetPasswordRequest;
import com.confessionverse.backend.dto.requestDTO.AuthRequest;
import com.confessionverse.backend.dto.responseDTO.AuthResponse;
import com.confessionverse.backend.dto.RegisterRequest;
import com.confessionverse.backend.model.Role;
import com.confessionverse.backend.model.User;
import com.confessionverse.backend.repository.UserRepository;
import com.confessionverse.backend.security.JwtUtil;
import com.confessionverse.backend.service.CustomUserDetailsService;
import com.confessionverse.backend.service.ChatRoomService;
import com.confessionverse.backend.service.PasswordResetService;
import com.confessionverse.backend.service.UserService;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;

import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.validation.BindingResult;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/auth")
@Validated
@CrossOrigin(origins = "http://localhost:5173") // React is allowed here
public class AuthController {

    private final PasswordEncoder passwordEncoder;
    private final UserRepository userRepository;
    private final UserService userService;
    private final AuthenticationManager authenticationManager;
    private final JwtUtil jwtUtil;
    private final CustomUserDetailsService userDetailsService;
    private final PasswordResetService passwordResetService;
    private final ChatRoomService chatRoomService;

    public AuthController(PasswordEncoder passwordEncoder,
                          UserRepository userRepository,
                          AuthenticationManager authenticationManager,
                          JwtUtil jwtUtil,
                          CustomUserDetailsService userDetailsService,
                          UserService userService,
                          PasswordResetService passwordResetService,
                          ChatRoomService chatRoomService) {
        this.passwordEncoder = passwordEncoder;
        this.userRepository = userRepository;
        this.authenticationManager = authenticationManager;
        this.jwtUtil = jwtUtil;
        this.userDetailsService = userDetailsService;
        this.userService = userService;
        this.passwordResetService = passwordResetService;
        this.chatRoomService = chatRoomService;
    }

    @GetMapping("/me")
    @PreAuthorize("hasAnyRole('USER', 'ADMIN')")
    public ResponseEntity<UserDTO> getMyProfile(Authentication authentication) {
        String username = authentication.getName();
        UserDTO userDto = userService.getUserByEmail(username);
        return ResponseEntity.ok(userDto);
    }

    @GetMapping("/admin-only")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<Map<String, String>> adminStuff() {
        return ResponseEntity.ok(Map.of("message", "Welcome, admin!"));
    }

    @PostMapping("/login")
    public ResponseEntity<?> login(@RequestBody @Valid AuthRequest request) {
        try {
            Authentication authentication = authenticationManager.authenticate(
                    new UsernamePasswordAuthenticationToken(request.getEmail(), request.getPassword())
            );
            SecurityContextHolder.getContext().setAuthentication(authentication);

            UserDetails userDetails = (UserDetails) authentication.getPrincipal();
            User user = userRepository.findByEmail(userDetails.getUsername())
                    .orElseThrow(() -> new RuntimeException("User not found"));

            String jwt = jwtUtil.generateToken(user.getEmail(), user.getEmail(), user.getRole().name());

            return ResponseEntity.ok(new AuthResponse(jwt, user.getId(), user.getUsername()));
        } catch (BadCredentialsException e) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED)
                    .body(Map.of("error", "Invalid email or password"));
        }
    }

    @PostMapping("/register")
    public ResponseEntity<?> register( @RequestBody @Valid RegisterRequest request, BindingResult bindingResult) {
        if (bindingResult.hasErrors()) {
            // Log the request and validation errors in the console
            System.out.println("Received register request: " + request);
            System.out.println("Validation errors: " + bindingResult.getAllErrors());

            List<String> errors = bindingResult.getFieldErrors().stream()
                    .map(fe -> fe.getField() + ": " + fe.getDefaultMessage())
                    .toList();

            return ResponseEntity.badRequest().body(Map.of("validationErrors", errors));
        }

        if (userRepository.existsByUsername(request.getUsername())) {
            return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                    .body(Map.of("error", "Username already exists"));
        }
        if (userRepository.existsByEmail(request.getEmail())) {
            return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                    .body(Map.of("error", "Email already exists"));
        }

        User user = new User();
        user.setUsername(request.getUsername());
        user.setEmail(request.getEmail());
        user.setPasswordHash(passwordEncoder.encode(request.getPassword()));
        user.setRole(Role.USER);
        user.setPremium(false);

        userRepository.save(user);
        chatRoomService.ensureStandardMembership(user);

        String jwt = jwtUtil.generateToken(user.getEmail(), user.getEmail(), user.getRole().name());

        return ResponseEntity.status(HttpStatus.CREATED)
                .body(new AuthResponse(jwt, user.getId(), user.getUsername()));
    }

    @PostMapping("/forgot-password")
    public ResponseEntity<Map<String, String>> forgotPassword(@RequestBody @Valid ForgotPasswordRequest request) {
        try {
            passwordResetService.requestPasswordReset(request.getEmail());
            return ResponseEntity.ok(Map.of("message", "Reset email sent successfully."));
        } catch (IllegalArgumentException ex) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND)
                    .body(Map.of("error", ex.getMessage()));
        } catch (RuntimeException ex) {
            return ResponseEntity.status(HttpStatus.SERVICE_UNAVAILABLE)
                    .body(Map.of("error", "Password reset email service is unavailable. Please try again later."));
        }
    }

    @PostMapping("/reset-password")
    public ResponseEntity<Map<String, String>> resetPassword(@RequestBody @Valid ResetPasswordRequest request) {
        passwordResetService.resetPassword(request.getToken(), request.getNewPassword());
        return ResponseEntity.ok(Map.of("message", "Password was reset successfully."));
    }

    @DeleteMapping("/delete/{id}")
    public ResponseEntity<?> deleteUser(@PathVariable Long id, Authentication authentication) {
        String currentEmail = authentication.getName();
        User currentUser = userRepository.findByEmail(currentEmail)
                .orElseThrow(() -> new RuntimeException("Current user not found"));

        return userRepository.findById(id)
                .map(user -> {
                    // Allow deletion if the user is admin or the current user
                    if (currentUser.getRole() == Role.ADMIN || currentUser.getId().equals(user.getId())) {
                        userRepository.delete(user);
                        return ResponseEntity.ok(Map.of("message", "User deleted successfully"));
                    } else {
                        return ResponseEntity.status(HttpStatus.FORBIDDEN).body(Map.of("error", "Access denied"));
                    }
                })
                .orElseGet(() -> ResponseEntity.status(HttpStatus.NOT_FOUND).body(Map.of("error", "User not found")));
    }
    @PutMapping("/update/{id}")
    public ResponseEntity<?> updateUser(@PathVariable Long id,
                                        @RequestBody @Valid UserDTO userDTO,
                                        Authentication authentication) {
        String currentEmail = authentication.getName();
        User currentUser = userRepository.findByEmail(currentEmail)
                .orElseThrow(() -> new RuntimeException("Current user not found"));

        return userRepository.findById(id)
                .map(user -> {
                    // Allow update if admin or the current user
                    if (currentUser.getRole() == Role.ADMIN || currentUser.getId().equals(user.getId())) {

                        // Validate username/email uniqueness if changed
                        if (!user.getUsername().equals(userDTO.getUsername()) && userRepository.existsByUsername(userDTO.getUsername())) {
                            return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(Map.of("error", "Username already exists"));
                        }
                        if (!user.getEmail().equals(userDTO.getEmail()) && userRepository.existsByEmail(userDTO.getEmail())) {
                            return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(Map.of("error", "Email already exists"));
                        }

                        // Update fields (adapt based on what you want to update)
                        user.setUsername(userDTO.getUsername());
                        user.setEmail(userDTO.getEmail());
                        // If you want to allow password changes, create a separate endpoint with validation + hash

                        userRepository.save(user);

                        return ResponseEntity.ok(Map.of("message", "User updated successfully"));
                    } else {
                        return ResponseEntity.status(HttpStatus.FORBIDDEN).body(Map.of("error", "Access denied"));
                    }
                })
                .orElseGet(() -> ResponseEntity.status(HttpStatus.NOT_FOUND).body(Map.of("error", "User not found")));
    }
}


