package com.confessionverse.backend.repository;

import com.confessionverse.backend.model.ChatRoom;
import com.confessionverse.backend.model.ChatRoomType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import jakarta.persistence.LockModeType;

import java.util.List;
import java.util.Optional;

public interface ChatRoomRepository extends JpaRepository<ChatRoom, Long> {
    List<ChatRoom> findAllByParticipants_Id(Long userId);
    List<ChatRoom> findAllByHiddenBy_Id(Long userId);
    List<ChatRoom> findAllByCreator_Id(Long userId);
    @Query("SELECT c FROM ChatRoom c " +
            "WHERE c.roomType = :roomType " +
            "AND SIZE(c.participants) = 2 " +
            "AND :user1Id IN (SELECT u.id FROM c.participants u) " +
            "AND :user2Id IN (SELECT u.id FROM c.participants u)")
    Optional<ChatRoom> findPrivateRoom(@Param("user1Id") Long user1Id,
                                       @Param("user2Id") Long user2Id,
                                       @Param("roomType") ChatRoomType roomType);

    @Query("""
            SELECT c FROM ChatRoom c
            WHERE c.roomType = :roomType
              AND (SELECT COUNT(m) FROM ChatRoomMembership m
                   WHERE m.chatRoom = c AND m.active = true
                     AND m.user.id IN (:user1Id, :user2Id)) = 2
              AND (SELECT COUNT(m) FROM ChatRoomMembership m
                   WHERE m.chatRoom = c AND m.active = true) = 2
            ORDER BY c.id DESC
            """)
    List<ChatRoom> findActivePrivateRooms(@Param("user1Id") Long user1Id,
                                          @Param("user2Id") Long user2Id,
                                          @Param("roomType") ChatRoomType roomType);

    List<ChatRoom> findAllByRoomTypeOrderByIdAsc(ChatRoomType roomType);
    List<ChatRoom> findAllByRoomTypeInOrderByIdAsc(List<ChatRoomType> roomTypes);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT c FROM ChatRoom c WHERE c.roomType = :roomType ORDER BY c.id ASC")
    List<ChatRoom> findAllByRoomTypeForUpdate(@Param("roomType") ChatRoomType roomType);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("""
            SELECT c FROM ChatRoom c
            WHERE c.roomType = :roomType
              AND (:excludedRoomId IS NULL OR c.id <> :excludedRoomId)
              AND (SELECT COUNT(m) FROM ChatRoomMembership m
                   WHERE m.chatRoom = c AND m.active = true) BETWEEN 1 AND :maxMembersMinusOne
            ORDER BY c.createdAt ASC, c.id ASC
            """)
    List<ChatRoom> findAvailableRandomRoomsForUpdate(@Param("roomType") ChatRoomType roomType,
                                                      @Param("excludedRoomId") Long excludedRoomId,
                                                      @Param("maxMembersMinusOne") long maxMembersMinusOne);
    boolean existsByIdAndParticipantsUsername(Long chatRoomId, String username);
    long countByParticipants_Id(Long userId);
    long countByCreator_Id(Long userId);
}
