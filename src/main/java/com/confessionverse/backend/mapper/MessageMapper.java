package com.confessionverse.backend.mapper;

import com.confessionverse.backend.dto.requestDTO.MessageRequestDTO;
import com.confessionverse.backend.dto.responseDTO.MessageResponseDTO;
import com.confessionverse.backend.model.Message;
import org.springframework.stereotype.Component;

@Component
public class MessageMapper {
    private final EntityDtoMapper entityDtoMapper;

    public MessageMapper(EntityDtoMapper entityDtoMapper) {
        this.entityDtoMapper = entityDtoMapper;
    }

    public Message toEntity(MessageRequestDTO dto) {
        if (dto == null) return null;

        Message message = new Message();
        message.setContent(dto.getContent());
        // chatRoom and sender trebuie setate separat în service, nu din DTO direct
        return message;
    }

    public MessageResponseDTO toResponseDTO(Message entity) {
        if (entity == null) return null;

        MessageResponseDTO dto = new MessageResponseDTO();
        dto.setId(entity.getId());
        dto.setContent(entity.getContent());
        dto.setTimestamp(entity.getTimestamp());
        dto.setChatRoomId(entity.getChatRoom() != null ? entity.getChatRoom().getId() : null);
        dto.setSenderId(entity.getSender() != null ? entity.getSender().getId() : null);
        dto.setSender(entityDtoMapper.toUserSummaryDTO(entity.getSender()));
        return dto;
    }
}

