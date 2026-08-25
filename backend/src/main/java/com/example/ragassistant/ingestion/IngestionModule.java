package com.example.ragassistant.ingestion;

import com.example.ragassistant.config.MetadataKeys;
import com.example.ragassistant.dto.RepoConnectRequest;
import com.example.ragassistant.dto.RepoStatusResponse;
import com.example.ragassistant.retrieval.WorkspaceRetriever;
import lombok.extern.slf4j.Slf4j;
import org.apache.pdfbox.cos.COSName;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.pdmodel.PDPage;
import org.apache.pdfbox.pdmodel.PDResources;
import org.apache.pdfbox.pdmodel.graphics.PDXObject;
import org.apache.pdfbox.pdmodel.graphics.image.PDImageXObject;
import org.apache.poi.xwpf.usermodel.XWPFDocument;
import org.apache.poi.xwpf.usermodel.XWPFPictureData;
import org.eclipse.jgit.api.Git;
import org.eclipse.jgit.transport.UsernamePasswordCredentialsProvider;
import org.springframework.ai.document.Document;
import org.springframework.ai.reader.tika.TikaDocumentReader;
import org.springframework.core.io.ByteArrayResource;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.io.File;
import java.io.IOException;
import java.nio.file.*;
import java.nio.file.attribute.BasicFileAttributes;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

@Service
@Slf4j
public class IngestionModule {

    public static final String UPLOAD_DIR = "uploads/images";

    private static final List<String> ALLOWED_EXTENSIONS = List.of("pdf", "docx", "txt", "html", "htm");
    private static final long MAX_FILE_SIZE = 8 * 1024 * 1024; // 8MB

    private static final Set<String> SUPPORTED_CODE_EXTENSIONS = Set.of(
            "java", "py", "js", "jsx", "ts", "tsx", "go", "rs", "cpp", "c", "h", "hpp",
            "cs", "rb", "php", "swift", "kt", "scala", "sh", "bash", "sql", "yaml",
            "yml", "json", "xml", "html", "css", "scss", "md", "markdown", "gradle",
            "dockerfile", "proto", "graphql"
    );

    private static final Set<String> IGNORED_DIRECTORIES = Set.of(
            ".git", ".svn", ".hg", "node_modules", "target", "build", "dist", "out",
            "bin", "obj", ".idea", ".vscode", ".eclipse", "venv", ".venv", "env",
            "__pycache__", ".pytest_cache", ".next", ".nuxt", "vendor", "packages",
            "coverage", ".gradle", ".mvn"
    );

    private static final Pattern CLASS_PATTERN = Pattern.compile("(?:public\\s+|protected\\s+|private\\s+)?(?:abstract\\s+|final\\s+)?(?:class|interface|enum|record)\\s+([A-Za-z0-9_]+)");
    private static final Pattern FUNCTION_PATTERN = Pattern.compile("(?:public|protected|private|static|async|def|fn|func)\\s+([A-Za-z0-9_]+)\\s*\\(");

    private final WorkspaceRetriever workspaceRetriever;
    private final ChunkBuilder chunkBuilder;

    public IngestionModule(WorkspaceRetriever workspaceRetriever, ChunkBuilder chunkBuilder) {
        this.workspaceRetriever = workspaceRetriever;
        this.chunkBuilder = chunkBuilder;
    }

    // ── Ingest Dispatch ──────────────────────────────────────────────────────

    public IndexResult ingest(IngestionSource source) {
        if (source instanceof IngestionSource.FileSource fileSource) {
            return ingestFile(fileSource.file(), fileSource.workspace());
        } else if (source instanceof IngestionSource.RepoSource repoSource) {
            RepoStatusResponse resp = ingestRepository(repoSource.request());
            return IndexResult.builder()
                    .filesIndexed(resp.getFilesIndexed())
                    .chunksIndexed(resp.getChunksIndexed())
                    .status(resp.getStatus())
                    .message(resp.getMessage())
                    .fileTypes(resp.getFileTypes())
                    .build();
        }
        throw new IllegalArgumentException("Unknown ingestion source: " + source);
    }

    // ── File Ingestion ───────────────────────────────────────────────────────

