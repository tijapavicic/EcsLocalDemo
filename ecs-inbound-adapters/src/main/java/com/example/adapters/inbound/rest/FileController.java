package com.example.adapters.inbound.rest;

import com.example.core.domain.StorageException;
import com.example.core.domain.StorageObject;
import com.example.core.ports.FileServicePort;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.util.Map;

/**
 * <strong>Inbound adapter</strong> — single REST controller for S3 file uploads.
 *
 * <p>Depends ONLY on {@link FileServicePort} (the inbound port interface).
 * Contains ZERO business logic — it translates HTTP into domain calls and back.</p>
 *
 * <p>Endpoint: {@code POST /api/files/upload} (multipart/form-data, field name: {@code file})</p>
 */
@RestController
@RequestMapping("/api/files")
public class FileController {

    private static final Logger log = LoggerFactory.getLogger(FileController.class);

    /** Inbound port — interface, never a concrete adapter. */
    private final FileServicePort fileService;

    public FileController(FileServicePort fileService) {
        this.fileService = fileService;
    }

    /**
     * Upload a file to S3 / MinIO.
     *
     * <pre>
     * curl -X POST http://localhost:8080/api/files/upload \
     *      -F "file=@/path/to/your/file.pdf"
     * </pre>
     *
     * @param file multipart file from the request body
     * @return {@code 201 Created} with {@link StorageObject} JSON body
     */
    @PostMapping(value = "/upload", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ResponseEntity<StorageObject> upload(@RequestParam("file") MultipartFile file) throws IOException, StorageException {

        log.info("Upload request: name={} size={} contentType={}",
                file.getOriginalFilename(), file.getSize(), file.getContentType());

        if (file.isEmpty()) {
            log.warn("Rejected upload — file is empty");
            return ResponseEntity.badRequest().build();
        }

        StorageObject stored = fileService.upload(
                file.getOriginalFilename(),
                file.getContentType() != null ? file.getContentType() : MediaType.APPLICATION_OCTET_STREAM_VALUE,
                file.getBytes()
        );

        log.info("Upload success: key={} bucket={}", stored.getKey(), stored.getBucket());
        return ResponseEntity.status(HttpStatus.CREATED).body(stored);
    }

    // ──────────────────────────────────────────────────────────────────────────
    // Exception handlers — translate domain exceptions to HTTP responses
    // ──────────────────────────────────────────────────────────────────────────

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

