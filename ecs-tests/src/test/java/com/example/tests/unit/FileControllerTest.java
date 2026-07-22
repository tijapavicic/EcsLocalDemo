package com.example.tests.unit;

import com.example.adapters.inbound.rest.FileController;
import com.example.core.domain.StorageException;
import com.example.core.domain.StorageObject;
import com.example.core.domain.TokenValidationException;
import com.example.core.ports.FileServicePort;
import com.example.core.ports.TokenExtractorPort;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import java.time.Instant;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Unit tests for {@link FileController} — the inbound REST adapter.
 *
 * <p>Uses MockMvc in <em>standalone mode</em> — NO Spring context is started.
 * The FileServicePort and TokenExtractorPort are mocked with Mockito.</p>
 */
@DisplayName("FileController — unit tests (standalone MockMvc)")
class FileControllerTest {

    private MockMvc mockMvc;
    private FileServicePort fileService;
    private TokenExtractorPort tokenExtractor;

    @BeforeEach
    void setUp() {
        fileService = mock(FileServicePort.class);
        tokenExtractor = mock(TokenExtractorPort.class);
        mockMvc = MockMvcBuilders
                .standaloneSetup(new FileController(fileService, tokenExtractor))
                .build();
    }

    @Test
    @DisplayName("POST /api/files/upload — returns 201 with StorageObject JSON")
    void upload_returns201_withStorageObjectBody() throws Exception {
        StorageObject stored = new StorageObject(
                "users/user123/2024-01-15/abc-report.pdf", "demo-bucket",
                "application/pdf", 1024L, Instant.now(), "user123");

        // Mock TokenExtractorPort to return a valid UserContext
        com.example.core.domain.UserContext userContext = new com.example.core.domain.UserContext("user123", "user@example.com", "token");
        when(tokenExtractor.extractUserContext(any())).thenReturn(userContext);
        when(fileService.upload(any(), any(), any(), any())).thenReturn(stored);

        MockMultipartFile file = new MockMultipartFile(
                "file", "report.pdf", "application/pdf", "PDF content".getBytes());

        // Create a Bearer token (base64: "user123:user@example.com")
        String bearerToken = java.util.Base64.getEncoder().encodeToString("user123:user@example.com".getBytes());

        mockMvc.perform(multipart("/api/files/upload")
                .file(file)
                .header("Authorization", "Bearer " + bearerToken))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.key").value("users/user123/2024-01-15/abc-report.pdf"))
                .andExpect(jsonPath("$.bucket").value("demo-bucket"))
                .andExpect(jsonPath("$.contentType").value("application/pdf"))
                .andExpect(jsonPath("$.sizeBytes").value(1024))
                .andExpect(jsonPath("$.userId").value("user123"));
    }

    @Test
    @DisplayName("POST /api/files/upload — returns 400 when file is empty")
    void upload_returns400_whenFileIsEmpty() throws Exception {
        MockMultipartFile emptyFile = new MockMultipartFile(
                "file", "empty.txt", MediaType.TEXT_PLAIN_VALUE, new byte[0]);

        mockMvc.perform(multipart("/api/files/upload").file(emptyFile))
                .andExpect(status().isBadRequest());
    }

    @Test
    @DisplayName("POST /api/files/upload — returns 500 when StorageException is thrown")
    void upload_returns500_whenStorageExceptionThrown() throws Exception {
        // Mock TokenExtractorPort to return a valid UserContext
        com.example.core.domain.UserContext userContext = new com.example.core.domain.UserContext("user123", "user@example.com", "token");
        when(tokenExtractor.extractUserContext(any())).thenReturn(userContext);
        when(fileService.upload(any(), any(), any(), any()))
                .thenThrow(new StorageException("MinIO unreachable"));

        MockMultipartFile file = new MockMultipartFile(
                "file", "file.txt", MediaType.TEXT_PLAIN_VALUE, "data".getBytes());

        // Create a Bearer token
        String bearerToken = java.util.Base64.getEncoder().encodeToString("user123:user@example.com".getBytes());

        mockMvc.perform(multipart("/api/files/upload")
                .file(file)
                .header("Authorization", "Bearer " + bearerToken))
                .andExpect(status().isInternalServerError())
                .andExpect(jsonPath("$.error").value("Storage operation failed"));
    }
}