    public IndexResult ingestFile(MultipartFile file, String workspace) {
        if (file == null || file.isEmpty()) {
            throw new IllegalArgumentException("Cannot process an empty or null file");
        }

        String originalFilename = file.getOriginalFilename();
        if (originalFilename == null || originalFilename.isBlank()) {
            throw new IllegalArgumentException("File must have a valid name");
        }

        if (file.getSize() > MAX_FILE_SIZE) {
            throw new IllegalArgumentException("File size exceeds the 8MB limit");
        }

        String extension = getFileExtension(originalFilename);
        if (!ALLOWED_EXTENSIONS.contains(extension)) {
            throw new IllegalArgumentException("Unsupported file type: " + extension + ". Supported types: " + ALLOWED_EXTENSIONS);
        }

        String safeWorkspace = (workspace == null || workspace.isBlank()) ? "default" : workspace.trim();
        String docId = generateDocId(originalFilename, safeWorkspace);

        try {
            byte[] fileBytes = file.getBytes();

            // Extract images if any
            extractImages(fileBytes, originalFilename, docId);

            // Extract text
            List<Document> rawDocuments = extractText(fileBytes, originalFilename, extension);

            // Chunk and enrich
            List<Document> enrichedChunks = new ArrayList<>();
            Map<String, Object> baseMeta = Map.of(
                    MetadataKeys.FILENAME, originalFilename,
                    MetadataKeys.DOC_ID, docId,
                    MetadataKeys.WORKSPACE, safeWorkspace
            );

            for (Document doc : rawDocuments) {
                enrichedChunks.addAll(chunkBuilder.buildParentChildChunks(doc, baseMeta));
            }

            workspaceRetriever.store(enrichedChunks);
            log.info("Successfully ingested {} chunks for file: {} (workspace: {})", enrichedChunks.size(), originalFilename, safeWorkspace);

            return IndexResult.builder()
                    .filesIndexed(1)
                    .chunksIndexed(enrichedChunks.size())
                    .status("SUCCESS")
                    .message("File uploaded and indexed successfully")
                    .build();
        } catch (IOException e) {
            log.error("Failed to read bytes for file {}: {}", originalFilename, e.getMessage());
            throw new RuntimeException("Failed to read file: " + e.getMessage(), e);
        }
    }

    private List<Document> extractText(byte[] fileBytes, String filename, String extension) {
        if ("docx".equalsIgnoreCase(extension)) {
            return extractDocx(fileBytes, filename);
        }
        try {
            TikaDocumentReader reader = new TikaDocumentReader(new ByteArrayResource(fileBytes));
            List<Document> docs = reader.get();
            if (docs.isEmpty()) {
                throw new IllegalArgumentException("No text could be extracted from file: " + filename);
            }
            return docs;
        } catch (Exception e) {
            log.error("Tika extraction failed for {}: {}", filename, e.getMessage());
            throw new RuntimeException("Text extraction failed: " + e.getMessage(), e);
        }
    }

    private List<Document> extractDocx(byte[] fileBytes, String filename) {
        try (XWPFDocument document = new XWPFDocument(new ByteArrayInputStream(fileBytes))) {
            StringBuilder sb = new StringBuilder();
            document.getParagraphs().forEach(p -> sb.append(p.getText()).append("\n"));
            document.getTables().forEach(t -> t.getRows().forEach(r ->
                    r.getTableCells().forEach(c -> sb.append(c.getText()).append(" "))));

            return List.of(new Document(sb.toString(), Map.of(MetadataKeys.FILENAME, filename)));
        } catch (Exception e) {
            log.error("Failed to parse DOCX file {}: {}", filename, e.getMessage());
            throw new RuntimeException("Failed to parse DOCX: " + e.getMessage(), e);
        }
    }

    // ── Repository Ingestion ─────────────────────────────────────────────────

