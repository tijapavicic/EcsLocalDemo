package com.example.application.observability;

import com.example.core.domain.StorageException;
import com.example.core.domain.StorageObject;
import com.example.core.ports.StoragePort;
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
 * Unit tests for {@link ObservableStoragePort} — the decorator pattern.
 *
 * <p><strong>Tests verify:</strong></p>
 * <ul>
 *   <li>Decorator delegates to wrapped implementation</li>
 *   <li>Decorator returns result unchanged</li>
 *   <li>Decorator propagates exceptions from wrapped implementation</li>
 *   <li>Decorator is transparent — caller sees no difference</li>
 * </ul>
 */
@DisplayName("ObservableStoragePort — decorator pattern (observability)")
class ObservableStoragePortTest {

    private static final String BUCKET = "test-bucket";
    private static final String KEY = "users/alice/story-123/2024-01-15/abc123-file.pdf";
    private static final String CONTENT_TYPE = "application/pdf";
    private static final String USER_ID = "alice";
    private static final byte[] CONTENT = new byte[1024];

    private StoragePort delegate;
    private ObservableStoragePort observable;

    @BeforeEach
    void setUp() {
        delegate = mock(StoragePort.class);
        observable = new ObservableStoragePort(delegate);
    }

    // ── Happy path: delegation ───────────────────────────────────────────────

    @Test
    @DisplayName("store() — delegates to wrapped implementation")
    void store_delegatesToWrappedImplementation() throws StorageException {
        StorageObject expected = new StorageObject(KEY, BUCKET, CONTENT_TYPE, (long) CONTENT.length, Instant.now(), USER_ID);
        when(delegate.store(eq(BUCKET), eq(KEY), eq(CONTENT_TYPE), eq(CONTENT), eq(USER_ID)))
                .thenReturn(expected);

        StorageObject result = observable.store(BUCKET, KEY, CONTENT_TYPE, CONTENT, USER_ID);

        assertThat(result).isEqualTo(expected);
        verify(delegate).store(eq(BUCKET), eq(KEY), eq(CONTENT_TYPE), eq(CONTENT), eq(USER_ID));
    }

    @Test
    @DisplayName("store() — returns result unchanged from delegate")
    void store_returnsResultUnchanged() throws StorageException {
        StorageObject expected = new StorageObject(KEY, BUCKET, "text/plain", 2048L, Instant.now(), "bob");
        when(delegate.store(any(), any(), any(), any(), any())).thenReturn(expected);

        StorageObject result = observable.store(BUCKET, KEY, "text/plain", new byte[2048], "bob");

        assertThat(result).isExactlyInstanceOf(StorageObject.class);
        assertThat(result.getKey()).isEqualTo(KEY);
        assertThat(result.getUserId()).isEqualTo("bob");
        assertThat(result.getSizeBytes()).isEqualTo(2048L);
    }

    // ── Error handling: propagation ────────────────────────────────────────────

    @Test
    @DisplayName("store() — propagates StorageException from delegate")
    void store_propagatesStorageException_fromDelegate() throws StorageException {
        when(delegate.store(any(), any(), any(), any(), any()))
                .thenThrow(new StorageException("S3 connection refused"));

        assertThatThrownBy(() -> observable.store(BUCKET, KEY, CONTENT_TYPE, CONTENT, USER_ID))
                .isInstanceOf(StorageException.class)
                .hasMessageContaining("S3 connection refused");

        verify(delegate).store(any(), any(), any(), any(), any());
    }

    @Test
    @DisplayName("store() — propagates IllegalArgumentException from delegate")
    void store_propagatesIllegalArgumentException_fromDelegate() throws StorageException {
        when(delegate.store(any(), any(), any(), any(), any()))
                .thenThrow(new IllegalArgumentException("userId must not be blank"));

        assertThatThrownBy(() -> observable.store(BUCKET, KEY, CONTENT_TYPE, CONTENT, ""))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("userId");

        verify(delegate).store(any(), any(), any(), any(), any());
    }

    // ── Transparency: decorator is invisible to caller ──────────────────────

    @Test
    @DisplayName("store() — transparent: caller observes no difference when wrapped vs direct")
    void store_transparent_whenWrappedVsDirectCall() throws StorageException {
        StorageObject expected = new StorageObject(KEY, BUCKET, CONTENT_TYPE, (long) CONTENT.length, Instant.now(), USER_ID);
        when(delegate.store(eq(BUCKET), eq(KEY), eq(CONTENT_TYPE), eq(CONTENT), eq(USER_ID)))
                .thenReturn(expected);

        // Call via decorator
        StorageObject resultViaDecorator = observable.store(BUCKET, KEY, CONTENT_TYPE, CONTENT, USER_ID);

        // Would be identical if called directly (same return value, exceptions)
        assertThat(resultViaDecorator).isEqualTo(expected);
    }
}

