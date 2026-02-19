package com.confessionverse.backend.repository;

import com.confessionverse.backend.model.ChatInvitation;
import com.confessionverse.backend.model.ChatInvitationStatus;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

public interface ChatInvitationRepository extends JpaRepository<ChatInvitation, Long> {

    Optional<ChatInvitation> findFirstByChatRoom_IdAndInvitee_IdAndStatus(
            Long chatRoomId,
            Long inviteeId,
            ChatInvitationStatus status
    );

    List<ChatInvitation> findByInvitee_EmailAndStatusOrderByCreatedAtDesc(
            String inviteeEmail,
            ChatInvitationStatus status
    );

    List<ChatInvitation> findByStatus(ChatInvitationStatus status);

    @Modifying
    @Query("UPDATE ChatInvitation ci SET ci.status = :expiredStatus, ci.respondedAt = :now " +
            "WHERE ci.status = :pendingStatus AND ci.createdAt < :cutoff")
    int markPendingAsExpired(@Param("pendingStatus") ChatInvitationStatus pendingStatus,
                             @Param("expiredStatus") ChatInvitationStatus expiredStatus,
                             @Param("cutoff") LocalDateTime cutoff,
                             @Param("now") LocalDateTime now);
}
