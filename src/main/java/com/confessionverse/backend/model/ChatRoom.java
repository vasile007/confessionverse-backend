package com.confessionverse.backend.model;

import com.confessionverse.backend.security.ownership.Ownable;
import com.fasterxml.jackson.annotation.JsonIgnore;
import jakarta.persistence.*;
import lombok.Data;

import java.time.LocalDateTime;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.Set;

@Entity
@Data
public class ChatRoom implements Ownable {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    private LocalDateTime createdAt;

    private String username;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 50)
    private ChatRoomType roomType = ChatRoomType.DIRECT;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "creator_id")
    private User creator;

    @ManyToMany
    @JoinTable(
            name = "chatroom_users",
            joinColumns = @JoinColumn(name = "chatroom_id"),
            inverseJoinColumns = @JoinColumn(name = "user_id"),
            uniqueConstraints = @UniqueConstraint(
                    name = "uk_chatroom_users_room_user",
                    columnNames = {"chatroom_id", "user_id"}
            )
    )
    private Set<User> participants = new LinkedHashSet<>();

    @ManyToMany
    @JoinTable(
            name = "chatroom_hidden_by",
            joinColumns = @JoinColumn(name = "chatroom_id"),
            inverseJoinColumns = @JoinColumn(name = "user_id")
    )
    private Set<User> hiddenBy = new HashSet<>();

    @OneToMany(mappedBy = "chatRoom", cascade = CascadeType.ALL, orphanRemoval = true)
    @JsonIgnore
    private Set<Message> messages = new LinkedHashSet<>();

    @PrePersist
    protected void onCreate() {
        createdAt = LocalDateTime.now();
        if (roomType == null) {
            roomType = ChatRoomType.DIRECT;
        }
    }

    @Override
    public Long getOwnerId() {
        return  creator != null ? creator.getId() : null;
    }
}
