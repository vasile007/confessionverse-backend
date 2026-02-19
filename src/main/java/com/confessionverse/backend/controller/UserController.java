package com.confessionverse.backend.controller;

import com.confessionverse.backend.dto.UserDTO;
import com.confessionverse.backend.dto.UserProfileUpdateDTO;
import com.confessionverse.backend.dto.responseDTO.PublicUserProfileDTO;
import com.confessionverse.backend.model.User;
import com.confessionverse.backend.service.UserService;
import com.confessionverse.backend.service.SubscriptionEntitlementService;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;


import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardCopyOption;
import java.util.List;
import java.util.Map;
import java.util.UUID;

@RestController
@RequestMapping("/api/users")
@CrossOrigin(origins = "*")
public class UserController {

    private final UserService userService;
    private final SubscriptionEntitlementService subscriptionEntitlementService;

    public UserController(UserService userService,
                          SubscriptionEntitlementService subscriptionEntitlementService) {
        this.userService = userService;
        this.subscriptionEntitlementService = subscriptionEntitlementService;
    }

    @PreAuthorize("hasRole('ADMIN')")
    @GetMapping
    public ResponseEntity<?> getAllUsers() {
        List<UserDTO> users = userService.getAllUsers();
        if (users.isEmpty()) {
            return ResponseEntity.status(HttpStatus.OK).body("No users found");
        }
        return ResponseEntity.ok(users);
    }

    @PostMapping
    @PreAuthorize("hasRole('ADMIN')")
    public UserDTO createUser(@RequestBody UserDTO userDto) {
        return userService.createUser(userDto);
    }

    @PutMapping("/{id}")
    @PreAuthorize("hasRole('ADMIN') or @ownershipUtil.isOwner(#id, authentication.name)")

    public UserDTO updateUser(@PathVariable Long id, @RequestBody UserDTO userDto) {
        return userService.updateUser(id, userDto);
    }

    @DeleteMapping("/{id}")
    @PreAuthorize("hasRole('ADMIN')")
    public void deleteUser(@PathVariable Long id) {
        userService.deleteUser(id);
    }


    @PutMapping("/me")
    @PreAuthorize("hasAnyRole('USER', 'ADMIN')")
    public ResponseEntity<UserDTO> updateMyProfile(
            @RequestBody @Valid UserProfileUpdateDTO userDto,
            Authentication authentication) {
        String email = authentication.getName(); // vine din JWT
        UserDTO updated = userService.updateUserByEmail(email, userDto);
        return ResponseEntity.ok(updated);
    }

    @GetMapping("/me")
    @PreAuthorize("hasAnyRole('USER', 'ADMIN')")
    public ResponseEntity<UserDTO> getMyProfile(Authentication authentication) {
        String email = authentication.getName(); // vine din JWT
        UserDTO user = userService.getUserByEmail(email);
        return ResponseEntity.ok(user);
    }



    @GetMapping("/user/{id}")
    @PreAuthorize("hasRole('ADMIN') or @ownershipUtil.isOwner(#id, authentication.name)")

    public ResponseEntity<?> getUserById(@PathVariable Long id) {
        UserDTO user = userService.getUserById(id);
        if (user == null) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND).body("User not found");
        }
        return ResponseEntity.ok(user);
    }

    @GetMapping("/search")
    @PreAuthorize("isAuthenticated()")
    public List<Map<String, Object>> search(@RequestParam String query) {
        String q = query.toLowerCase();
        return userService.searchUsers(q).stream()
                .map(u -> {
                    Map<String, Object> m = new java.util.HashMap<>();
                    SubscriptionEntitlementService.PlanSnapshot planSnapshot =
                            subscriptionEntitlementService.getPlanSnapshot(u.getId());
                    m.put("id", u.getId());
                    m.put("username", u.getUsername());
                    m.put("email", u.getEmail());
                    m.put("premium", planSnapshot.premium());
                    m.put("planType", planSnapshot.planType());
                    return m;
                })
                .toList();
    }

    /**
     * Returns a safe public profile view used by the public profile page.
     */
    @GetMapping("/public/{username}")
    @PreAuthorize("isAuthenticated()")
    public ResponseEntity<PublicUserProfileDTO> getPublicProfile(@PathVariable String username) {
        return ResponseEntity.ok(userService.getPublicProfileByUsername(username));
    }

    /*

    ///  pentru poze la avatar pe viitor !!!
    @PostMapping("/me/avatar")
    @PreAuthorize("isAuthenticated()")
    public ResponseEntity<?> uploadAvatar(
            @RequestParam("avatar") MultipartFile file,
            Authentication authentication) throws IOException {

        String username = authentication.getName();
        User user = userService.getUserEntityByUsername(username);

        // Ex: salvează în folderul avatars/
        String fileName = UUID.randomUUID() + "_" + file.getOriginalFilename();
        Path uploadPath = Paths.get("uploads/avatars");
        Files.createDirectories(uploadPath);
        Path filePath = uploadPath.resolve(fileName);
        Files.copy(file.getInputStream(), filePath, StandardCopyOption.REPLACE_EXISTING);

        String avatarUrl = "/uploads/avatars/" + fileName;
        user.setAvatar(avatarUrl);
        userService.save(user);

        return ResponseEntity.ok(Map.of("avatarUrl", avatarUrl));
    }
     */

}
