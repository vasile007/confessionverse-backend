package com.confessionverse.backend.dto;

import com.confessionverse.backend.model.Role;
import lombok.Data;

@Data
public class RegisterRequest {
    private String username;
    private String email;
    private String password;
    private Role role;
}
