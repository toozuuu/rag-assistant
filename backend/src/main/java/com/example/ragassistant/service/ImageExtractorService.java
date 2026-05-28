package com.example.ragassistant.service;

import lombok.extern.slf4j.Slf4j;
import org.apache.pdfbox.Loader;
import org.apache.pdfbox.cos.COSName;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.pdmodel.PDPage;
import org.apache.pdfbox.pdmodel.PDResources;
import org.apache.pdfbox.pdmodel.graphics.PDXObject;
import org.apache.pdfbox.pdmodel.graphics.image.PDImageXObject;
import org.apache.poi.xwpf.usermodel.XWPFDocument;
import org.apache.poi.xwpf.usermodel.XWPFPictureData;
import org.springframework.stereotype.Service;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.List;

@Service
@Slf4j
public class ImageExtractorService {

    public static final String UPLOAD_DIR = "uploads/images";

    /**
     * Extracts images from the given file bytes based on file extension.
     * Returns a list of relative paths: "{docId}/{filename}.png"
     */
    public List<String> extractImages(byte[] fileBytes, String originalFilename, String docId) {
        if (originalFilename == null) return List.of();
        String ext = originalFilename.substring(originalFilename.lastIndexOf('.') + 1).toLowerCase();
        return switch (ext) {
            case "pdf"  -> extractFromPdf(fileBytes, docId);
            case "docx" -> extractFromDocx(fileBytes, docId);
            default     -> List.of();
        };
    }

    // ── PDF Image Extraction ──────────────────────────────────────────────────
    private List<String> extractFromPdf(byte[] fileBytes, String docId) {
        List<String> imagePaths = new ArrayList<>();
        Path docDir = Paths.get(UPLOAD_DIR, docId);
        try {
            Files.createDirectories(docDir);
            try (PDDocument document = Loader.loadPDF(fileBytes)) {
                int pageIndex = 0;
                int imgCounter = 0;
                for (PDPage page : document.getPages()) {
                    PDResources resources = page.getResources();
                    if (resources == null) { pageIndex++; continue; }

                    for (COSName name : resources.getXObjectNames()) {
                        try {
                            PDXObject xObject = resources.getXObject(name);
                            if (xObject instanceof PDImageXObject image) {
                                BufferedImage bi = image.getImage();
                                // Skip tiny icons/decorations (less than 80x80 px)
                                if (bi != null && bi.getWidth() > 80 && bi.getHeight() > 80) {
                                    String fname = String.format("page_%d_img_%d.png", pageIndex, imgCounter++);
                                    ImageIO.write(bi, "PNG", docDir.resolve(fname).toFile());
                                    imagePaths.add(docId + "/" + fname);
                                    log.info("Extracted image: {}/{}", docId, fname);
                                }
                            }
                        } catch (Exception e) {
                            log.debug("Skipping xobject on page {}: {}", pageIndex, e.getMessage());
                        }
                    }
                    pageIndex++;
                }
            }
        } catch (Exception e) {
            log.warn("PDF image extraction failed for docId {}: {}", docId, e.getMessage());
        }
        return imagePaths;
    }

    // ── DOCX Image Extraction ─────────────────────────────────────────────────
    private List<String> extractFromDocx(byte[] fileBytes, String docId) {
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
                    log.info("Extracted DOCX image: {}/{}", docId, fname);
                }
            }
        } catch (Exception e) {
            log.warn("DOCX image extraction failed for docId {}: {}", docId, e.getMessage());
        }
        return imagePaths;
    }
}
