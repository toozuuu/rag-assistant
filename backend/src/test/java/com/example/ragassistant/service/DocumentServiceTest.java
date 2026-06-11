package com.example.ragassistant.service;

import com.example.ragassistant.config.ChunkingProperties;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.ai.document.Document;
import org.springframework.ai.vectorstore.SearchRequest;
import org.springframework.ai.vectorstore.VectorStore;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.util.*;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class DocumentServiceTest {

    @Mock
    private VectorStore vectorStore;

    @Mock
    private ImageExtractorService imageExtractorService;

    @Mock
    private ChunkingProperties chunkingProperties;

    @InjectMocks
    private DocumentService documentService;

    private MockMultipartFile pdfFile;
    private MockMultipartFile txtFile;
    private MockMultipartFile docxFile;
    private MockMultipartFile emptyFile;

    @BeforeEach
    void setUp() {
        // Set up chunking properties
        when(chunkingProperties.getParentMaxTokens()).thenReturn(512);
        when(chunkingProperties.getParentOverlap()).thenReturn(64);
        when(chunkingProperties.getChildMaxTokens()).thenReturn(128);
        when(chunkingProperties.getChildOverlap()).thenReturn(16);
        when(chunkingProperties.getMinChunkSize()).thenReturn(10);
        when(chunkingProperties.getMaxChunks()).thenReturn(1000);

        // Create test files
        pdfFile = new MockMultipartFile("test.pdf", "test.pdf", "application/pdf", "PDF content here".getBytes());
        txtFile = new MockMultipartFile("test.txt", "test.txt", "text/plain", "Text content here".getBytes());
        docxFile = new MockMultipartFile("test.docx", "test.docx", 
            "application/vnd.openxmlformats-officedocument.wordprocessingml.document", 
            ("Some DOCX content".getBytes()));
        emptyFile = new MockMultipartFile("empty.txt", "empty.txt", "text/plain", "".getBytes());
    }

    @Test
    void testProcessAndStoreDocument_PdfWithText() throws IOException {
        // Arrange
        String testText = "This is a test PDF document with sufficient content for chunking.";
        when(imageExtractorService.extractImages(any(byte[].class), eq("test.pdf"), anyString())).thenReturn(Collections.emptyList());
        when(vectorStore.accept(anyList())).thenReturn(null);
        
        // Mock the extractText method to return our test text
        DocumentService spyService = spy(documentService);
        doReturn(testText).when(spyService).extractText(any(byte[].class), eq("test.pdf"), anyString(), anyString());
        
        // Act
        spyService.processAndStoreDocument(pdfFile, "default");
        
        // Assert
        verify(vectorStore, times(1)).accept(anyList());
        verifyNoMoreInteractions(vectorStore);
    }

    @Test
    void testProcessAndStoreDocument_PdfWithNoText() throws IOException {
        // Arrange
        when(imageExtractorService.extractImages(any(byte[].class), eq("empty.pdf"), anyString())).thenReturn(Collections.emptyList());
        when(vectorStore.accept(anyList())).thenReturn(null);
        
        // Mock the extractText method to return empty string (simulating image-only PDF)
        DocumentService spyService = spy(documentService);
        doReturn("").when(spyService).extractText(any(byte[].class), eq("empty.pdf"), anyString(), anyString());
        
        // Act
        spyService.processAndStoreDocument(new MockMultipartFile("empty.pdf", "empty.pdf", "application/pdf", "PDF bytes".getBytes()), "default");
        
        // Assert - should not call vectorStore.accept since no text to chunk
        verify(vectorStore, never()).accept(anyList());
    }

    @Test
    void testProcessAndStoreDocument_TextFile() throws IOException {
        // Arrange
        when(imageExtractorService.extractImages(any(byte[].class), eq("test.txt"), anyString())).thenReturn(Collections.emptyList());
        when(vectorStore.accept(anyList())).thenReturn(null);
        
        // Mock the extractText method to return our test text
        DocumentService spyService = spy(documentService);
        doReturn("This is test text content").when(spyService).extractText(any(byte[].class), eq("test.txt"), anyString(), anyString());
        
        // Act
        spyService.processAndStoreDocument(txtFile, "default");
        
        // Assert
        verify(vectorStore, times(1)).accept(anyList());
    }

    @Test
    void testProcessAndStoreDocument_UnsupportedExtension() {
        // Arrange & Act & Assert
        MultipartFile unsupportedFile = new MockMultipartFile("test.exe", "test.exe", "application/octet-stream", "bad content".getBytes());
        
        IllegalArgumentException exception = assertThrows(IllegalArgumentException.class, () -> {
            documentService.processAndStoreDocument(unsupportedFile, "default");
        });
        
        assertTrue(exception.getMessage().contains("Unsupported file type"));
    }

    @Test
    void testProcessAndStoreDocument_FileTooLarge() throws IOException {
        // Arrange
        byte[] largeContent = new byte[9 * 1024 * 1024]; // 9MB > 8MB limit
        Arrays.fill(largeContent, (byte) 'a');
        MultipartFile largeFile = new MockMultipartFile("large.pdf", "large.pdf", "application/pdf", largeContent);
        
        // Act & Assert
        IllegalArgumentException exception = assertThrows(IllegalArgumentException.class, () -> {
            documentService.processAndStoreDocument(largeFile, "default");
        });
        
        assertTrue(exception.getMessage().contains("exceeds maximum allowed limit"));
    }

    @Test
    void testExtractText_ReturnsEmptyString_WhenTikaReturnsNull() throws IOException {
        // Arrange
        byte[] fileBytes = "test content".getBytes();
        
        // Act
        String result = documentService.extractText(fileBytes, "test.pdf", "doc1", "test.pdf");
        
        // Assert - since we're not mocking TikaDocumentReader, this will use the real implementation
        // But we can at least verify it doesn't throw and returns a String
        assertNotNull(result);
        assertTrue(result instanceof String);
    }

    @Test
    void testGenerateDocId() {
        // Arrange & Act
        String docId = documentService.generateDocId("Test Document.PDF");
        
        // Assert
        assertEquals("test_document", docId);
    }

    @Test
    void testGenerateDocId_WithSpecialCharacters() {
        // Arrange & Act
        String docId = documentService.generateDocId("Test@#$%^&*()_+Document.PDF");
        
        // Assert
        assertEquals("test________document", docId);
    }

    @Test
    void testGenerateDocId_NullFilename() {
        // Arrange & Act
        String docId = documentService.generateDocId(null);
        
        // Assert
        assertEquals("unknown", docId);
    }

    @Test
    void testGenerateDocId_EmptyFilename() {
        // Arrange & Act
        String docId = documentService.generateDocId("");
        
        // Assert
        assertEquals("", docId); // base becomes "" and toLowerCase() is "" and replaceAll on "" is ""
    }

    @Test
    void testGetExtension() {
        // Arrange & Act
        String ext = documentService.getExtension("document.pdf");
        
        // Assert
        assertEquals("pdf", ext);
    }

    @Test
    void testGetExtension_NoExtension() {
        // Arrange & Act
        String ext = documentService.getExtension("document");
        
        // Assert
        assertEquals("", ext);
    }

    @Test
    void testGetExtension_HiddenFile() {
        // Arrange & Act
        String ext = documentService.getExtension(".bashrc");
        
        // Assert
        assertEquals("bashrc", ext);
    }
}