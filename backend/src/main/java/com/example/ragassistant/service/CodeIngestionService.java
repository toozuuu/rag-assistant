package com.example.ragassistant.service;

import com.example.ragassistant.config.ChunkingProperties;
import com.example.ragassistant.dto.RepoConnectRequest;
import com.example.ragassistant.dto.RepoStatusResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.eclipse.jgit.api.Git;
import org.eclipse.jgit.transport.UsernamePasswordCredentialsProvider;
import org.springframework.ai.document.Document;
import org.springframework.ai.transformer.splitter.TokenTextSplitter;
import org.springframework.ai.vectorstore.VectorStore;
import org.springframework.stereotype.Service;

import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.nio.file.attribute.BasicFileAttributes;
import java.util.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

@Service
@RequiredArgsConstructor
@Slf4j
public class CodeIngestionService {

    private static final long MAX_CODE_FILE_SIZE = 1024 * 1024; // 1 MB per code file
    private static final Set<String> DEFAULT_CODE_EXTENSIONS = Set.of(
            "java", "kt", "scala", "groovy",
            "py", "js", "jsx", "ts", "tsx", "vue", "svelte",
            "go", "rs", "c", "cpp", "h", "hpp", "cs", "rb", "php", "swift",
            "sql", "sh", "bash", "ps1",
            "json", "yaml", "yml", "xml", "properties", "toml", "proto", "graphql",
            "md", "html", "css", "scss"
    );

    private static final Set<String> IGNORED_DIRS = Set.of(
            ".git", "node_modules", "target", "build", "dist", "out",
            ".idea", ".vscode", ".gradle", "bin", "obj", "vendor",
            "coverage", "__pycache__", ".next", ".nuxt", ".turbo"
    );

    private static final Set<String> IGNORED_FILES = Set.of(
            "package-lock.json", "yarn.lock", "pnpm-lock.yaml", "composer.lock",
            "Cargo.lock", "Gemfile.lock", "go.sum"
    );

    // Regex for structural symbols & REST endpoints
    private static final Pattern JAVA_ANNOTATION_PATTERN = Pattern.compile("@(Get|Post|Put|Delete|Patch|Request)Mapping\\s*\\(([^)]*)\\)");
    private static final Pattern CLASS_PATTERN = Pattern.compile("(?:class|interface|record|enum|struct)\\s+(\\w+)");
    private static final Pattern FUNCTION_PATTERN = Pattern.compile("(?:def|func|function|const|let|var|public|private|protected)?\\s*(\\w+)\\s*(?:=\\s*(?:async\\s*)?\\([^)]*\\)|\\([^)]*\\)\\s*(?:[:{]|=>))");

    private final VectorStore vectorStore;
    private final ChunkingProperties chunkingProperties;

