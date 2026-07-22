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
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

import jakarta.validation.constraints.NotNull;
import java.io.IOException;

/**
 * <strong>Inbound adapter</strong> — REST controller for S3 file uploads with IAM support.
 *
 * <p><strong>Single Responsibility Principle:</strong> Focuses exclusively on HTTP concerns:
 * routing, request/response mapping, and status codes. Business logic delegated to
 * {@link FileServicePort}. Exception handling delegated to {@link RestExceptionHandler}.</p>
 *
 * <p><strong>Dependencies:</strong></p>
 * <ul>
 *   <li>{@link FileServicePort} — inbound port (use case interface)</li>
 *   <li>{@link TokenExtractorPort} — inbound port (token validation)</li>
 *   <li>{@link FileUploadValidator} — input validation strategy</li>
 * </ul>
 *
 * <p><strong>Endpoint:</strong> {@code POST /api/files/upload} (multipart/form-data)</p>
 *
 * @see RestExceptionHandler for centralized error handling
 */
@RestController
@RequestMapping("/api/files")
public class FileController {

    private static final Logger log = LoggerFactory.getLogger(FileController.class);

    private final FileServicePort fileService;
    private final TokenExtractorPort tokenExtractor;
    private final FileUploadValidator fileValidator;

    /**
     * Construct with required port dependencies.
     *
     * @param fileService    inbound port for file upload use case
     * @param tokenExtractor inbound port for Bearer token extraction
     * @param fileValidator  input validation strategy
     */
    public FileController(
            FileServicePort fileService,
            TokenExtractorPort tokenExtractor,
            FileUploadValidator fileValidator) {
        this.fileService = fileService;
        this.tokenExtractor = tokenExtractor;
        this.fileValidator = fileValidator;
    }

    /**
     * Upload a file to S3 / MinIO with user identity and story context.
     *
     * <p><strong>Request:</strong> Multipart form with {@code storyId} and {@code file} fields, plus Bearer token header</p>
     * <p><strong>Response:</strong> {@code 201 Created} with {@link StorageObject} JSON body</p>
     *
     * <p><strong>Example cURL:</strong></p>
     * <pre>
     * curl -X POST http://localhost:8080/api/files/upload \
     *      -H "Authorization: Bearer dXNlcjEyMzp1c2VyQGV4YW1wbGUuY29t" \
     *      -F "storyId=story-123" \
     *      -F "file=@/path/to/your/file.pdf"
     * </pre>
     *
     * <p><strong>Flow:</strong></p>
     * <ol>
     *   <li>Validate multipart file and storyId (via {@link FileUploadValidator})</li>
     *   <li>Extract Bearer token from Authorization header</li>
     *   <li>Extract user context from token (via {@link TokenExtractorPort})</li>
     *   <li>Upload file with user context and story context (via {@link FileServicePort})</li>
     *   <li>Return {@code 201 Created} with metadata</li>
     * </ol>
     *
     * @param authHeader Authorization header containing Bearer token
     * @param storyId    story context ID for organizing files
     * @param file       uploaded file (multipart form data, field name: "file")
     * @return {@code 201 Created} with {@link StorageObject} JSON body
     * @throws IOException      if reading multipart file bytes fails
     * @throws StorageException if cloud storage operation fails (handled by {@link RestExceptionHandler})
     * @throws TokenValidationException if token validation fails (handled by {@link RestExceptionHandler})
     * @throws IllegalArgumentException if validation fails (handled by {@link RestExceptionHandler})
     */
    @PostMapping(value = "/upload", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ResponseEntity<StorageObject> upload(
            @RequestHeader(value = "Authorization") String authHeader,
            @RequestParam("storyId") @NotNull(message = "storyId is required") String storyId,
            @RequestParam("file") @NotNull(message = "file is required") MultipartFile file)
            throws IOException, StorageException, TokenValidationException {

        // ─ Step 1: Validate file and storyId ─
        fileValidator.validate(file);
        if (storyId.isBlank()) {
            throw new IllegalArgumentException("storyId must not be blank");
        }

        // ─ Step 2: Extract token ─
        String token = AuthorizationExtractor.extractBearerToken(authHeader);
        log.debug("Extracted Bearer token, length={}", token.length());

        // ─ Step 3: Extract user context ─
        UserContext userContext = tokenExtractor.extractUserContext(token);
        log.info("Upload request: userId={} storyId={} filename={} size={} contentType={}",
                userContext.getUserId(), storyId, file.getOriginalFilename(), file.getSize(), file.getContentType());

        // ─ Step 4: Upload file ─
        StorageObject stored = fileService.upload(
                userContext,
                storyId,
                file.getOriginalFilename(),
                file.getContentType(),  // ← Pass declared content type; strategy will resolve if null
                file.getBytes()
        );

        // ─ Step 5: Return response ─
        log.info("Upload success: userId={} storyId={} key={} bucket={}",
                userContext.getUserId(), storyId, stored.getKey(), stored.getBucket());
        return ResponseEntity.status(HttpStatus.CREATED).body(stored);
    }
}
