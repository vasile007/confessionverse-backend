

package com.confessionverse.backend.service;

import com.confessionverse.backend.dto.ConfessionDTO;
import com.confessionverse.backend.dto.responseDTO.ConfessionResponseDTO;
import com.confessionverse.backend.exception.ResourceNotFoundException;
import com.confessionverse.backend.mapper.EntityDtoMapper;
import com.confessionverse.backend.model.Confession;
import com.confessionverse.backend.model.User;

import com.confessionverse.backend.repository.ConfessionRepository;
import com.confessionverse.backend.repository.ConfessionReportRepository;
import com.confessionverse.backend.repository.ConfessionVoteRepository;
import com.confessionverse.backend.repository.BoostRepository;
import com.confessionverse.backend.repository.UserRepository;
import com.confessionverse.backend.security.ownership.OwnableService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Optional;
import java.util.stream.Collectors;

@Service
public class ConfessionService implements OwnableService<Confession> {

    private final ConfessionRepository confessionRepository;
    private final UserRepository userRepository;
    private final EntityDtoMapper mapper;
    private final ConfessionVoteRepository confessionVoteRepository;
    private final ConfessionReportRepository confessionReportRepository;
    private final BoostRepository boostRepository;

    @Autowired
    public ConfessionService(ConfessionRepository confessionRepository,
                             UserRepository userRepository,
                             EntityDtoMapper mapper,
                             ConfessionVoteRepository confessionVoteRepository,
                             ConfessionReportRepository confessionReportRepository,
                             BoostRepository boostRepository) {
        this.confessionRepository = confessionRepository;
        this.userRepository = userRepository;
        this.mapper = mapper;
        this.confessionVoteRepository = confessionVoteRepository;
        this.confessionReportRepository = confessionReportRepository;
        this.boostRepository = boostRepository;
    }

    public List<ConfessionDTO> getAllConfessions() {
        return confessionRepository.findAll().stream()
                .map(mapper::toConfessionDTO)
                .collect(Collectors.toList());
    }

    public ConfessionDTO getConfessionById(Long id) {
        Confession confession = confessionRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Confession not found with id " + id));
        return mapper.toConfessionDTO(confession);
    }

    public ConfessionDTO createConfession(ConfessionDTO dto, Long authenticatedUserId) {
        // Obține user-ul autentificat
        User user = userRepository.findById(authenticatedUserId)
                .orElseThrow(() -> new ResourceNotFoundException("User not found with id " + authenticatedUserId));

        // Creează confession folosind mapper care setează user-ul
        Confession confession = mapper.toEntity(dto, user);

        // Salvează în baza de date
        Confession saved = confessionRepository.save(confession);

        // Returnează DTO
        return mapper.toConfessionDTO(saved);
    }

    public List<ConfessionResponseDTO> getPublicConfessionsByUsername(String username) {
        userRepository.findByUsernameIgnoreCase(username)
                .orElseThrow(() -> new ResourceNotFoundException("User not found with username: " + username));

        return confessionRepository.findByUserUsernameIgnoreCaseOrderByCreatedAtDesc(username).stream()
                .map(mapper::toConfessionResponseDTO)
                .peek(dto -> dto.setAuthor("Anonim"))
                .collect(Collectors.toList());
    }


    public ConfessionDTO updateConfession(Long id, ConfessionDTO dto) {
        Confession existing = confessionRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Confession not found with id " + id));
        existing.setContent(dto.getContent());
        // User update dacă e nevoie (sau ignorat)
        Confession updated = confessionRepository.save(existing);
        return mapper.toConfessionDTO(updated);
    }

    public void deleteConfession(Long id) {
        if (!confessionRepository.existsById(id)) {
            throw new ResourceNotFoundException("Confession not found with id " + id);
        }
        confessionRepository.deleteById(id);
    }

    @Transactional
    public void deleteConfessionByAdmin(Long confessionId) {
        Confession confession = confessionRepository.findById(confessionId)
                .orElseThrow(() -> new ResourceNotFoundException("Confession not found with id " + confessionId));

        confessionVoteRepository.deleteAllByConfessionId(confessionId);
        confessionReportRepository.deleteAllByConfessionId(confessionId);
        boostRepository.deleteAllByConfessionId(confessionId);
        confessionRepository.delete(confession);
    }

    @Transactional
    public ConfessionResponseDTO hideConfessionByAdmin(Long confessionId) {
        Confession confession = confessionRepository.findById(confessionId)
                .orElseThrow(() -> new ResourceNotFoundException("Confession not found with id " + confessionId));
        confession.setHidden(true);
        Confession updated = confessionRepository.save(confession);
        return mapper.toConfessionResponseDTO(updated);
    }

    @Override
    public Optional<Confession> getById(Long id) {
        return Optional.ofNullable(confessionRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Confession not found")));
    }

    @Override
    public Class<Confession> getEntityClass() {
        return Confession.class;
    }
}
