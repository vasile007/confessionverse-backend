package com.confessionverse.backend.dto.responseDTO;

import lombok.Data;
@Data
public class AiResponseDto {
    private String reply;

    public AiResponseDto() {}

    public AiResponseDto(String reply) {
        this.reply = reply;
    }

}
