package com.example.tests.unit;

import com.example.adapters.inbound.rest.FileUploadValidator;
import com.example.adapters.inbound.rest.config.ValidationConfiguration;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.web.multipart.MultipartFile;

import java.util.List;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;

/**
 * Unit tests for FileUploadValidator with extension validation support.
 *
 * <p><b>Refactoring #4 & Phase 3 Extension Filtering:</b> Tests verify that
 * validation rules are configurable via YAML without code changes, including
 * file extension restrictions.
 *
 * <p><b>Key Features:</b>
 * <ul>
 *   <li>File size validation (configurable max bytes)</li>
 *   <li>Content type validation (wildcard pattern matching)</li>
 *   <li>File extension validation (case-insensitive, dot-prefix optional)</li>
 * </ul>
 */
@DisplayName("FileUploadValidator — Extension Validation Tests")
class FileUploadValidatorTest {

    private ValidationConfiguration config;
    private FileUploadValidator validator;

    @BeforeEach
    void setUp() {
        config = new ValidationConfiguration();
        validator = new FileUploadValidator(config);
    }

    // ==================== File Size Validation ====================

    @Test
    @DisplayName("validate() — passes when file size within limit")
    void validate_passes_whenFileSizeWithinLimit() {
        config.setMaxFileSizeBytes(1024);  // 1 KB
        MultipartFile file = mockFile("test.txt", 512, "text/plain");

        assertThatNoException().isThrownBy(() -> validator.validate(file));
    }

    @Test
    @DisplayName("validate() — throws when file size exceeds limit")
    void validate_throws_whenFileSizeExceedsLimit() {
        config.setMaxFileSizeBytes(1024);  // 1 KB
        MultipartFile file = mockFile("test.txt", 2048, "text/plain");

        assertThatThrownBy(() -> validator.validate(file))
            .isInstanceOf(IllegalArgumentException.class)
            .hasMessageContaining("exceeds maximum allowed size");
    }

    // ==================== Extension Validation ====================

    @Test
    @DisplayName("validate() — passes when extension in allowed list")
    void validate_passes_whenExtensionInAllowedList() {
        config.setAllowedExtensions(List.of("pdf", "txt", "doc"));
        MultipartFile file = mockFile("document.pdf", 100, "application/pdf");

        assertThatNoException().isThrownBy(() -> validator.validate(file));
    }

    @Test
    @DisplayName("validate() — passes when extension matches (case-insensitive)")
    void validate_passes_whenExtensionMatchesCaseInsensitive() {
        config.setAllowedExtensions(List.of("PDF", "TXT"));
        MultipartFile file = mockFile("document.pdf", 100, "application/pdf");

        assertThatNoException().isThrownBy(() -> validator.validate(file));
    }

    @Test
    @DisplayName("validate() — passes when extension with dot prefix in allowed list")
    void validate_passes_whenExtensionWithDotPrefix() {
        config.setAllowedExtensions(List.of(".pdf", ".txt"));
        MultipartFile file = mockFile("document.pdf", 100, "application/pdf");

        assertThatNoException().isThrownBy(() -> validator.validate(file));
    }

    @Test
    @DisplayName("validate() — throws when extension not in allowed list")
    void validate_throws_whenExtensionNotInAllowedList() {
        config.setAllowedExtensions(List.of("pdf", "txt"));
        MultipartFile file = mockFile("script.exe", 100, "application/octet-stream");

        assertThatThrownBy(() -> validator.validate(file))
            .isInstanceOf(IllegalArgumentException.class)
            .hasMessageContaining("file extension 'exe' not allowed");
    }

    @Test
    @DisplayName("validate() — passes when no extension restrictions configured")
    void validate_passes_whenNoExtensionRestrictions() {
        config.setAllowedExtensions(List.of());  // Empty = all allowed
        MultipartFile file = mockFile("anything.xyz", 100, "application/octet-stream");

        assertThatNoException().isThrownBy(() -> validator.validate(file));
    }

    @Test
    @DisplayName("validate() — throws when filename is missing with extension restrictions")
    void validate_throws_whenFilenameMissingWithExtensionRestrictions() {
        config.setAllowedExtensions(List.of("pdf"));
        MultipartFile file = mockFileWithoutFilename(100, "application/pdf");

        assertThatThrownBy(() -> validator.validate(file))
            .isInstanceOf(IllegalArgumentException.class)
            .hasMessageContaining("filename is required");
    }

    @Test
    @DisplayName("validate() — throws when file has no extension with restrictions")
    void validate_throws_whenFileHasNoExtension() {
        config.setAllowedExtensions(List.of("pdf", "txt"));
        MultipartFile file = mockFile("README", 100, "text/plain");

        assertThatThrownBy(() -> validator.validate(file))
            .isInstanceOf(IllegalArgumentException.class)
            .hasMessageContaining("file extension '' not allowed");
    }

    // ==================== Content Type Validation ====================

