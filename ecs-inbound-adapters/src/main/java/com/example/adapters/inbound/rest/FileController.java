package com.example.adapters.inbound.rest;

import com.example.adapters.inbound.security.AuthorizationExtractor;
import com.example.core.domain.StorageException;
import com.example.core.domain.StorageObject;
import com.example.core.domain.TokenValidationException;
import com.example.core.domain.UserContext;
import com.example.core.ports.FileServicePort;
import com.example.core.ports.TokenExtractorPort;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.util.Map;

/**
 * <strong>Inbound adapter</strong> — single REST controller for S3 file uploads with IAM support.
 *
 * <p>Depends ONLY on {@link FileServicePort} and {@link TokenExtractorPort} (inbound port interfaces).
 * Contains ZERO business logic — it translates HTTP into domain calls and back.</p>
 *
 * <p>Endpoint: {@code POST /api/files/upload} (multipart/form-data, field name: {@code file})</p>
 * <p>Authorization: Bearer token in Authorization header</p>
 */
@RestController
@RequestMapping("/api/files")
public class FileController {

    private static final Logger log = LoggerFactory.getLogger(FileController.class);

    /** Inbound port — interface, never a concrete adapter. */
    private final FileServicePort fileService;

    /** Token extraction port — interface for Bearer token validation. */
    private final TokenExtractorPort tokenExtractor;

    public FileController(FileServicePort fileService, TokenExtractorPort tokenExtractor) {
        this.fileService = fileService;
        this.tokenExtractor = tokenExtractor;
    }

    /**
     * Upload a file to S3 / MinIO with user identity.
     *
     * <pre>
     * curl -X POST http://localhost:8080/api/files/upload \
     *      -H "Authorization: Bearer dXNlcjEyMzp1c2VyQGV4YW1wbGUuY29t" \
     *      -F "file=@/path/to/your/file.pdf"
     * </pre>
     *
     * @param authHeader Authorization header (Bearer token)
     * @param file multipart file from the request body
     * @return {@code 201 Created} with {@link StorageObject} JSON body
     */
    @PostMapping(value = "/upload", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ResponseEntity<StorageObject> upload(
            @RequestHeader(value = "Authorization") String authHeader,
            @RequestParam("file") MultipartFile file) throws IOException, StorageException {

        try {
            // Extract Bearer token from Authorization header
            String token = AuthorizationExtractor.extractBearerToken(authHeader);
            log.debug("Extracted Bearer token, length={}", token.length());

            // Extract user context from token
            UserContext userContext = tokenExtractor.extractUserContext(token);
            log.info("Upload request: userId={} filename={} size={} contentType={}",
                    userContext.getUserId(), file.getOriginalFilename(), file.getSize(), file.getContentType());

            if (file.isEmpty()) {
                log.warn("Rejected upload — file is empty");
                return ResponseEntity.badRequest().build();
            }

            // Upload with user context
            StorageObject stored = fileService.upload(
                    userContext,
                    file.getOriginalFilename(),
                    file.getContentType() != null ? file.getContentType() : MediaType.APPLICATION_OCTET_STREAM_VALUE,
                    file.getBytes()
            );

            log.info("Upload success: userId={} key={} bucket={}",
                    userContext.getUserId(), stored.getKey(), stored.getBucket());
            return ResponseEntity.status(HttpStatus.CREATED).body(stored);

        } catch (TokenValidationException ex) {
            log.warn("Token validation failed: {}", ex.getMessage());
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).build();
        }
    }

    // ──────────────────────────────────────────────────────────────────────────
    // Exception handlers — translate domain exceptions to HTTP responses
    // ──────────────────────────────────────────────────────────────────────────

    @ExceptionHandler(TokenValidationException.class)
    public ResponseEntity<Map<String, String>> handleTokenValidation(TokenValidationException ex) {
        log.warn("Token validation error: {}", ex.getMessage());
        return ResponseEntity.status(HttpStatus.UNAUTHORIZED)
                .body(Map.of("error", "Invalid or expired token", "detail", ex.getMessage()));
    }

    @ExceptionHandler(StorageException.class)
    public ResponseEntity<Map<String, String>> handleStorageException(StorageException ex) {
        log.error("Storage failure: {}", ex.getMessage(), ex);
        return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                .body(Map.of("error", "Storage operation failed", "detail", ex.getMessage()));
    }

    @ExceptionHandler(IllegalArgumentException.class)
    public ResponseEntity<Map<String, String>> handleIllegalArgument(IllegalArgumentException ex) {
        log.warn("Validation failure: {}", ex.getMessage());
        return ResponseEntity.badRequest()
                .body(Map.of("error", "Invalid request", "detail", ex.getMessage()));
    }
}

