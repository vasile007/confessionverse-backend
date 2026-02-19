package com.confessionverse.backend.service;



import com.confessionverse.backend.dto.requestDTO.SubscriptionRequestDTO;
import com.confessionverse.backend.dto.responseDTO.SubscriptionResponseDTO;
import com.confessionverse.backend.exception.ResourceNotFoundException;
import com.confessionverse.backend.mapper.EntityDtoMapper;
import com.confessionverse.backend.model.Subscription;
import com.confessionverse.backend.model.User;
import com.confessionverse.backend.repository.SubscriptionRepository;
import com.confessionverse.backend.repository.UserRepository;
import com.confessionverse.backend.security.ownership.OwnableService;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Optional;
import java.util.stream.Collectors;

@Service
public class SubscriptionService implements OwnableService<Subscription> {

    private final SubscriptionRepository subscriptionRepository;
    private final UserRepository userRepository;
    private final EntityDtoMapper mapper;

    public SubscriptionService(SubscriptionRepository subscriptionRepository, UserRepository userRepository, EntityDtoMapper mapper) {
        this.subscriptionRepository = subscriptionRepository;
        this.userRepository = userRepository;
        this.mapper = mapper;
    }

    public List<SubscriptionResponseDTO> getAll() {
        return subscriptionRepository.findAll()
                .stream()
                .map(mapper::toResponseDto)
                .collect(Collectors.toList());
    }

    public SubscriptionResponseDTO getDtoById(Long id) {
        Subscription sub = subscriptionRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Subscription not found with id " + id));
        return mapper.toResponseDto(sub);
    }

    public SubscriptionResponseDTO getLatestByUserId(Long userId) {
        return subscriptionRepository.findTopByUserIdOrderByUpdatedAtDesc(userId)
                .map(subscription -> {
                    SubscriptionResponseDTO dto = mapper.toResponseDto(subscription);
                    dto.setPlanType(isProStatus(subscription.getStatus()) ? "PRO" : "FREE");
                    return dto;
                })
                .orElseGet(() -> {
                    SubscriptionResponseDTO fallback = new SubscriptionResponseDTO();
                    fallback.setUserId(userId);
                    fallback.setPlanType("FREE");
                    fallback.setStatus("free");
                    fallback.setStartDate(null);
                    fallback.setEndDate(null);
                    fallback.setCurrentPeriodStart(null);
                    fallback.setCurrentPeriodEnd(null);
                    fallback.setCancelAtPeriodEnd(false);
                    fallback.setLastPaymentAt(null);
                    fallback.setLastInvoiceId(null);
                    fallback.setStripeCustomerIdShort(null);
                    fallback.setStripeSubscriptionIdShort(null);
                    return fallback;
                });
    }

    public SubscriptionResponseDTO create(SubscriptionRequestDTO dto) {
        User user = userRepository.findById(dto.getUserId())
                .orElseThrow(() -> new ResourceNotFoundException("User not found with id " + dto.getUserId()));
        Subscription sub = mapper.toEntity(dto, user);
        sub.setSubscriber(user);
        if (sub.getStatus() == null) {
            sub.setStatus("active");
        }
        Subscription saved = subscriptionRepository.save(sub);
        return mapper.toResponseDto(saved);
    }

    public SubscriptionResponseDTO update(Long id, SubscriptionRequestDTO dto) {
        Subscription sub = subscriptionRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Subscription not found with id " + id));

        sub.setPlanType(dto.getPlanType());
        sub.setStartDate(dto.getStartDate());
        sub.setEndDate(dto.getEndDate());

        if (dto.getUserId() != null) {
            User user = userRepository.findById(dto.getUserId())
                    .orElseThrow(() -> new ResourceNotFoundException("User not found with id " + dto.getUserId()));
            sub.setUser(user);
            sub.setSubscriber(user);
        }

        Subscription updated = subscriptionRepository.save(sub);
        return mapper.toResponseDto(updated);
    }

    public void delete(Long id) {
        if (!subscriptionRepository.existsById(id)) {
            throw new ResourceNotFoundException("Subscription not found with id " + id);
        }
        subscriptionRepository.deleteById(id);
    }


    @Override
    public Optional<Subscription> getById(Long id) {
        return Optional.ofNullable(subscriptionRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Subscription not found")));
    }

    @Override
    public Class<Subscription> getEntityClass() {
        return Subscription.class;
    }
    public Long getOwnerUserIdBySubscriptionId(Long subscriptionId) {
        return subscriptionRepository.findById(subscriptionId)
                .map(subscription -> subscription.getUser().getId())
                .orElse(null);
    }

    private boolean isProStatus(String status) {
        return "active".equalsIgnoreCase(status) || "trialing".equalsIgnoreCase(status);
    }

}

