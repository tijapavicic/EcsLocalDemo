package com.example.tests.unit;

import com.example.adapters.inbound.rest.FileController;
import com.example.adapters.inbound.rest.FileUploadValidator;
import com.example.adapters.inbound.rest.RestExceptionHandler;
import com.example.core.domain.StorageException;
import com.example.core.domain.StorageObject;
import com.example.core.domain.TokenValidationException;
import com.example.core.domain.UserContext;
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
import java.util.Base64;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Unit tests for {@link FileController} — the inbound REST adapter.
 *
 * <p><strong>Uses MockMvc in standalone mode:</strong> NO Spring context started.
 * Dependencies ({@link FileServicePort}, {@link TokenExtractorPort}, {@link FileUploadValidator})
 * are mocked with Mockito. The {@link RestExceptionHandler} is registered to test the
 * full HTTP error handling chain.</p>
 *
 * <p><strong>Tests verify:</strong></p>
 * <ul>
 *   <li>Happy path: successful file upload returns 201 Created with metadata</li>
 *   <li>Error handling: domain exceptions are translated to proper HTTP responses</li>
 *   <li>Validation: invalid inputs are rejected with 400 Bad Request</li>
 *   <li>Security: Bearer token is extracted and validated</li>
 * </ul>
 *
 * @see RestExceptionHandler for centralized error handling logic
 */
@DisplayName("FileController — unit tests (standalone MockMvc)")
class FileControllerTest {

    private MockMvc mockMvc;
    private FileServicePort fileService;
    private TokenExtractorPort tokenExtractor;
    private FileUploadValidator fileValidator;

    @BeforeEach
    void setUp() {
        fileService = mock(FileServicePort.class);
        tokenExtractor = mock(TokenExtractorPort.class);
        fileValidator = mock(FileUploadValidator.class);

        // Register controller and exception handler for proper HTTP layer testing
        mockMvc = MockMvcBuilders
                .standaloneSetup(new FileController(fileService, tokenExtractor, fileValidator))
                .setControllerAdvice(new RestExceptionHandler())
                .build();
    }

    // ── Happy path ─────────────────────────────────────────────────────────────

    @Test
    @DisplayName("POST /api/files/upload — returns 201 Created with StorageObject JSON")
    void upload_returns201_withStorageObjectBody() throws Exception {
        StorageObject stored = new StorageObject(
                "users/user123/story-456/2024-01-15/abc-report.pdf", "demo-bucket",
                "application/pdf", 1024L, Instant.now(), "user123");

        UserContext userContext = UserContext.of("user123", "user@example.com", "token");
        when(tokenExtractor.extractUserContext(any())).thenReturn(userContext);
        when(fileService.upload(any(), any(), any(), any(), any())).thenReturn(stored);

        MockMultipartFile file = new MockMultipartFile(
                "file", "report.pdf", "application/pdf", "PDF content".getBytes());

        String bearerToken = Base64.getEncoder().encodeToString("user123:user@example.com".getBytes());

        mockMvc.perform(multipart("/api/files/upload")
                .file(file)
                .param("storyId", "story-456")
                .header("Authorization", "Bearer " + bearerToken))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.key").value("users/user123/story-456/2024-01-15/abc-report.pdf"))
                .andExpect(jsonPath("$.bucket").value("demo-bucket"))
                .andExpect(jsonPath("$.contentType").value("application/pdf"))
                .andExpect(jsonPath("$.sizeBytes").value(1024))
                .andExpect(jsonPath("$.userId").value("user123"));
    }

    // ── Validation errors ──────────────────────────────────────────────────────

    @Test
    @DisplayName("POST /api/files/upload — returns 400 Bad Request when file is empty")
    void upload_returns400_whenFileIsEmpty() throws Exception {
        MockMultipartFile emptyFile = new MockMultipartFile(
                "file", "empty.txt", MediaType.TEXT_PLAIN_VALUE, new byte[0]);

        mockMvc.perform(multipart("/api/files/upload").file(emptyFile))
                .andExpect(status().isBadRequest());
    }

    @Test
    @DisplayName("POST /api/files/upload — returns 400 Bad Request when file is missing")
    void upload_returns400_whenFileIsMissing() throws Exception {
        mockMvc.perform(multipart("/api/files/upload"))
                .andExpect(status().isBadRequest());
    }

    // ── Error propagation ──────────────────────────────────────────────────────

    @Test
    @DisplayName("POST /api/files/upload — returns 500 Internal Server Error when StorageException is thrown")
    void upload_returns500_whenStorageExceptionThrown() throws Exception {
        UserContext userContext = UserContext.of("user123", "user@example.com", "token");
        when(tokenExtractor.extractUserContext(any())).thenReturn(userContext);
        when(fileService.upload(any(), any(), any(), any(), any()))
                .thenThrow(new StorageException("MinIO unreachable"));

        MockMultipartFile file = new MockMultipartFile(
                "file", "file.txt", MediaType.TEXT_PLAIN_VALUE, "data".getBytes());

        String bearerToken = Base64.getEncoder().encodeToString("user123:user@example.com".getBytes());

        mockMvc.perform(multipart("/api/files/upload")
                .file(file)
                .param("storyId", "story-123")
                .header("Authorization", "Bearer " + bearerToken))
                .andExpect(status().isInternalServerError())
                .andExpect(jsonPath("$.error").value("Storage operation failed"));
    }

    @Test
    @DisplayName("POST /api/files/upload — returns 401 Unauthorized when token validation fails")
    void upload_returns401_whenTokenValidationFails() throws Exception {
        when(tokenExtractor.extractUserContext(any()))
                .thenThrow(new TokenValidationException("Invalid token"));

        MockMultipartFile file = new MockMultipartFile(
                "file", "file.txt", MediaType.TEXT_PLAIN_VALUE, "data".getBytes());

        String invalidBearerToken = "invalid-token";

        mockMvc.perform(multipart("/api/files/upload")
                .file(file)
                .param("storyId", "story-123")
                .header("Authorization", "Bearer " + invalidBearerToken))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.error").value("Invalid or expired token"));
    }
}

