package com.example.ragassistant.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.document.Document;
import org.springframework.ai.reader.tika.TikaDocumentReader;
import org.springframework.ai.transformer.splitter.TokenTextSplitter;
import org.springframework.ai.vectorstore.VectorStore;
import org.springframework.core.io.ByteArrayResource;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;
import org.apache.poi.xwpf.usermodel.*;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

@Service
@RequiredArgsConstructor
@Slf4j
public class DocumentService {

    private final VectorStore vectorStore;
    private final ImageExtractorService imageExtractorService;

    public void processAndStoreDocument(MultipartFile file) throws IOException {
        byte[] fileBytes = file.getBytes();
        String originalFilename = file.getOriginalFilename();

        // Generate a safe doc ID from filename
        String docId = generateDocId(originalFilename);

        // 1. Extract embedded images (PDF / DOCX)
        List<String> imageRefs = imageExtractorService.extractImages(fileBytes, originalFilename, docId);
        log.info("Extracted {} image(s) from document: {}", imageRefs.size(), originalFilename);

        // 2. Extract text & embed image references
        String fullText = "";
        String lowerFilename = originalFilename != null ? originalFilename.toLowerCase() : "";

        if (lowerFilename.endsWith(".docx")) {
            fullText = extractDocxWithImageTags(fileBytes, docId);
        } else {
            // PDF / HTML / TXT: use Tika to extract text
            ByteArrayResource resource = new ByteArrayResource(fileBytes) {
                @Override
                public String getFilename() { return originalFilename; }
            };
            TikaDocumentReader documentReader = new TikaDocumentReader(resource);
            List<Document> documents = documentReader.get();
            fullText = documents.isEmpty() ? "" : documents.get(0).getContent();
        }

        // 3. Semantic chunking (500 tokens, 100 overlap) and Metadata enrichment
        TokenTextSplitter textSplitter = new TokenTextSplitter(500, 100, 5, 10000, true);
        List<Document> splitDocuments = new ArrayList<>();

        if (lowerFilename.endsWith(".pdf") && fullText != null && !fullText.isEmpty()) {
            // PDF: Page-by-page chunking & page-level image association
            String[] pages = fullText.split("\\f|\\u000c");
            for (int p = 0; p < pages.length; p++) {
                String pageText = pages[p];
                if (pageText.trim().isEmpty()) continue;

                // Find images extracted from this specific page (0-indexed)
                final int pageIdx = p;
                List<String> pageImages = imageRefs.stream()
                        .filter(ref -> ref.contains("/page_" + pageIdx + "_img_"))
                        .toList();

                Document pageDoc = new Document(pageText);
                List<Document> pageChunks = textSplitter.apply(List.of(pageDoc));

                for (Document chunk : pageChunks) {
                    // Append page-level image tags to the chunk content so they stay close to the text
                    StringBuilder chunkContent = new StringBuilder(chunk.getContent());
                    for (String imgRef : pageImages) {
                        chunkContent.append("\n[image: ").append(imgRef).append("]\n");
                    }

                    Document enrichedChunk = new Document(chunkContent.toString(), chunk.getMetadata());
                    enrichedChunk.getMetadata().put("filename", originalFilename);
                    enrichedChunk.getMetadata().put("doc_id", docId);
                    enrichedChunk.getMetadata().put("page_number", pageIdx + 1);
                    splitDocuments.add(enrichedChunk);
                }
            }
        } else {
            // DOCX / HTML / TXT: Chunk the text (which already has embedded image tags for DOCX)
            Document doc = new Document(fullText);
            List<Document> rawChunks = textSplitter.apply(List.of(doc));

            for (Document chunk : rawChunks) {
                chunk.getMetadata().put("filename", originalFilename);
                chunk.getMetadata().put("doc_id", docId);
                splitDocuments.add(chunk);
            }
        }

        // 4. Store in Qdrant
        vectorStore.accept(splitDocuments);
        log.info("Stored {} chunks for document: {}", splitDocuments.size(), originalFilename);
    }

    private String extractDocxWithImageTags(byte[] fileBytes, String docId) throws IOException {
        StringBuilder sb = new StringBuilder();
        try (XWPFDocument doc = new XWPFDocument(new ByteArrayInputStream(fileBytes))) {
            int[] imgCounter = new int[]{0};
            for (IBodyElement element : doc.getBodyElements()) {
                if (element instanceof XWPFParagraph para) {
                    processParagraph(para, sb, docId, imgCounter);
                } else if (element instanceof XWPFTable table) {
                    for (XWPFTableRow row : table.getRows()) {
                        for (XWPFTableCell cell : row.getTableCells()) {
                            for (XWPFParagraph para : cell.getParagraphs()) {
                                processParagraph(para, sb, docId, imgCounter);
                            }
                        }
                    }
                }
            }
        }
        return sb.toString();
    }

    private void processParagraph(XWPFParagraph para, StringBuilder sb, String docId, int[] imgCounter) {
        sb.append(para.getText());
        if (para.getRuns() != null) {
            for (XWPFRun run : para.getRuns()) {
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
        }
        sb.append("\n");
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
