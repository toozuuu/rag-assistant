package com.example.ragassistant.controller;

import com.example.ragassistant.dto.RepoConnectRequest;
import com.example.ragassistant.dto.RepoStatusResponse;
import com.example.ragassistant.service.CodeIngestionService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/repository")
@RequiredArgsConstructor
@Slf4j
public class RepositoryController {

    private final CodeIngestionService codeIngestionService;

    @PostMapping("/connect")
    public ResponseEntity<RepoStatusResponse> connectRepository(@Valid @RequestBody RepoConnectRequest request) {
        log.info("Received request to connect repository: {} for workspace: {}", request.getRepoUrl(), request.getWorkspace());
        RepoStatusResponse response = codeIngestionService.ingestRepository(request);
        if ("SUCCESS".equalsIgnoreCase(response.getStatus())) {
            return ResponseEntity.ok(response);
        } else {
            return ResponseEntity.badRequest().body(response);
        }
    }

    @GetMapping("/providers")
    public ResponseEntity<List<Map<String, String>>> getProviders() {
        return ResponseEntity.ok(List.of(
                Map.of("id", "GITHUB", "name", "GitHub", "placeholder", "https://github.com/owner/repository", "hint", "Use GitHub Personal Access Token (classic or fine-grained) for private repos"),
                Map.of("id", "BITBUCKET", "name", "Bitbucket", "placeholder", "https://bitbucket.org/workspace/repository", "hint", "Use Bitbucket Username + App Password (with repository read permission)"),
                Map.of("id", "GITLAB", "name", "GitLab", "placeholder", "https://gitlab.com/group/project", "hint", "Use GitLab Personal Access Token with read_repository scope"),
                Map.of("id", "CUSTOM", "name", "Custom Git Server", "placeholder", "https://git.yourcompany.com/repo.git", "hint", "Standard HTTPS Git URL with Token or HTTP Basic credentials")
        ));
    }
}