package com.confessionverse.backend.repository;

import com.confessionverse.backend.model.ChatRoomMembership;
import com.confessionverse.backend.model.ChatRoomMembershipId;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

import java.util.List;
import java.util.Optional;

public interface ChatRoomMembershipRepository extends JpaRepository<ChatRoomMembership, ChatRoomMembershipId> {
    Optional<ChatRoomMembership> findByChatRoom_IdAndUser_Id(Long chatRoomId, Long userId);
    Optional<ChatRoomMembership> findByChatRoom_IdAndUser_IdAndActiveTrue(Long chatRoomId, Long userId);
    List<ChatRoomMembership> findAllByUser_IdAndActiveTrueAndHiddenAtIsNull(Long userId);
    List<ChatRoomMembership> findAllByChatRoom_IdAndActiveTrue(Long chatRoomId);
    boolean existsByChatRoom_IdAndUser_IdAndActiveTrue(Long chatRoomId, Long userId);

    @Query("SELECT COUNT(DISTINCT m.user.id) FROM ChatRoomMembership m WHERE m.active = true")
    long countDistinctActiveUsers();
}