    public RepoStatusResponse ingestRepository(RepoConnectRequest request) {
        String repoUrl = request.getRepoUrl();
        String workspace = (request.getWorkspace() == null || request.getWorkspace().isBlank()) ? "default" : request.getWorkspace().trim();
        String branch = (request.getBranch() == null || request.getBranch().isBlank()) ? "main" : request.getBranch().trim();

        Path tempDir = null;
        try {
            tempDir = Files.createTempDirectory("rag-repo-clone-");
            log.info("Cloning repository {} into {}", repoUrl, tempDir);

            var cloneCommand = Git.cloneRepository()
                    .setURI(repoUrl)
                    .setDirectory(tempDir.toFile())
                    .setBranch(branch)
                    .setCloneAllBranches(false)
                    .setDepth(1);

            if (request.getPersonalAccessToken() != null && !request.getPersonalAccessToken().isBlank()) {
                String username = (request.getUsername() != null && !request.getUsername().isBlank()) ? request.getUsername() : "oauth2";
                cloneCommand.setCredentialsProvider(new UsernamePasswordCredentialsProvider(username, request.getPersonalAccessToken()));
            }

            try (Git git = cloneCommand.call()) {
                log.info("Successfully cloned {}", repoUrl);
            }

            Map<String, Integer> fileTypes = new HashMap<>();
            List<Document> allChunks = new ArrayList<>();
            final Path rootPath = tempDir;

            Files.walkFileTree(tempDir, new SimpleFileVisitor<>() {
                @Override
                public FileVisitResult preVisitDirectory(Path dir, BasicFileAttributes attrs) {
                    if (IGNORED_DIRECTORIES.contains(dir.getFileName().toString())) {
                        return FileVisitResult.SKIP_SUBTREE;
                    }
                    return FileVisitResult.CONTINUE;
                }

                @Override
                public FileVisitResult visitFile(Path file, BasicFileAttributes attrs) {
                    String ext = getFileExtension(file.getFileName().toString());
                    if (SUPPORTED_CODE_EXTENSIONS.contains(ext)) {
                        try {
                            String content = Files.readString(file);
                            if (content.length() <= 100_000 && !content.isBlank()) {
                                String relativePath = rootPath.relativize(file).toString().replace('\\', '/');
                                fileTypes.merge(ext, 1, Integer::sum);

                                Map<String, Object> meta = Map.of(
                                        MetadataKeys.FILENAME, file.getFileName().toString(),
                                        MetadataKeys.FILE_PATH, relativePath,
                                        MetadataKeys.LANGUAGE, ext,
                                        MetadataKeys.REPOSITORY, repoUrl,
                                        MetadataKeys.BRANCH, branch,
                                        MetadataKeys.WORKSPACE, workspace,
                                        MetadataKeys.DOC_ID, generateDocId(relativePath, workspace)
                                );

                                Document doc = new Document(content, meta);
                                allChunks.addAll(chunkBuilder.buildParentChildChunks(doc, meta));
                            }
                        } catch (Exception e) {
                            log.debug("Skipping file {}: {}", file, e.getMessage());
                        }
                    }
                    return FileVisitResult.CONTINUE;
                }
            });

            workspaceRetriever.store(allChunks);

            return RepoStatusResponse.builder()
                    .workspace(workspace)
                    .repoUrl(repoUrl)
                    .branch(branch)
                    .provider(request.getProvider() != null ? request.getProvider() : "GIT")
                    .filesIndexed(fileTypes.values().stream().mapToInt(Integer::intValue).sum())
                    .chunksIndexed(allChunks.size())
                    .status("SUCCESS")
                    .message("Successfully indexed repository")
                    .fileTypes(fileTypes)
                    .build();

        } catch (Exception e) {
            log.error("Failed to ingest repository {}: {}", repoUrl, e.getMessage(), e);
            return RepoStatusResponse.builder()
                    .workspace(workspace)
                    .repoUrl(repoUrl)
                    .branch(branch)
                    .status("FAILED")
                    .message("Ingestion failed: " + e.getMessage())
                    .fileTypes(Map.of())
                    .build();
        } finally {
            if (tempDir != null) {
                deleteDirectoryRecursively(tempDir.toFile());
            }
        }
    }

    // ── Image Extraction Helper ──────────────────────────────────────────────

    public List<String> extractImages(byte[] fileBytes, String originalFilename, String docId) {
        if (originalFilename == null) return List.of();
        String ext = getFileExtension(originalFilename);
        return switch (ext) {
            case "pdf"  -> extractFromPdf(fileBytes, docId);
            case "docx" -> extractFromDocxImages(fileBytes, docId);
            default     -> List.of();
        };
    }

