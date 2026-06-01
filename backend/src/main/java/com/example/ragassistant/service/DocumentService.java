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

    public void processAndStoreDocument(MultipartFile file, String workspace) throws IOException {
        byte[] fileBytes = file.getBytes();
        String originalFilename = file.getOriginalFilename();
        String docId = generateDocId(originalFilename);

        // 1. Extract embedded images
        List<String> imageRefs = imageExtractorService.extractImages(fileBytes, originalFilename, docId);
        log.info("Extracted {} image(s) from document: {}", imageRefs.size(), originalFilename);

        // 2. Extract text
        String lowerFilename = originalFilename != null ? originalFilename.toLowerCase() : "";
        String fullText = extractText(fileBytes, originalFilename, docId, lowerFilename);

        // 3. Chunking & Ingestion
        List<Document> splitDocuments;
        if (lowerFilename.endsWith(".pdf") && fullText != null && !fullText.isEmpty()) {
            splitDocuments = chunkAndEnrichPdf(fullText, originalFilename, docId, workspace, imageRefs);
        } else {
            splitDocuments = chunkAndEnrichGeneral(fullText, originalFilename, docId, workspace);
        }

        // 4. Store in Qdrant
        vectorStore.accept(splitDocuments);
        log.info("Stored {} chunks for document: {}", splitDocuments.size(), originalFilename);
    }

    private String extractText(byte[] fileBytes, String originalFilename, String docId, String lowerFilename) throws IOException {
        if (lowerFilename.endsWith(".docx")) {
            return extractDocxWithImageTags(fileBytes, docId);
        }
        
        ByteArrayResource resource = new ByteArrayResource(fileBytes) {
            @Override
            public String getFilename() { return originalFilename; }
        };
        TikaDocumentReader documentReader = new TikaDocumentReader(resource);
        List<Document> documents = documentReader.get();
        return documents.isEmpty() ? "" : documents.get(0).getContent();
    }

    private List<Document> chunkAndEnrichPdf(String fullText, String originalFilename, String docId, String workspace, List<String> imageRefs) {
        TokenTextSplitter parentSplitter = new TokenTextSplitter(1000, 200, 5, 10000, true);
        TokenTextSplitter childSplitter = new TokenTextSplitter(150, 30, 5, 10000, true);
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
        TokenTextSplitter parentSplitter = new TokenTextSplitter(1000, 200, 5, 10000, true);
        TokenTextSplitter childSplitter = new TokenTextSplitter(150, 30, 5, 10000, true);
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

    private String generateDocId(String filename) {
        if (filename == null) return "unknown";
        // Remove extension, lowercase, replace non-alphanumeric chars with underscore
        String base = filename.contains(".")
                ? filename.substring(0, filename.lastIndexOf('.'))
                : filename;
        return base.toLowerCase().replaceAll("[^a-z0-9]+", "_");
    }
}
