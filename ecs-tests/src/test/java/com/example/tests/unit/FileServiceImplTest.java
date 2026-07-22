package com.example.tests.unit;

import com.example.core.domain.StorageException;
import com.example.core.domain.StorageObject;
import com.example.core.domain.UserContext;
import com.example.core.ports.StoragePort;
import com.example.core.usecases.FileServiceImpl;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.Instant;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Unit tests for {@link FileServiceImpl} — the core use-case.
 *
 * <p>NO Spring context. Pure JUnit 5 + Mockito.</p>
 */
@DisplayName("FileServiceImpl — unit tests")
class FileServiceImplTest {

    private static final String BUCKET = "test-bucket";
    private static final byte[] CONTENT = "hello world".getBytes();
    private static final String FILENAME = "hello.txt";
    private static final String CONTENT_TYPE = "text/plain";

    private StoragePort storagePort;
    private FileServiceImpl fileService;

    @BeforeEach
    void setUp() {
        storagePort = mock(StoragePort.class);
        fileService = new FileServiceImpl(storagePort, BUCKET);
    }

    // ── Happy path ────────────────────────────────────────────────────────────

    @Test
    @DisplayName("upload() — delegates to StoragePort with correct bucket and content")
    void upload_delegatesToStoragePort_withCorrectArguments() throws StorageException {
        StorageObject expected = new StorageObject("key", BUCKET, CONTENT_TYPE, (long) CONTENT.length, Instant.now(), null);
        when(storagePort.store(eq(BUCKET), any(), eq(CONTENT_TYPE), eq(CONTENT))).thenReturn(expected);

        StorageObject result = fileService.upload(FILENAME, CONTENT_TYPE, CONTENT);

        assertThat(result).isNotNull();
        assertThat(result.getBucket()).isEqualTo(BUCKET);
        assertThat(result.getContentType()).isEqualTo(CONTENT_TYPE);
        verify(storagePort).store(eq(BUCKET), any(), eq(CONTENT_TYPE), eq(CONTENT));
    }

    @Test
    @DisplayName("upload() — key contains today's date, userId and original filename")
    void upload_generatedKey_containsDateUserIdAndFilename() throws StorageException {
        when(storagePort.store(any(), any(), any(), any()))
                .thenAnswer(inv -> new StorageObject(inv.getArgument(1), BUCKET, CONTENT_TYPE, (long) CONTENT.length, Instant.now(), null));

        UserContext user = new UserContext("user123", "user@example.com", "token");
        fileService.upload(user, FILENAME, CONTENT_TYPE, CONTENT);

        // Capture the key that was passed to storagePort
        var captor = org.mockito.ArgumentCaptor.forClass(String.class);
        verify(storagePort).store(eq(BUCKET), captor.capture(), any(), any());
        String capturedKey = captor.getValue();

        // Key should have format: users/{userId}/{date}/{uuid-filename}
        assertThat(capturedKey).contains("users/user123");
        assertThat(capturedKey).contains(FILENAME);
        assertThat(capturedKey).matches("users/user123/\\d{4}-\\d{2}-\\d{2}/.+");
    }

    // ── Validation ────────────────────────────────────────────────────────────

    @Test
    @DisplayName("upload() — throws IllegalArgumentException when content is empty")
    void upload_throwsIllegalArgument_whenContentIsEmpty() {
        assertThatThrownBy(() -> fileService.upload(FILENAME, CONTENT_TYPE, new byte[0]))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("content");
    }

    @Test
    @DisplayName("upload() — throws IllegalArgumentException when filename is blank")
    void upload_throwsIllegalArgument_whenFilenameIsBlank() {
        assertThatThrownBy(() -> fileService.upload("   ", CONTENT_TYPE, CONTENT))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("filename");
    }

    @Test
    @DisplayName("upload() — throws IllegalArgumentException when filename is null")
    void upload_throwsIllegalArgument_whenFilenameIsNull() {
        assertThatThrownBy(() -> fileService.upload(null, CONTENT_TYPE, CONTENT))
                .isInstanceOf(IllegalArgumentException.class);
    }

    // ── Error propagation ─────────────────────────────────────────────────────

    @Test
    @DisplayName("upload() — propagates StorageException from StoragePort")
    void upload_propagatesStorageException_fromStoragePort() throws StorageException {
        when(storagePort.store(any(), any(), any(), any()))
                .thenThrow(new StorageException("S3 connection refused"));

        assertThatThrownBy(() -> fileService.upload(FILENAME, CONTENT_TYPE, CONTENT))
                .isInstanceOf(StorageException.class)
                .hasMessageContaining("S3 connection refused");
    }

    // ── Constructor guards ────────────────────────────────────────────────────

    @Test
    @DisplayName("constructor — throws NullPointerException when storagePort is null")
    void constructor_throwsNPE_whenStoragePortIsNull() {
        assertThatThrownBy(() -> new FileServiceImpl(null, BUCKET))
                .isInstanceOf(NullPointerException.class);
    }

    @Test
    @DisplayName("constructor — throws IllegalArgumentException when bucket is blank")
    void constructor_throwsIllegalArgument_whenBucketIsBlank() {
        assertThatThrownBy(() -> new FileServiceImpl(storagePort, ""))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("bucket");
    }
}

