package com.confessionverse.backend.dto.requestDTO;

import lombok.Data;

@Data
public class UserRequestDTO {
    private String username;
    private String email;
    private String password;  // plain password for registration/update
    private String role;      // ex: "USER", "ADMIN"
    private Boolean premium;
}