    @Test
    @DisplayName("validate() — passes when content type matches allowed pattern")
    void validate_passes_whenContentTypeMatchesPattern() {
        config.setAllowedContentTypes(List.of("image/*", "application/pdf"));
        MultipartFile file = mockFile("photo.jpg", 100, "image/jpeg");

        assertThatNoException().isThrownBy(() -> validator.validate(file));
    }

    @Test
    @DisplayName("validate() — throws when content type not in allowed list")
    void validate_throws_whenContentTypeNotAllowed() {
        config.setAllowedContentTypes(List.of("image/*"));
        MultipartFile file = mockFile("document.pdf", 100, "application/pdf");

        assertThatThrownBy(() -> validator.validate(file))
            .isInstanceOf(IllegalArgumentException.class)
            .hasMessageContaining("content type 'application/pdf' not allowed");
    }

    // ==================== Combined Validation (Size + Type + Extension) ====================

    @Test
    @DisplayName("validate() — passes with all restrictions when all conditions met")
    void validate_passes_withAllRestrictions() {
        config.setMaxFileSizeBytes(1024);
        config.setAllowedContentTypes(List.of("image/*", "application/pdf"));
        config.setAllowedExtensions(List.of("pdf", "jpg", "png"));

        MultipartFile file = mockFile("document.pdf", 512, "application/pdf");

        assertThatNoException().isThrownBy(() -> validator.validate(file));
    }

    @Test
    @DisplayName("validate() — throws on size violation with other restrictions configured")
    void validate_throws_onSizeViolation_withOtherRestrictions() {
        config.setMaxFileSizeBytes(100);  // 100 bytes
        config.setAllowedContentTypes(List.of("application/pdf"));
        config.setAllowedExtensions(List.of("pdf"));

        MultipartFile file = mockFile("document.pdf", 200, "application/pdf");

        assertThatThrownBy(() -> validator.validate(file))
            .isInstanceOf(IllegalArgumentException.class)
            .hasMessageContaining("exceeds maximum");
    }

    @Test
    @DisplayName("validate() — throws on extension violation with other restrictions configured")
    void validate_throws_onExtensionViolation_withOtherRestrictions() {
        config.setMaxFileSizeBytes(1024);
        config.setAllowedContentTypes(List.of("application/octet-stream"));
        config.setAllowedExtensions(List.of("pdf"));

        MultipartFile file = mockFile("script.exe", 512, "application/octet-stream");

        assertThatThrownBy(() -> validator.validate(file))
            .isInstanceOf(IllegalArgumentException.class)
            .hasMessageContaining("file extension 'exe' not allowed");
    }

    // ==================== Edge Cases ====================

    @Test
    @DisplayName("validate() — throws when file is null")
    void validate_throws_whenFileIsNull() {
        assertThatThrownBy(() -> validator.validate(null))
            .isInstanceOf(IllegalArgumentException.class)
            .hasMessageContaining("file is required");
    }

    @Test
    @DisplayName("validate() — throws when file is empty")
    void validate_throws_whenFileIsEmpty() {
        MultipartFile file = mockFile("empty.txt", 0, "text/plain");
        when(file.isEmpty()).thenReturn(true);

        assertThatThrownBy(() -> validator.validate(file))
            .isInstanceOf(IllegalArgumentException.class)
            .hasMessageContaining("file is required");
    }

    @Test
    @DisplayName("validate() — handles filename with multiple dots")
    void validate_handlesFilenameWithMultipleDots() {
        config.setAllowedExtensions(List.of("gz"));
        MultipartFile file = mockFile("archive.tar.gz", 100, "application/gzip");

        // Should extract 'gz' as extension (not 'tar.gz')
        assertThatNoException().isThrownBy(() -> validator.validate(file));
    }

    @Test
    @DisplayName("validate() — handles extension normalization (mixed case)")
    void validate_handlesExtensionNormalization() {
        config.setAllowedExtensions(List.of("PDF"));  // Uppercase in config
        MultipartFile file = mockFile("document.pdf", 100, "application/pdf");  // Lowercase in filename

        assertThatNoException().isThrownBy(() -> validator.validate(file));
    }

    // ==================== Helper Methods ====================

    private MultipartFile mockFile(String filename, long size, String contentType) {
        MultipartFile file = mock(MultipartFile.class);
        when(file.getOriginalFilename()).thenReturn(filename);
        when(file.getSize()).thenReturn(size);
        when(file.getContentType()).thenReturn(contentType);
        when(file.isEmpty()).thenReturn(false);
        return file;
    }

    private MultipartFile mockFileWithoutFilename(long size, String contentType) {
        MultipartFile file = mock(MultipartFile.class);
        when(file.getOriginalFilename()).thenReturn(null);
        when(file.getSize()).thenReturn(size);
        when(file.getContentType()).thenReturn(contentType);
        when(file.isEmpty()).thenReturn(false);
        return file;
    }
}

