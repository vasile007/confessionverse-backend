package com.confessionverse.backend.service;

import com.confessionverse.backend.dto.BoostDTO;
import com.confessionverse.backend.exception.ResourceNotFoundException;
import com.confessionverse.backend.mapper.EntityDtoMapper;
import com.confessionverse.backend.model.Boost;
import com.confessionverse.backend.model.BoostType;
import com.confessionverse.backend.model.Confession;
import com.confessionverse.backend.model.User;
import com.confessionverse.backend.repository.BoostRepository;
import com.confessionverse.backend.repository.ConfessionRepository;
import com.confessionverse.backend.repository.UserRepository;

import com.confessionverse.backend.security.ownership.OwnableService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Optional;
import java.util.stream.Collectors;

@Service
public class BoostService implements OwnableService<Boost> {


    private final BoostRepository boostRepository;
    private final UserRepository userRepository;
    private final ConfessionRepository confessionRepository;
    private final EntityDtoMapper mapper;

    @Autowired
    public BoostService(BoostRepository repository, BoostRepository boostRepository,
                        UserRepository userRepository,
                        ConfessionRepository confessionRepository,
                        EntityDtoMapper mapper) {

        this.boostRepository = boostRepository;
        this.userRepository = userRepository;
        this.confessionRepository = confessionRepository;
        this.mapper = mapper;
    }

    public List<BoostDTO> getAllBoosts() {
        return boostRepository.findAll().stream()
                .map(mapper::toBoostDTO)
                .collect(Collectors.toList());
    }

    public BoostDTO getBoostById(Long id) {
        Boost boost = boostRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Boost not found with id " + id));
        return mapper.toBoostDTO(boost);
    }

    public BoostDTO createBoost(BoostDTO dto) {
        Boost boost = mapper.toBoostEntity(dto);
        if (dto.getUserId() != null) {
            User user = userRepository.findById(dto.getUserId())
                    .orElseThrow(() -> new ResourceNotFoundException("User not found with id " + dto.getUserId()));
            boost.setUser(user);
        }
        if (dto.getConfessionId() != null) {
            Confession confession = confessionRepository.findById(dto.getConfessionId())
                    .orElseThrow(() -> new ResourceNotFoundException("Confession not found with id " + dto.getConfessionId()));
            boost.setConfession(confession);
        }
        Boost saved = boostRepository.save(boost);
        return mapper.toBoostDTO(saved);
    }

    public BoostDTO updateBoost(Long id, BoostDTO dto) {
        Boost existing = boostRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Boost not found with id " + id));
        existing.setBoostType(BoostType.valueOf(dto.getBoostType()));
        existing.setDate(dto.getDate());
        // User și Confession pot rămâne la fel sau update dacă vrei
        Boost updated = boostRepository.save(existing);
        return mapper.toBoostDTO(updated);
    }

    public void deleteBoost(Long id) {
        if (!boostRepository.existsById(id)) {
            throw new ResourceNotFoundException("Boost not found with id " + id);
        }
        boostRepository.deleteById(id);
    }


    @Override
    public Optional<Boost> getById(Long id) {
        return Optional.ofNullable(boostRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Boost not found")));
    }

    @Override
    public Class<Boost> getEntityClass() {
        return Boost.class;
    }
}

