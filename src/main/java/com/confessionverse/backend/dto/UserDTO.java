package com.confessionverse.backend.dto;

import com.confessionverse.backend.model.User;
import lombok.Data;
import java.util.Set;
import java.util.stream.Collectors;

@Data
public class UserDTO {
    private Long id;
    private String username;
    private String email;
 //   private String passwordHash;
    private String role;
    private Boolean premium;
    private String planType;

    private Set<Long> confessionIds;
    private Set<Long> chatRoomIds;
    private Set<Long> messageIds;
    private Set<Long> subscriptionIds;
    private Set<Long> boostIds;

    public UserDTO() {
    }

    public UserDTO(Long id, String username, String email, String role, Boolean premium,
                   Set<Long> confessionIds, Set<Long> chatRoomIds, Set<Long> messageIds,
                   Set<Long> subscriptionIds, Set<Long> boostIds) {
        this.id = id;
        this.username = username;
        this.email = email;
        this.role = role;
        this.premium = premium;
        this.confessionIds = confessionIds;
        this.chatRoomIds = chatRoomIds;
        this.messageIds = messageIds;
        this.subscriptionIds = subscriptionIds;
        this.boostIds = boostIds;
    }

    public static UserDTO fromEntity(User user) {
        if (user == null) return null;

        UserDTO dto = new UserDTO(
                user.getId(),
                user.getUsername(),
                user.getEmail(),
                user.getRole() != null ? user.getRole().name() : null,
                user.getPremium(),
                user.getConfessions().stream().map(c -> c.getId()).collect(Collectors.toSet()),
                user.getChatRooms().stream().map(c -> c.getId()).collect(Collectors.toSet()),
                user.getMessages().stream().map(m -> m.getId()).collect(Collectors.toSet()),
                user.getSubscriptions().stream().map(s -> s.getId()).collect(Collectors.toSet()),
                user.getBoosts().stream().map(b -> b.getId()).collect(Collectors.toSet())
        );
        dto.setPlanType(Boolean.TRUE.equals(user.getPremium()) ? "PRO" : "FREE");
        return dto;
    }
}
