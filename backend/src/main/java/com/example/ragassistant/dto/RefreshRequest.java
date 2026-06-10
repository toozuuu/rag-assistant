package com.example.ragassistant.dto;

import jakarta.validation.constraints.NotBlank;
import lombok.Data;

@Data
public class RefreshRequest {
    @NotBlank(message = "Refresh token must not be blank")
    private String refreshToken;
}
