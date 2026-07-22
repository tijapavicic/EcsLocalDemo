package com.example.core.ports;

/**
 * <strong>Outbound port</strong> — abstraction for content type resolution strategy.
 *
 * <p><strong>Open/Closed Principle:</strong> New resolution strategies (file extension mapping,
 * magic bytes detection, Apache Tika integration) can be added by implementing this port
 * without modifying {@link com.example.core.usecases.FileServiceImpl}.</p>
 *
 * <p>No Spring dependencies. Pure Java interface.</p>
 */
public interface ContentTypeResolverPort {

    /**
     * Resolve content type from filename and declared type.
     *
     * <p>Resolution order:</p>
     * <ol>
     *   <li>Use declared type if present and non-blank</li>
     *   <li>Infer from filename (future enhancement)</li>
     *   <li>Return default content type (application/octet-stream)</li>
     * </ol>
     *
     * @param filename     original filename (e.g., "report.pdf")
     * @param declaredType content type from HTTP request (may be null)
     * @return resolved MIME type (never null)
     */
    String resolve(String filename, String declaredType);

    /**
     * Default fallback when content type cannot be determined.
     */
    String DEFAULT_CONTENT_TYPE = "application/octet-stream";
}

