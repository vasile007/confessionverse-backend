package com.confessionverse.backend.repository;

import com.confessionverse.backend.model.Message;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

import java.time.LocalDateTime;
import java.util.List;

public interface MessageRepository extends JpaRepository<Message, Long> {
    Page<Message> findByChatRoomId(Long chatRoomId, Pageable pageable);
    Page<Message> findByChatRoomIdAndContentContainingIgnoreCase(Long chatRoomId, String content, Pageable pageable);
    long countBySenderIdAndTimestampAfter(Long senderId, LocalDateTime timestamp);
    void deleteAllBySenderId(Long senderId);
    List<Message> findByChatRoomId(Long chatRoomId);
}


