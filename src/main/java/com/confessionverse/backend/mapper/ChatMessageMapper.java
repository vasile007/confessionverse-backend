package com.confessionverse.backend.mapper;

import com.confessionverse.backend.dto.ChatMessageDTO;
import com.confessionverse.backend.model.ChatRoom;
import com.confessionverse.backend.model.Message;
import com.confessionverse.backend.model.User;

import java.time.LocalDateTime;

public class ChatMessageMapper {
    public static Message toEntity(ChatMessageDTO dto, User sender, ChatRoom chatRoom) {
        Message message = new Message();
        message.setContent(dto.getContent());
        message.setSender(sender);
        message.setChatRoom(chatRoom);
        message.setTimestamp(LocalDateTime.now());
        return message;
    }
}
