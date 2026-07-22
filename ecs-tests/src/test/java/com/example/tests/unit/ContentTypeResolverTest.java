package com.example.tests.unit;

import com.example.adapters.outbound.validation.DefaultContentTypeResolver;
import com.example.core.ports.ContentTypeResolverPort;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Unit tests for {@link ContentTypeResolverPort} implementations.
 *
 * <p><strong>Tests:</strong></p>
 * <ul>
 *   <li>Returns declared type when provided</li>
 *   <li>Returns default type when declared type is null</li>
 *   <li>Returns default type when declared type is blank</li>
 *   <li>Ignores filename when declared type is provided</li>
 * </ul>
 */
@DisplayName("ContentTypeResolverPort — content type resolution strategy")
class ContentTypeResolverTest {

    private ContentTypeResolverPort resolver;

    @BeforeEach
    void setUp() {
        resolver = new DefaultContentTypeResolver();
    }

    // ── Happy path: declared type provided ──────────────────────────────────

    @Test
    @DisplayName("resolve() — returns declared type when provided")
    void resolve_returnsDeclaredType_whenProvided() {
        String result = resolver.resolve("file.bin", "application/pdf");

        assertThat(result).isEqualTo("application/pdf");
    }

    @Test
    @DisplayName("resolve() — returns declared type even when filename suggests different type")
    void resolve_returnsDeclaredType_evenWhenFilenameDiffersLogically() {
        // Declared type takes precedence
        String result = resolver.resolve("report.pdf", "text/plain");

        assertThat(result).isEqualTo("text/plain");
    }

    // ── Fallback: declared type not provided ───────────────────────────────

    @Test
    @DisplayName("resolve() — returns default when declaredType is null")
    void resolve_returnsDefault_whenDeclaredTypeIsNull() {
        String result = resolver.resolve("file.dat", null);

        assertThat(result).isEqualTo(ContentTypeResolverPort.DEFAULT_CONTENT_TYPE);
    }

    @Test
    @DisplayName("resolve() — returns default when declaredType is blank")
    void resolve_returnsDefault_whenDeclaredTypeIsBlank() {
        String result = resolver.resolve("file.dat", "   ");

        assertThat(result).isEqualTo(ContentTypeResolverPort.DEFAULT_CONTENT_TYPE);
    }

    @Test
    @DisplayName("resolve() — returns default when declaredType is empty string")
    void resolve_returnsDefault_whenDeclaredTypeIsEmpty() {
        String result = resolver.resolve("file.dat", "");

        assertThat(result).isEqualTo(ContentTypeResolverPort.DEFAULT_CONTENT_TYPE);
    }

    // ── Default content type ───────────────────────────────────────────────

    @Test
    @DisplayName("resolve() — default content type is application/octet-stream")
    void resolve_defaultContentType_isApplicationOctetStream() {
        assertThat(ContentTypeResolverPort.DEFAULT_CONTENT_TYPE)
                .isEqualTo("application/octet-stream");
    }

    @Test
    @DisplayName("resolve() — always returns non-null content type")
    void resolve_alwaysReturnsNonNull_contentType() {
        String result1 = resolver.resolve("file.txt", null);
        String result2 = resolver.resolve("file.bin", "");
        String result3 = resolver.resolve("file.pdf", "application/pdf");

        assertThat(result1).isNotNull();
        assertThat(result2).isNotNull();
        assertThat(result3).isNotNull();
    }
}

