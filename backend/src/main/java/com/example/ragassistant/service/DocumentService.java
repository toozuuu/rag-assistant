package com.example.ragassistant.service;

import com.example.ragassistant.config.ChunkingProperties;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.document.Document;
import org.springframework.ai.reader.tika.TikaDocumentReader;
import org.springframework.ai.transformer.splitter.TokenTextSplitter;
import org.springframework.ai.vectorstore.SearchRequest;
import org.springframework.ai.vectorstore.VectorStore;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.io.ByteArrayResource;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;
import org.springframework.web.multipart.MultipartFile;
import org.apache.poi.xwpf.usermodel.*;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

@Service
@RequiredArgsConstructor
@Slf4j
public class DocumentService {

    private static final long MAX_FILE_SIZE = 8 * 1024 * 1024; // 8MB
    private static final Set<String> ALLOWED_EXTENSIONS = Set.of("pdf", "txt", "docx", "html", "md");

    private final VectorStore vectorStore;
    private final ImageExtractorService imageExtractorService;
    private final ChunkingProperties chunkingProperties;

    @Value("${spring.ai.vectorstore.qdrant.host:localhost}")
    private String qdrantHost;

    @Value("${qdrant.rest-port:6333}")
    private int qdrantRestPort;

    @Value("${spring.ai.vectorstore.qdrant.collection-name:rag-docs}")
    private String collectionName;

    public void processAndStoreDocument(MultipartFile file, String workspace) throws IOException {
        byte[] fileBytes = file.getBytes();
        String originalFilename = file.getOriginalFilename();

        if (originalFilename == null || originalFilename.isBlank()) {
            throw new IllegalArgumentException("Filename must not be empty");
        }

        String ext = getExtension(originalFilename);
        if (!ALLOWED_EXTENSIONS.contains(ext)) {
            throw new IllegalArgumentException("Unsupported file type: ." + ext + ". Allowed: " + ALLOWED_EXTENSIONS);
        }

        if (fileBytes.length > MAX_FILE_SIZE) {
            throw new IllegalArgumentException("File size exceeds maximum allowed limit of 8MB");
        }

        String docId = generateDocId(originalFilename);
        log.info("Processing document: {} (workspace: {}, size: {} bytes)", originalFilename, workspace, fileBytes.length);

        // 1. Extract embedded images
        List<String> imageRefs = imageExtractorService.extractImages(fileBytes, originalFilename, docId);
        log.info("Extracted {} image(s) from document: {}", imageRefs.size(), originalFilename);

        try {
            // 2. Extract text
            String lowerFilename = originalFilename.toLowerCase();
            String fullText = extractText(fileBytes, originalFilename, docId, lowerFilename);

            if (fullText == null || fullText.isBlank()) {
                log.warn("No extractable text in document: {} (size: {} bytes). Skipping chunking and storage.", 
                        originalFilename, fileBytes.length);
                return;
            }

            log.debug("Extracted text length: {} characters for document: {}", fullText.length(), originalFilename);

            // 3. Chunking & Ingestion
            List<Document> splitDocuments;
            if (ext.equals("pdf")) {
                splitDocuments = chunkAndEnrichPdf(fullText, originalFilename, docId, workspace, imageRefs);
            } else {
                splitDocuments = chunkAndEnrichGeneral(fullText, originalFilename, docId, workspace);
            }

            log.debug("Created {} chunks for document: {}", splitDocuments.size(), originalFilename);

            // 4. Store in Qdrant
            vectorStore.accept(splitDocuments);
            log.info("Stored {} chunks for document: {} (workspace: {})", 
                    splitDocuments.size(), originalFilename, workspace);
        } catch (Exception e) {
            log.error("Failed to process document: {} (workspace: {}) - {}", 
                    originalFilename, workspace, e.getMessage(), e);
            cleanupExtractedImages(docId);
            log.warn("Cleaned up extracted images for docId {} due to processing failure", docId);
            throw e;
        }
    }

    private String getExtension(String filename) {
        int dotIdx = filename.lastIndexOf('.');
        if (dotIdx < 0) return "";
        return filename.substring(dotIdx + 1).toLowerCase();
    }

