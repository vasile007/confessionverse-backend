package com.confessionverse.backend.controller;

import com.confessionverse.backend.dto.requestDTO.BoostRequestDTO;
import com.confessionverse.backend.dto.responseDTO.BoostResponseDTO;
import com.confessionverse.backend.mapper.EntityDtoMapper;
import com.confessionverse.backend.model.Boost;
import com.confessionverse.backend.model.Confession;
import com.confessionverse.backend.model.User;
import com.confessionverse.backend.repository.BoostRepository;
import com.confessionverse.backend.repository.ConfessionRepository;
import com.confessionverse.backend.repository.UserRepository;
import com.confessionverse.backend.security.ownership.OwnershipUtil;
import com.confessionverse.backend.service.UserService;

import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/boosts")
@CrossOrigin(origins = "*")
@RequiredArgsConstructor
public class BoostController  {

    private final BoostRepository boostRepository;
    private final UserRepository userRepository;
    private final ConfessionRepository confessionRepository;
    private final EntityDtoMapper mapper;
    private final OwnershipUtil ownershipUtil;


    private final UserService userService;  // Pentru user info în verificări

    @GetMapping
    @PreAuthorize("hasAnyRole('USER', 'ADMIN')")
    public List<BoostResponseDTO> getAllBoosts(Authentication authentication) {
        String currentEmail = authentication.getName();
        if (ownershipUtil.isAdmin(currentEmail)) {
            return boostRepository.findAll().stream()
                    .map(mapper::toResponseDto)
                    .toList();
        } else {
            User currentUser = userRepository.findByEmail(currentEmail)
                    .orElseThrow(() -> new RuntimeException("User not found"));
            return boostRepository.findByUser(currentUser).stream()
                    .map(mapper::toResponseDto)
                    .toList();
        }
    }


    @PostMapping
    @PreAuthorize("hasAnyRole('USER', 'ADMIN')")
    public ResponseEntity<?> createBoost(@Valid @RequestBody BoostRequestDTO dto, Authentication authentication) {
        String currentEmail = authentication.getName();

        // Verifică dacă user-ul din token e cel care creează boost-ul sau admin
        if (!ownershipUtil.isAdmin(currentEmail) && !userOwnsBoost(dto.getUserId(), currentEmail)) {
            return ResponseEntity.status(403).build();
        }

        User user = userRepository.findById(dto.getUserId())
                .orElseThrow(() -> new RuntimeException("User not found"));
        Confession confession = confessionRepository.findById(dto.getConfessionId())
                .orElseThrow(() -> new RuntimeException("Confession not found"));

        Boost boost = mapper.toEntity(dto, user, confession);
        boostRepository.save(boost);

        return ResponseEntity.ok(mapper.toResponseDto(boost));
    }

    @PutMapping("/{id}")
    @PreAuthorize("@ownershipUtil.checkOwnership(T(com.confessionverse.backend.model.Boost), #id, authentication.name)")
    public ResponseEntity<BoostResponseDTO> updateBoost(@PathVariable Long id, @RequestBody BoostRequestDTO dto) {
        Boost boost = boostRepository.findById(id)
                .orElseThrow(() -> new RuntimeException("Boost not found"));

        boost.setBoostType(com.confessionverse.backend.model.BoostType.valueOf(dto.getBoostType()));
        boostRepository.save(boost);

        return ResponseEntity.ok(mapper.toResponseDto(boost));
    }

    @DeleteMapping("/{id}")
    @PreAuthorize("@ownershipUtil.checkOwnership(T(com.confessionverse.backend.model.Boost), #id, authentication.name)")
    public ResponseEntity<Void> deleteBoost(@PathVariable Long id) {
        if (!boostRepository.existsById(id)) {
            return ResponseEntity.notFound().build();
        }
        boostRepository.deleteById(id);
        return ResponseEntity.noContent().build();
    }

    private boolean userOwnsBoost(Long userId, String email) {
        Long currentUserId = userService.getUserIdByEmail(email);
        return currentUserId != null && currentUserId.equals(userId);
    }

    @GetMapping("/{id}")
    @PreAuthorize("hasAnyRole('USER', 'ADMIN')")
    public BoostResponseDTO getBoostById(@PathVariable Long id, Authentication authentication) {
        String currentEmail = authentication.getName();
        Boost boost = boostRepository.findById(id)
                .orElseThrow(() -> new RuntimeException("Boost not found"));

        User currentUser = userRepository.findByEmail(currentEmail)
                .orElseThrow(() -> new RuntimeException("User not found"));

        if (!ownershipUtil.isAdmin(currentEmail) && !boost.getOwnerId().equals(currentUser.getId())) {
            throw new AccessDeniedException("Access denied");
        }

        return mapper.toResponseDto(boost);
    }
}



