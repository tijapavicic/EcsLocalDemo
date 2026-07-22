package com.example.tests.unit;

import com.example.core.domain.StorageException;
import com.example.core.domain.StorageObject;
import com.example.core.domain.UserContext;
import com.example.core.ports.StorageKeyGeneratorPort;
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
 * <p><strong>NO Spring context.</strong> Pure JUnit 5 + Mockito.</p>
 *
 * <p><strong>Tests verify:</strong></p>
 * <ul>
 *   <li>Happy path: successful file upload with user context</li>
 *   <li>Integration: delegates to StoragePort and StorageKeyGeneratorPort correctly</li>
 *   <li>Validation: rejects invalid parameters (null, blank, empty content)</li>
 *   <li>Error handling: propagates exceptions from dependencies</li>
 *   <li>Constructor guards: fails fast on null/blank inputs</li>
 * </ul>
 */
@DisplayName("FileServiceImpl — unit tests")
class FileServiceImplTest {

    private static final String BUCKET = "test-bucket";
    private static final String USER_ID = "user123";
    private static final String EMAIL = "user@example.com";
    private static final String TOKEN = "token";
    private static final String STORY_ID = "story-456";
    private static final byte[] CONTENT = "hello world".getBytes();
    private static final String FILENAME = "hello.txt";
    private static final String CONTENT_TYPE = "text/plain";
    private static final String GENERATED_KEY = "users/user123/story-456/2024-01-15/abc123-hello.txt";

    private StoragePort storagePort;
    private StorageKeyGeneratorPort keyGenerator;
    private FileServiceImpl fileService;

    @BeforeEach
    void setUp() {
        storagePort = mock(StoragePort.class);
        keyGenerator = mock(StorageKeyGeneratorPort.class);
        fileService = new FileServiceImpl(storagePort, keyGenerator, BUCKET);
    }

    // ── Happy path ────────────────────────────────────────────────────────────

    @Test
    @DisplayName("upload() — delegates to StoragePort with correct bucket, key, and content")
    void upload_delegatesToStoragePort_withCorrectArguments() throws StorageException {
        StorageObject mockStorageResult = new StorageObject(GENERATED_KEY, BUCKET, CONTENT_TYPE, (long) CONTENT.length, Instant.now(), null);
        when(keyGenerator.generateKey(USER_ID, STORY_ID, FILENAME)).thenReturn(GENERATED_KEY);
        when(storagePort.store(eq(BUCKET), eq(GENERATED_KEY), eq(CONTENT_TYPE), eq(CONTENT)))
                .thenReturn(mockStorageResult);

        UserContext user = new UserContext(USER_ID, EMAIL, TOKEN);
        StorageObject result = fileService.upload(user, STORY_ID, FILENAME, CONTENT_TYPE, CONTENT);

        assertThat(result).isNotNull();
        assertThat(result.getBucket()).isEqualTo(BUCKET);
        assertThat(result.getContentType()).isEqualTo(CONTENT_TYPE);
        assertThat(result.getSizeBytes()).isEqualTo((long) CONTENT.length);
        verify(keyGenerator).generateKey(USER_ID, STORY_ID, FILENAME);
        verify(storagePort).store(eq(BUCKET), eq(GENERATED_KEY), eq(CONTENT_TYPE), eq(CONTENT));
    }

    @Test
    @DisplayName("upload() — attaches userId to response for audit trail")
    void upload_attachesUserId_toResponse() throws StorageException {
        StorageObject mockStorageResult = new StorageObject(GENERATED_KEY, BUCKET, CONTENT_TYPE, (long) CONTENT.length, Instant.now(), null);
        when(keyGenerator.generateKey(USER_ID, STORY_ID, FILENAME)).thenReturn(GENERATED_KEY);
        when(storagePort.store(any(), any(), any(), any())).thenReturn(mockStorageResult);

        UserContext user = new UserContext(USER_ID, EMAIL, TOKEN);
        StorageObject result = fileService.upload(user, STORY_ID, FILENAME, CONTENT_TYPE, CONTENT);

        assertThat(result.getUserId()).isEqualTo(USER_ID);
    }