    private void cleanupExtractedImages(String docId) {
        try {
            Path docDir = Paths.get(ImageExtractorService.UPLOAD_DIR, docId);
            if (Files.exists(docDir)) {
                try (var files = Files.list(docDir)) {
                    files.forEach(file -> {
                        try { Files.deleteIfExists(file); } catch (IOException ignored) {}
                    });
                }
                Files.deleteIfExists(docDir);
            }
        } catch (IOException e) {
            log.warn("Failed to clean up images for docId {}: {}", docId, e.getMessage());
        }
    }

    private String extractText(byte[] fileBytes, String originalFilename, String docId, String lowerFilename) throws IOException {
        if (lowerFilename.endsWith(".docx")) {
            return extractDocxWithImageTags(fileBytes, docId);
        }
        
        try {
            ByteArrayResource resource = new ByteArrayResource(fileBytes) {
                @Override
                public String getFilename() { return originalFilename; }
            };
            TikaDocumentReader documentReader = new TikaDocumentReader(resource);
            List<Document> documents = documentReader.get();
            if (!documents.isEmpty() && documents.get(0).getContent() != null) {
                return documents.get(0).getContent();
            }
        } catch (Exception e) {
            log.warn("Failed to extract text from {} (size: {} bytes): {}", 
                    originalFilename, fileBytes.length, e.getMessage());
        }
        return "";
    }

    private List<Document> chunkAndEnrichPdf(String fullText, String originalFilename, String docId, String workspace, List<String> imageRefs) {
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
        List<Document> splitDocuments = new ArrayList<>();

        // PDF: Page-by-page Parent-Child chunking using form feed character
        String[] pages = fullText.split("\\f");
        for (int p = 0; p < pages.length; p++) {
            String pageText = pages[p];
            if (pageText.trim().isEmpty()) continue;

            final int pageIdx = p;
            List<String> pageImages = imageRefs.stream()
                    .filter(ref -> ref.contains("/page_" + pageIdx + "_img_"))
                    .toList();

            Document pageDoc = new Document(pageText);
            List<Document> parentChunks = parentSplitter.apply(List.of(pageDoc));

            for (Document parentChunk : parentChunks) {
                StringBuilder parentContent = new StringBuilder(parentChunk.getContent());
                for (String imgRef : pageImages) {
                    parentContent.append("\n[image: ").append(imgRef).append("]\n");
                }
                String parentTextEnriched = parentContent.toString();

                Document enrichedParentDoc = new Document(parentTextEnriched);
                List<Document> childChunks = childSplitter.apply(List.of(enrichedParentDoc));

                for (Document childChunk : childChunks) {
                    Document enrichedChild = new Document(childChunk.getContent(), childChunk.getMetadata());
                    enrichedChild.getMetadata().put("filename", originalFilename);
                    enrichedChild.getMetadata().put("doc_id", docId);
                    enrichedChild.getMetadata().put("page_number", pageIdx + 1);
                    enrichedChild.getMetadata().put("parent_text", parentTextEnriched);
                    enrichedChild.getMetadata().put("workspace", workspace);
                    splitDocuments.add(enrichedChild);
                }
            }
        }
        return splitDocuments;
    }

    private List<Document> chunkAndEnrichGeneral(String fullText, String originalFilename, String docId, String workspace) {
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
        List<Document> splitDocuments = new ArrayList<>();

        Document doc = new Document(fullText);
        List<Document> parentChunks = parentSplitter.apply(List.of(doc));

        for (Document parentChunk : parentChunks) {
            String parentText = parentChunk.getContent();
            List<Document> childChunks = childSplitter.apply(List.of(parentChunk));

            for (Document childChunk : childChunks) {
                Document enrichedChild = new Document(childChunk.getContent(), childChunk.getMetadata());
                enrichedChild.getMetadata().put("filename", originalFilename);
                enrichedChild.getMetadata().put("doc_id", docId);
                enrichedChild.getMetadata().put("parent_text", parentText);
                enrichedChild.getMetadata().put("workspace", workspace);
                splitDocuments.add(enrichedChild);
            }
        }
        return splitDocuments;
    }

