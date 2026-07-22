package com.example.tests.unit;

import com.example.adapters.inbound.rest.FileUploadValidator;
import com.example.adapters.inbound.rest.config.ValidationConfiguration;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockMultipartFile;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.api.Assertions.assertThatNoException;

/**
 * Unit tests for {@link FileUploadValidator} with configurable validation rules.
 *
 * <p><strong>Tests:</strong></p>
 * <ul>
 *   <li>File size validation against configured limit</li>
 *   <li>Content type validation against allowed patterns</li>
 *   <li>Empty/null file validation</li>
 *   <li>Wildcard content type matching (e.g., "image/*")</li>
 * </ul>
 */
@DisplayName("FileUploadValidator — configuration-driven validation")
class FileUploadValidatorConfigTest {

    private ValidationConfiguration config;
    private FileUploadValidator validator;

    @BeforeEach
    void setUp() {
        config = new ValidationConfiguration();
        // Use default values: 10MB, no content type restrictions
    }

    // ── File size validation ──────────────────────────────────────────────────

    @Test
    @DisplayName("validate() — accepts file within configured size limit")
    void validate_acceptsFile_whenWithinSizeLimit() {
        config.setMaxFileSizeBytes(1024);  // 1 KB limit
        validator = new FileUploadValidator(config);

        MockMultipartFile file = new MockMultipartFile(
                "file", "test.txt", "text/plain", new byte[512]);  // 512 bytes

        assertThatNoException().isThrownBy(() -> validator.validate(file));
    }

    @Test
    @DisplayName("validate() — throws IllegalArgumentException when size exceeds configured limit")
    void validate_throwsIllegalArgument_whenSizeExceedsConfiguredLimit() {
        config.setMaxFileSizeBytes(1024);  // 1 KB limit
        validator = new FileUploadValidator(config);

        MockMultipartFile file = new MockMultipartFile(
                "file", "test.txt", "text/plain", new byte[2048]);  // 2 KB

        assertThatThrownBy(() -> validator.validate(file))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("exceeds maximum")
                .hasMessageContaining("2048")
                .hasMessageContaining("1024");
    }

    @Test
    @DisplayName("validate() — uses default 10MB limit when not configured")
    void validate_usesDefaultLimit_whenNotConfigured() {
        validator = new FileUploadValidator(config);  // Uses default 10MB

        // 5MB file should pass
        MockMultipartFile file = new MockMultipartFile(
                "file", "test.txt", "text/plain", new byte[5 * 1024 * 1024]);

        assertThatNoException().isThrownBy(() -> validator.validate(file));
    }

    // ── Content type validation ───────────────────────────────────────────────

    @Test
    @DisplayName("validate() — accepts any content type when allowedContentTypes is empty")
    void validate_acceptsAnyContentType_whenAllowedListIsEmpty() {
        config.setAllowedContentTypes(List.of());  // Empty = all allowed
        validator = new FileUploadValidator(config);

        MockMultipartFile file = new MockMultipartFile(
                "file", "test.bin", "application/octet-stream", new byte[100]);

        assertThatNoException().isThrownBy(() -> validator.validate(file));
    }

    @Test
    @DisplayName("validate() — accepts exact content type match")
    void validate_acceptsFile_whenContentTypeMatchesExactly() {
        config.setAllowedContentTypes(List.of("application/pdf", "text/plain"));
        validator = new FileUploadValidator(config);

        MockMultipartFile file = new MockMultipartFile(
                "file", "test.pdf", "application/pdf", new byte[100]);

        assertThatNoException().isThrownBy(() -> validator.validate(file));
    }

    @Test
    @DisplayName("validate() — throws IllegalArgumentException when content type not allowed")
    void validate_throwsIllegalArgument_whenContentTypeNotAllowed() {
        config.setAllowedContentTypes(List.of("application/pdf", "text/plain"));
        validator = new FileUploadValidator(config);

        MockMultipartFile file = new MockMultipartFile(
                "file", "test.jpg", "image/jpeg", new byte[100]);

        assertThatThrownBy(() -> validator.validate(file))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("content type")
                .hasMessageContaining("not allowed");
    }

    @Test
    @DisplayName("validate() — supports wildcard content type patterns (image/*)")
    void validate_supportsWildcardPatterns_forContentTypes() {
        config.setAllowedContentTypes(List.of("image/*", "application/pdf"));
        validator = new FileUploadValidator(config);

        // Should accept image/jpeg
        MockMultipartFile jpeg = new MockMultipartFile(
                "file", "photo.jpg", "image/jpeg", new byte[100]);
        assertThatNoException().isThrownBy(() -> validator.validate(jpeg));

        // Should accept image/png
        MockMultipartFile png = new MockMultipartFile(
                "file", "photo.png", "image/png", new byte[100]);
        assertThatNoException().isThrownBy(() -> validator.validate(png));

        // Should reject text/plain
        MockMultipartFile txt = new MockMultipartFile(
                "file", "doc.txt", "text/plain", new byte[100]);
        assertThatThrownBy(() -> validator.validate(txt))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    @DisplayName("validate() — throws IllegalArgumentException when content type is null and restrictions apply")
    void validate_throwsIllegalArgument_whenContentTypeIsNullAndRestrictionsApply() {
        config.setAllowedContentTypes(List.of("application/pdf"));
        validator = new FileUploadValidator(config);

        MockMultipartFile file = new MockMultipartFile(
                "file", "test.bin", null, new byte[100]);  // null content type

        assertThatThrownBy(() -> validator.validate(file))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("content type");
    }

    // ── Basic validation (unchanged) ──────────────────────────────────────────

    @Test
    @DisplayName("validate() — throws IllegalArgumentException when file is null")
    void validate_throwsIllegalArgument_whenFileIsNull() {
        validator = new FileUploadValidator(config);

        assertThatThrownBy(() -> validator.validate(null))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("required");
    }

    @Test
    @DisplayName("validate() — throws IllegalArgumentException when file is empty")
    void validate_throwsIllegalArgument_whenFileIsEmpty() {
        validator = new FileUploadValidator(config);

        MockMultipartFile emptyFile = new MockMultipartFile(
                "file", "test.txt", "text/plain", new byte[0]);

        assertThatThrownBy(() -> validator.validate(emptyFile))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("required")
                .hasMessageContaining("not be empty");
    }
}

