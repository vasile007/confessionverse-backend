package com.confessionverse.backend.dto.responseDTO;

import lombok.Data;

@Data
public class AuthResponseDTO {
    private String token;
    private Long userId;
    private String username;
}