    private String extractDocxWithImageTags(byte[] fileBytes, String docId) throws IOException {
        StringBuilder sb = new StringBuilder();
        try (XWPFDocument doc = new XWPFDocument(new ByteArrayInputStream(fileBytes))) {
            int[] imgCounter = new int[]{0};
            for (IBodyElement element : doc.getBodyElements()) {
                if (element instanceof XWPFParagraph para) {
                    processParagraph(para, sb, docId, imgCounter);
                } else if (element instanceof XWPFTable table) {
                    processTable(table, sb, docId, imgCounter);
                }
            }
        }
        return sb.toString();
    }

    private void processTable(XWPFTable table, StringBuilder sb, String docId, int[] imgCounter) {
        for (XWPFTableRow row : table.getRows()) {
            for (XWPFTableCell cell : row.getTableCells()) {
                for (XWPFParagraph para : cell.getParagraphs()) {
                    processParagraph(para, sb, docId, imgCounter);
                }
            }
        }
    }

    private void processParagraph(XWPFParagraph para, StringBuilder sb, String docId, int[] imgCounter) {
        sb.append(para.getText());
        if (para.getRuns() != null) {
            for (XWPFRun run : para.getRuns()) {
                processRunPictures(run, sb, docId, imgCounter);
            }
        }
        sb.append("\n");
    }

    private void processRunPictures(XWPFRun run, StringBuilder sb, String docId, int[] imgCounter) {
        List<XWPFPicture> pictures = run.getEmbeddedPictures();
        if (pictures != null && !pictures.isEmpty()) {
            for (XWPFPicture pic : pictures) {
                XWPFPictureData picData = pic.getPictureData();
                String picExt = picData.suggestFileExtension();
                String ext = picExt.isEmpty() ? "png" : picExt;
                String fname = String.format("img_%d.%s", imgCounter[0]++, ext);
                sb.append("\n[image: ").append(docId).append("/").append(fname).append("]\n");
            }
        }
    }

    public List<Map<String, String>> listDocuments(String workspace) {
        String safeWorkspace = workspace != null ? workspace.replace("'", "\\'") : "default";
        List<Document> docs = vectorStore.similaritySearch(
                SearchRequest.query("document")
                        .withTopK(100)
                        .withSimilarityThreshold(0.0)
                        .withFilterExpression("workspace == '" + safeWorkspace + "'")
        );

        Map<String, Map<String, String>> uniqueDocs = new HashMap<>();
        for (Document doc : docs) {
            String docId = String.valueOf(doc.getMetadata().getOrDefault("doc_id", ""));
            String filename = String.valueOf(doc.getMetadata().getOrDefault("filename", "Unknown"));
            if (!docId.isEmpty() && !uniqueDocs.containsKey(docId)) {
                Map<String, String> entry = new HashMap<>();
                entry.put("docId", docId);
                entry.put("filename", filename);
                uniqueDocs.put(docId, entry);
            }
        }
        return new ArrayList<>(uniqueDocs.values());
    }

    public void deleteDocument(String docId, String workspace) {
        String safeWorkspace = workspace != null ? workspace.replace("'", "\\'") : "default";
        String safeDocId = docId != null ? docId.replace("'", "\\'") : "";

        // Use Qdrant REST API to delete points by filter
        try {
            RestTemplate rt = new RestTemplate();
            String url = String.format("http://%s:%d/collections/%s/points/delete",
                    qdrantHost, qdrantRestPort, collectionName);

            String requestBody = String.format("""
                    {
                      "filter": {
                        "must": [
                          {"key": "workspace", "match": {"value": "%s"}},
                          {"key": "doc_id", "match": {"value": "%s"}}
                        ]
                      }
                    }
                    """, safeWorkspace, safeDocId);

            HttpHeaders headers = new HttpHeaders();
            headers.setContentType(MediaType.APPLICATION_JSON);
            HttpEntity<String> request = new HttpEntity<>(requestBody, headers);
            rt.postForEntity(url, request, String.class);
        } catch (Exception e) {
            log.warn("Failed to delete document '{}' from Qdrant: {}", docId, e.getMessage());
        }

        cleanupExtractedImages(docId);
    }

    private String generateDocId(String filename) {
        if (filename == null) return "unknown";
        // Remove extension, lowercase, replace non-alphanumeric chars with underscore
        String base = filename.contains(".")
                ? filename.substring(0, filename.lastIndexOf('.'))
                : filename;
        return base.toLowerCase().replaceAll("[^a-z0-9]+", "_");
    }
}