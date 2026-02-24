package com.confessionverse.backend.dto.requestDTO;

import jakarta.validation.constraints.NotNull;
import lombok.Builder;
import lombok.Data;

@Data
@Builder
public class BoostRequestDTO {

    @NotNull(message = "Confession ID is required")
    private Long confessionId;

    @NotNull(message = "User ID is required")
    private Long userId;

    @NotNull(message = "Boost type is required")
    private String boostType;  // Or BoostType if you use the enum directly
}

