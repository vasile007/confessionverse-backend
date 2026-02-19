package com.confessionverse.backend.dto;

import com.confessionverse.backend.dto.UserDTO;
import com.confessionverse.backend.model.User;
import com.confessionverse.backend.service.SubscriptionEntitlementService;
import org.springframework.stereotype.Component;

import java.util.stream.Collectors;

@Component
public class UserDtoMapper {
    private final SubscriptionEntitlementService subscriptionEntitlementService;

    public UserDtoMapper(SubscriptionEntitlementService subscriptionEntitlementService) {
        this.subscriptionEntitlementService = subscriptionEntitlementService;
    }

    public UserDTO toUserDto(User user) {
        UserDTO dto = new UserDTO();
        dto.setId(user.getId());
        dto.setUsername(user.getUsername());
        dto.setEmail(user.getEmail());
      ///  dto.setPasswordHash(user.getPasswordHash());
        dto.setRole(user.getRole().name());
        SubscriptionEntitlementService.PlanSnapshot planSnapshot = subscriptionEntitlementService.getPlanSnapshot(user.getId());
        dto.setPremium(planSnapshot.premium());
        dto.setPlanType(planSnapshot.planType());

        dto.setConfessionIds(user.getConfessions().stream()
                .map(c -> c.getId())
                .collect(Collectors.toSet()));

        dto.setChatRoomIds(user.getChatRooms().stream()
                .map(cr -> cr.getId())
                .collect(Collectors.toSet()));

        dto.setMessageIds(user.getMessages().stream()
                .map(m -> m.getId())
                .collect(Collectors.toSet()));

        dto.setSubscriptionIds(user.getSubscriptions().stream()
                .map(s -> s.getId())
                .collect(Collectors.toSet()));

        dto.setBoostIds(user.getBoosts().stream()
                .map(b -> b.getId())
                .collect(Collectors.toSet()));

        return dto;
    }
}
