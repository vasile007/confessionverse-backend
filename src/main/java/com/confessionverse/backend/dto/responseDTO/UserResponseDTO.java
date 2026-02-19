package com.confessionverse.backend.dto.responseDTO;

import lombok.Data;

@Data
public class UserResponseDTO {
    private Long id;
    private String username;
    private String email;
    private String role;
    private Boolean premium;
    private String planType;
}

