
package com.confessionverse.backend.dto.requestDTO;

import lombok.Data;

@Data
public class AiRequestDto {
    private String message;

    public AiRequestDto() {}

    public AiRequestDto(String message) {
        this.message = message;
    }

}
