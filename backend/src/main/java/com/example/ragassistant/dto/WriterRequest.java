package com.example.ragassistant.dto;

import jakarta.validation.constraints.NotBlank;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class WriterRequest {
    @NotBlank(message = "Prompt must not be blank")
    private String prompt;
    private String workspace;
}