    private List<String> extractFromPdf(byte[] fileBytes, String docId) {
        List<String> imagePaths = new ArrayList<>();
        Path docDir = Paths.get(UPLOAD_DIR, docId);
        try {
            Files.createDirectories(docDir);
            try (PDDocument document = PDDocument.load(fileBytes)) {
                int pageIndex = 0;
                int imgCounter = 0;
                for (PDPage page : document.getPages()) {
                    PDResources resources = page.getResources();
                    if (resources != null) {
                        for (COSName name : resources.getXObjectNames()) {
                            PDXObject xObject = resources.getXObject(name);
                            if (xObject instanceof PDImageXObject image) {
                                BufferedImage bi = image.getImage();
                                if (bi != null && bi.getWidth() > 80 && bi.getHeight() > 80) {
                                    String fname = String.format("page_%d_img_%d.png", pageIndex, imgCounter++);
                                    ImageIO.write(bi, "PNG", docDir.resolve(fname).toFile());
                                    imagePaths.add(docId + "/" + fname);
                                }
                            }
                        }
                    }
                    pageIndex++;
                }
            }
        } catch (Exception e) {
            log.warn("PDF image extraction failed for {}: {}", docId, e.getMessage());
        }
        return imagePaths;
    }

    private List<String> extractFromDocxImages(byte[] fileBytes, String docId) {
        List<String> imagePaths = new ArrayList<>();
        Path docDir = Paths.get(UPLOAD_DIR, docId);
        try {
            Files.createDirectories(docDir);
            try (XWPFDocument doc = new XWPFDocument(new ByteArrayInputStream(fileBytes))) {
                int i = 0;
                for (XWPFPictureData pic : doc.getAllPictures()) {
                    byte[] data = pic.getData();
                    String picExt = pic.suggestFileExtension();
                    String ext = picExt.isEmpty() ? "png" : picExt;
                    String fname = String.format("img_%d.%s", i++, ext);
                    Files.write(docDir.resolve(fname), data);
                    imagePaths.add(docId + "/" + fname);
                }
            }
        } catch (Exception e) {
            log.warn("DOCX image extraction failed for {}: {}", docId, e.getMessage());
        }
        return imagePaths;
    }

    // ── Delete & List Operations ─────────────────────────────────────────────

    public void deleteDocument(String docId, String workspace) {
        workspaceRetriever.deleteByDocId(docId, workspace);
        // Clean up extracted images directory
        try {
            Path imageDir = Paths.get(UPLOAD_DIR, docId);
            if (Files.exists(imageDir)) {
                deleteDirectoryRecursively(imageDir.toFile());
            }
        } catch (Exception e) {
            log.warn("Failed to delete image directory for docId {}: {}", docId, e.getMessage());
        }
    }

    public List<Map<String, String>> listDocuments(String workspace) {
        List<Document> docs = workspaceRetriever.listByWorkspace(workspace);
        Map<String, Map<String, String>> uniqueDocs = new LinkedHashMap<>();

        for (Document doc : docs) {
            Map<String, Object> meta = doc.getMetadata();
            if (meta == null) continue;
            String docId = (String) meta.get(MetadataKeys.DOC_ID);
            String filename = (String) meta.get(MetadataKeys.FILENAME);
            if (docId != null && filename != null && !uniqueDocs.containsKey(docId)) {
                uniqueDocs.put(docId, Map.of(
                        MetadataKeys.DOC_ID, docId,
                        MetadataKeys.FILENAME, filename,
                        MetadataKeys.WORKSPACE, (String) meta.getOrDefault(MetadataKeys.WORKSPACE, "default")
                ));
            }
        }
        return new ArrayList<>(uniqueDocs.values());
    }

    // ── Utilities ────────────────────────────────────────────────────────────

    public static String getFileExtension(String filename) {
        if (filename == null) return "";
        int dot = filename.lastIndexOf('.');
        return (dot == -1) ? "" : filename.substring(dot + 1).toLowerCase();
    }

    public static String generateDocId(String filename, String workspace) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] hash = digest.digest((filename + "_" + workspace).getBytes());
            StringBuilder hex = new StringBuilder();
            for (int i = 0; i < 8; i++) {
                hex.append(String.format("%02x", hash[i]));
            }
            return hex.toString();
        } catch (NoSuchAlgorithmException e) {
            return UUID.randomUUID().toString().substring(0, 16);
        }
    }

    private void deleteDirectoryRecursively(File file) {
        if (file.isDirectory()) {
            File[] files = file.listFiles();
            if (files != null) {
                for (File f : files) {
                    deleteDirectoryRecursively(f);
                }
            }
        }
        file.delete();
    }
}