    public RepoStatusResponse ingestRepository(RepoConnectRequest request) {
        log.info("Starting code ingestion for repo: {} (branch: {}, workspace: {})", 
                request.getRepoUrl(), request.getBranch(), request.getWorkspace());

        Path tempDir = null;
        try {
            tempDir = Files.createTempDirectory("rag-repo-clone-");
            cloneRepository(request, tempDir.toFile());

            Set<String> targetExtensions = new HashSet<>(DEFAULT_CODE_EXTENSIONS);
            if (request.getCustomExtensions() != null && !request.getCustomExtensions().isEmpty()) {
                targetExtensions.addAll(request.getCustomExtensions());
            }

            List<Document> allChunks = new ArrayList<>();
            Map<String, Integer> languageStats = new HashMap<>();
            int[] fileCount = new int[]{0};

            final Path repoRoot = tempDir;
            Files.walkFileTree(repoRoot, new SimpleFileVisitor<>() {
                @Override
                public FileVisitResult preVisitDirectory(Path dir, BasicFileAttributes attrs) {
                    String dirName = dir.getFileName() != null ? dir.getFileName().toString() : "";
                    if (IGNORED_DIRS.contains(dirName)) {
                        return FileVisitResult.SKIP_SUBTREE;
                    }
                    return FileVisitResult.CONTINUE;
                }

                @Override
                public FileVisitResult visitFile(Path file, BasicFileAttributes attrs) throws IOException {
                    if (attrs.isRegularFile() && attrs.size() <= MAX_CODE_FILE_SIZE) {
                        String fileName = file.getFileName().toString();
                        if (IGNORED_FILES.contains(fileName)) {
                            return FileVisitResult.CONTINUE;
                        }

                        String ext = getFileExtension(fileName);
                        if (targetExtensions.contains(ext)) {
                            String relativePath = repoRoot.relativize(file).toString().replace('\\', '/');
                            try {
                                String content = Files.readString(file, StandardCharsets.UTF_8);
                                if (!content.trim().isEmpty()) {
                                    List<Document> fileChunks = processCodeFile(content, relativePath, fileName, ext, request);
                                    allChunks.addAll(fileChunks);
                                    fileCount[0]++;
                                    languageStats.merge(ext, 1, Integer::sum);
                                }
                            } catch (Exception e) {
                                log.debug("Skipping non-UTF8 or unreadable file {}: {}", relativePath, e.getMessage());
                            }
                        }
                    }
                    return FileVisitResult.CONTINUE;
                }
            });

            if (!allChunks.isEmpty()) {
                int batchSize = 100;
                for (int i = 0; i < allChunks.size(); i += batchSize) {
                    int end = Math.min(i + batchSize, allChunks.size());
                    vectorStore.accept(allChunks.subList(i, end));
                }
            }

            log.info("Successfully indexed {} files and {} chunks for workspace {}", 
                    fileCount[0], allChunks.size(), request.getWorkspace());

            return RepoStatusResponse.builder()
                    .workspace(request.getWorkspace())
                    .repoUrl(request.getRepoUrl())
                    .branch(request.getBranch())
                    .provider(request.getProvider())
                    .filesIndexed(fileCount[0])
                    .chunksIndexed(allChunks.size())
                    .status("SUCCESS")
                    .message("Successfully connected and indexed " + fileCount[0] + " code files (" + allChunks.size() + " vector chunks).")
                    .fileTypes(languageStats)
                    .build();

        } catch (Exception e) {
            log.error("Failed to clone and index repository {}: {}", request.getRepoUrl(), e.getMessage(), e);
            return RepoStatusResponse.builder()
                    .workspace(request.getWorkspace())
                    .repoUrl(request.getRepoUrl())
                    .branch(request.getBranch())
                    .provider(request.getProvider())
                    .filesIndexed(0)
                    .chunksIndexed(0)
                    .status("FAILED")
                    .message("Failed to index repository: " + e.getMessage())
                    .fileTypes(Map.of())
                    .build();
        } finally {
            if (tempDir != null) {
                deleteDirectoryRecursively(tempDir);
            }
        }
    }

    private void cloneRepository(RepoConnectRequest request, File targetDir) throws Exception {
        var cloneCommand = Git.cloneRepository()
                .setURI(request.getRepoUrl())
                .setDirectory(targetDir)
                .setBranch(request.getBranch() != null && !request.getBranch().isBlank() ? request.getBranch() : "main")
                .setDepth(1);

        String token = request.getTokenOrPassword();
        String user = request.getUsername();

        if (token != null && !token.isBlank()) {
            if (user == null || user.isBlank()) {
                user = "git";
            }
            cloneCommand.setCredentialsProvider(new UsernamePasswordCredentialsProvider(user, token));
        }

        try (Git git = cloneCommand.call()) {
            log.info("Cloned repository {} into temporary directory", request.getRepoUrl());
        }
    }

