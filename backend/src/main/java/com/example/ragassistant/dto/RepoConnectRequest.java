package com.example.ragassistant.dto;

import jakarta.validation.constraints.NotBlank;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class RepoConnectRequest {

    @NotBlank(message = "Repository URL is required")
    private String repoUrl;

    @Builder.Default
    private String branch = "main";

    @Builder.Default
    private String provider = "GITHUB"; // GITHUB, BITBUCKET, GITLAB, CUSTOM

    private String username;

    private String tokenOrPassword;

    @NotBlank(message = "Workspace name is required")
    private String workspace;

    private List<String> customExtensions;
}