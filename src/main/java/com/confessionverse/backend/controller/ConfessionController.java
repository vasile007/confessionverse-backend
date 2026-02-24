package com.confessionverse.backend.controller;



import com.confessionverse.backend.dto.requestDTO.ConfessionRequestDTO;
import com.confessionverse.backend.dto.responseDTO.ConfessionResponseDTO;
import com.confessionverse.backend.mapper.EntityDtoMapper;
import com.confessionverse.backend.model.Confession;
import com.confessionverse.backend.model.User;
import com.confessionverse.backend.repository.ConfessionRepository;
import com.confessionverse.backend.repository.UserRepository;
import com.confessionverse.backend.security.ownership.OwnershipUtil;
import com.confessionverse.backend.service.ConfessionService;
import com.confessionverse.backend.service.UserService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Optional;

@RequestMapping("/api/confessions")
@RestController
@RequiredArgsConstructor
public class ConfessionController {

    private final ConfessionRepository confessionRepository;
    private final UserRepository userRepository;
    private final EntityDtoMapper mapper;
    private final OwnershipUtil ownershipUtil;
    private final UserService userService;
    private final ConfessionService confessionService;

    // 1. PUBLIC endpoint - returns all confessions (anonymous)
    @GetMapping("/public")
    public ResponseEntity<List<ConfessionResponseDTO>> getAllPublicConfessions() {
        List<Confession> confessions = confessionRepository.findAllPublicWithUserOrderByCreatedAtDesc();

        // Hide user information (preserve anonymity)
        List<ConfessionResponseDTO> response = confessions.stream()
                .map(mapper::toConfessionResponseDTO)
                .peek(dto -> dto.setAuthor("Anonymous"))
                .toList();

        return ResponseEntity.ok(response);
    }

    /**
     * Returns public confessions for the requested username, newest first.
     * Anonymity behavior is kept consistent with the general public feed.
     */
    @GetMapping("/public/by-user/{username}")
    @PreAuthorize("isAuthenticated()")
    public ResponseEntity<List<ConfessionResponseDTO>> getPublicConfessionsByUser(@PathVariable String username) {
        return ResponseEntity.ok(confessionService.getPublicConfessionsByUsername(username));
    }

    // 2. Create confession - authenticated user only
    @PostMapping
    @PreAuthorize("hasRole('USER') or hasRole('ADMIN')")
    public ResponseEntity<ConfessionResponseDTO> createConfession(
            @Valid @RequestBody ConfessionRequestDTO request,
            @AuthenticationPrincipal UserDetails userDetails) {

        if (userDetails == null) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).build();
        }

        User user = userRepository.findByEmail(userDetails.getUsername()).orElse(null);
        if (user == null) {
            return ResponseEntity.badRequest().build();
        }

        Confession confession = mapper.toEntity(request, user);
        confessionRepository.save(confession);
        ConfessionResponseDTO response = mapper.toConfessionResponseDTO(confession);
        response.setAuthor("Anonymous"); // anonymize

        return ResponseEntity.status(HttpStatus.CREATED).body(response);
    }

    // ✅ 3. Confesiunile userului curent (profil)
    @GetMapping("/my")
    @PreAuthorize("hasRole('USER') or hasRole('ADMIN')")
    public ResponseEntity<List<ConfessionResponseDTO>> getUserConfessions(
            @AuthenticationPrincipal UserDetails userDetails) {

        if (userDetails == null)
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).build();

        User currentUser = userRepository.findByEmail(userDetails.getUsername())
                .orElseThrow(() -> new RuntimeException("User not found"));

        List<Confession> confessions = confessionRepository.findByUserId(currentUser.getId());

        List<ConfessionResponseDTO> response = confessions.stream()
                .map(mapper::toConfessionResponseDTO)
                .peek(dto -> dto.setAuthor("You")) // only the author sees it as theirs
                .toList();

        return ResponseEntity.ok(response);
    }

    // 4. Confession by ID - owner or admin only
    @GetMapping("/{id}")
    @PreAuthorize("hasRole('ADMIN') or @ownershipUtil.checkOwnership(T(com.confessionverse.backend.model.Confession), #id, authentication.name)")
    public ResponseEntity<ConfessionResponseDTO> getConfessionById(
            @PathVariable Long id,
            @AuthenticationPrincipal UserDetails userDetails) {

        Optional<Confession> optionalConfession = confessionRepository.findById(id);
        if (optionalConfession.isEmpty()) {
            return ResponseEntity.notFound().build();
        }

        Confession confession = optionalConfession.get();
        ConfessionResponseDTO dto = mapper.toConfessionResponseDTO(confession);
        dto.setAuthor("Anonymous");
        return ResponseEntity.ok(dto);
    }

    // 5. Delete confession
    @DeleteMapping("/{id}")
    @PreAuthorize("hasRole('ADMIN') or @ownershipUtil.checkOwnership(T(com.confessionverse.backend.model.Confession), #id, authentication.name)")
    public ResponseEntity<?> deleteConfession(
            @PathVariable Long id,
            @AuthenticationPrincipal UserDetails userDetails) {

        if (userDetails == null)
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).build();

        boolean hasAccess = ownershipUtil.checkOwnership(Confession.class, id, userDetails.getUsername());
        if (!hasAccess)
            return ResponseEntity.status(HttpStatus.FORBIDDEN).body("Forbidden");

        confessionRepository.deleteById(id);
        return ResponseEntity.ok().body("Deleted");
    }

    // 6. Update confession
    @PutMapping("/{id}")
    @PreAuthorize("hasRole('ADMIN') or @ownershipUtil.checkOwnership(T(com.confessionverse.backend.model.Confession), #id, authentication.name)")
    public ResponseEntity<ConfessionResponseDTO> updateConfession(
            @PathVariable Long id,
            @Valid @RequestBody ConfessionRequestDTO request,
            @AuthenticationPrincipal UserDetails userDetails) {

        Optional<Confession> optionalConfession = confessionRepository.findById(id);
        if (optionalConfession.isEmpty())
            return ResponseEntity.notFound().build();

        Confession existingConfession = optionalConfession.get();
        existingConfession.setContent(request.getContent());
        confessionRepository.save(existingConfession);

        ConfessionResponseDTO responseDTO = mapper.toConfessionResponseDTO(existingConfession);
        responseDTO.setAuthor("Anonymous");
        return ResponseEntity.ok(responseDTO);
    }

    @GetMapping
    public ResponseEntity<List<ConfessionResponseDTO>> getAllPublicConfessionsDefault() {
        List<Confession> confessions = confessionRepository.findAllPublicWithUserOrderByCreatedAtDesc();
        List<ConfessionResponseDTO> response = confessions.stream()
                .map(mapper::toConfessionResponseDTO)
                .peek(dto -> dto.setAuthor("Anonymous"))
                .toList();
        return ResponseEntity.ok(response);
    }

}