    private List<Document> processCodeFile(String content, String relativePath, String fileName, String ext, RepoConnectRequest request) {
        TokenTextSplitter parentSplitter = new TokenTextSplitter(
                chunkingProperties.getParentMaxTokens(),
                chunkingProperties.getParentOverlap(),
                chunkingProperties.getMinChunkSize(),
                chunkingProperties.getMaxChunks(),
                true);
        TokenTextSplitter childSplitter = new TokenTextSplitter(
                chunkingProperties.getChildMaxTokens(),
                chunkingProperties.getChildOverlap(),
                chunkingProperties.getMinChunkSize(),
                chunkingProperties.getMaxChunks(),
                true);

        // Extract structural symbols & annotations
        List<String> symbols = extractSymbols(content, ext);
        String symbolHeader = symbols.isEmpty() ? "" : "// Key Symbols: " + String.join(", ", symbols) + "\n";

        String headerPrefix = "// File: " + relativePath + "\n" +
                              "// Repository: " + request.getRepoUrl() + "\n" +
                              "// Branch: " + request.getBranch() + " | Language: " + ext + "\n" +
                              symbolHeader + "\n";

        String enrichedContent = headerPrefix + content;
        Document initialDoc = new Document(enrichedContent);
        List<Document> parentChunks = parentSplitter.apply(List.of(initialDoc));
        List<Document> result = new ArrayList<>();

        String docId = "code-" + UUID.nameUUIDFromBytes((request.getRepoUrl() + ":" + relativePath).getBytes(StandardCharsets.UTF_8));

        for (Document parentChunk : parentChunks) {
            String parentText = parentChunk.getContent();
            List<Document> childChunks = childSplitter.apply(List.of(parentChunk));

            for (Document childChunk : childChunks) {
                Document enrichedChild = new Document(childChunk.getContent(), childChunk.getMetadata());
                enrichedChild.getMetadata().put("filename", fileName);
                enrichedChild.getMetadata().put("filePath", relativePath);
                enrichedChild.getMetadata().put("language", ext);
                enrichedChild.getMetadata().put("repository", request.getRepoUrl());
                enrichedChild.getMetadata().put("branch", request.getBranch());
                enrichedChild.getMetadata().put("doc_id", docId);
                enrichedChild.getMetadata().put("parent_text", parentText);
                enrichedChild.getMetadata().put("workspace", request.getWorkspace());
                result.add(enrichedChild);
            }
        }
        return result;
    }

    private List<String> extractSymbols(String content, String ext) {
        Set<String> symbols = new LinkedHashSet<>();
        
        // Classes & types
        Matcher classMatcher = CLASS_PATTERN.matcher(content);
        while (classMatcher.find() && symbols.size() < 10) {
            symbols.add(classMatcher.group(1));
        }

        // Framework endpoints (Spring REST annotations)
        Matcher annotMatcher = JAVA_ANNOTATION_PATTERN.matcher(content);
        while (annotMatcher.find() && symbols.size() < 15) {
            symbols.add("@" + annotMatcher.group(1) + "Mapping(" + annotMatcher.group(2).trim() + ")");
        }

        return new ArrayList<>(symbols);
    }

    private String getFileExtension(String filename) {
        int dotIdx = filename.lastIndexOf('.');
        if (dotIdx < 0) return "";
        return filename.substring(dotIdx + 1).toLowerCase();
    }

    private void deleteDirectoryRecursively(Path path) {
        try {
            Files.walkFileTree(path, new SimpleFileVisitor<>() {
                @Override
                public FileVisitResult visitFile(Path file, BasicFileAttributes attrs) throws IOException {
                    Files.deleteIfExists(file);
                    return FileVisitResult.CONTINUE;
                }

                @Override
                public FileVisitResult postVisitDirectory(Path dir, IOException exc) throws IOException {
                    Files.deleteIfExists(dir);
                    return FileVisitResult.CONTINUE;
                }
            });
        } catch (IOException e) {
            log.warn("Failed to clean up temp directory {}: {}", path, e.getMessage());
        }
    }
}