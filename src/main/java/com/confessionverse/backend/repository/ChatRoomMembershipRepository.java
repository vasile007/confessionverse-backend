package com.confessionverse.backend.repository;

import com.confessionverse.backend.model.ChatRoomMembership;
import com.confessionverse.backend.model.ChatRoomMembershipId;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;
import java.time.LocalDateTime;

public interface ChatRoomMembershipRepository extends JpaRepository<ChatRoomMembership, ChatRoomMembershipId> {
    Optional<ChatRoomMembership> findByChatRoom_IdAndUser_Id(Long chatRoomId, Long userId);
    Optional<ChatRoomMembership> findByChatRoom_IdAndUser_IdAndActiveTrue(Long chatRoomId, Long userId);
    List<ChatRoomMembership> findAllByUser_IdAndActiveTrueAndHiddenAtIsNull(Long userId);
    List<ChatRoomMembership> findAllByChatRoom_IdAndActiveTrue(Long chatRoomId);
    long countByChatRoom_IdAndActiveTrue(Long chatRoomId);
    boolean existsByChatRoom_IdAndUser_IdAndActiveTrue(Long chatRoomId, Long userId);

    @Query("""
            SELECT m FROM ChatRoomMembership m
            WHERE m.user.id = :userId AND m.active = true
              AND (m.chatRoom.roomType = :randomType
                   OR (m.chatRoom.username LIKE 'Random %' AND m.chatRoom.roomType IN :legacyTypes))
            ORDER BY m.joinedAt DESC
            """)
    List<ChatRoomMembership> findActiveRandomMemberships(
            @Param("userId") Long userId,
            @Param("randomType") com.confessionverse.backend.model.ChatRoomType randomType,
            @Param("legacyTypes") List<com.confessionverse.backend.model.ChatRoomType> legacyTypes);

    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Query("""
            UPDATE ChatRoomMembership m
            SET m.active = false, m.leftAt = :now
            WHERE m.active = true
              AND (m.chatRoom.roomType = :randomType
                   OR (m.chatRoom.username LIKE 'Random %' AND m.chatRoom.roomType IN :legacyTypes))
              AND COALESCE(m.lastActiveAt, m.joinedAt) < :cutoff
            """)
    int deactivateStaleRandomMemberships(
            @Param("randomType") com.confessionverse.backend.model.ChatRoomType randomType,
            @Param("legacyTypes") List<com.confessionverse.backend.model.ChatRoomType> legacyTypes,
            @Param("cutoff") LocalDateTime cutoff,
            @Param("now") LocalDateTime now);

    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Query("""
            UPDATE ChatRoomMembership m
            SET m.lastActiveAt = :now
            WHERE m.chatRoom.id = :roomId AND m.user.id = :userId AND m.active = true
            """)
    int touchActiveMembership(@Param("roomId") Long roomId,
                              @Param("userId") Long userId,
                              @Param("now") LocalDateTime now);

    @Query("SELECT COUNT(DISTINCT m.user.id) FROM ChatRoomMembership m WHERE m.active = true")
    long countDistinctActiveUsers();
}