    @Test
    @DisplayName("upload() — delegates key generation to StorageKeyGeneratorPort with userId, storyId, and filename")
    void upload_delegatesToKeyGenerator_withUserIdStoryIdAndFilename() throws StorageException {
        when(keyGenerator.generateKey(USER_ID, STORY_ID, FILENAME)).thenReturn(GENERATED_KEY);
        when(storagePort.store(any(), any(), any(), any()))
                .thenAnswer(inv -> new StorageObject(inv.getArgument(1), BUCKET, CONTENT_TYPE, (long) CONTENT.length, Instant.now(), null));

        UserContext user = new UserContext(USER_ID, EMAIL, TOKEN);
        fileService.upload(user, STORY_ID, FILENAME, CONTENT_TYPE, CONTENT);

        verify(keyGenerator).generateKey(eq(USER_ID), eq(STORY_ID), eq(FILENAME));
    }

    // ── Validation ────────────────────────────────────────────────────────────

    @Test
    @DisplayName("upload() — throws IllegalArgumentException when userContext is null")
    void upload_throwsIllegalArgument_whenUserContextIsNull() {
        assertThatThrownBy(() -> fileService.upload(null, STORY_ID, FILENAME, CONTENT_TYPE, CONTENT))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("userContext");
    }

    @Test
    @DisplayName("upload() — throws IllegalArgumentException when storyId is blank")
    void upload_throwsIllegalArgument_whenStoryIdIsBlank() {
        UserContext user = new UserContext(USER_ID, EMAIL, TOKEN);
        assertThatThrownBy(() -> fileService.upload(user, "   ", FILENAME, CONTENT_TYPE, CONTENT))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("storyId");
    }

    @Test
    @DisplayName("upload() — throws IllegalArgumentException when content is empty")
    void upload_throwsIllegalArgument_whenContentIsEmpty() {
        UserContext user = new UserContext(USER_ID, EMAIL, TOKEN);
        assertThatThrownBy(() -> fileService.upload(user, STORY_ID, FILENAME, CONTENT_TYPE, new byte[0]))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("content");
    }

    @Test
    @DisplayName("upload() — throws IllegalArgumentException when filename is blank")
    void upload_throwsIllegalArgument_whenFilenameIsBlank() {
        UserContext user = new UserContext(USER_ID, EMAIL, TOKEN);
        assertThatThrownBy(() -> fileService.upload(user, STORY_ID, "   ", CONTENT_TYPE, CONTENT))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("filename");
    }

    @Test
    @DisplayName("upload() — throws IllegalArgumentException when contentType is blank")
    void upload_throwsIllegalArgument_whenContentTypeIsBlank() {
        UserContext user = new UserContext(USER_ID, EMAIL, TOKEN);
        assertThatThrownBy(() -> fileService.upload(user, STORY_ID, FILENAME, "", CONTENT))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("contentType");
    }

    // ── Error propagation ─────────────────────────────────────────────────────

    @Test
    @DisplayName("upload() — propagates StorageException from StoragePort")
    void upload_propagatesStorageException_fromStoragePort() throws StorageException {
        when(keyGenerator.generateKey(USER_ID, STORY_ID, FILENAME)).thenReturn(GENERATED_KEY);
        when(storagePort.store(any(), any(), any(), any()))
                .thenThrow(new StorageException("S3 connection refused"));

        UserContext user = new UserContext(USER_ID, EMAIL, TOKEN);
        assertThatThrownBy(() -> fileService.upload(user, STORY_ID, FILENAME, CONTENT_TYPE, CONTENT))
                .isInstanceOf(StorageException.class)
                .hasMessageContaining("S3 connection refused");
    }

    // ── Constructor guards ────────────────────────────────────────────────────

    @Test
    @DisplayName("constructor — throws NullPointerException when storagePort is null")
    void constructor_throwsNPE_whenStoragePortIsNull() {
        assertThatThrownBy(() -> new FileServiceImpl(null, keyGenerator, BUCKET))
                .isInstanceOf(NullPointerException.class);
    }

    @Test
    @DisplayName("constructor — throws NullPointerException when keyGenerator is null")
    void constructor_throwsNPE_whenKeyGeneratorIsNull() {
        assertThatThrownBy(() -> new FileServiceImpl(storagePort, null, BUCKET))
                .isInstanceOf(NullPointerException.class);
    }

    @Test
    @DisplayName("constructor — throws IllegalArgumentException when bucket is blank")
    void constructor_throwsIllegalArgument_whenBucketIsBlank() {
        assertThatThrownBy(() -> new FileServiceImpl(storagePort, keyGenerator, ""))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("bucket");
    }
}

