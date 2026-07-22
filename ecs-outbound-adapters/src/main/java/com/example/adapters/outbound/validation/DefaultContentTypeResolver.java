package com.example.adapters.outbound.validation;

import com.example.core.ports.ContentTypeResolverPort;
import org.springframework.stereotype.Component;

/**
 * <strong>Outbound adapter</strong> — default content type resolution strategy.
 *
 * <p><strong>Open/Closed Principle:</strong> Additional resolution strategies can be added
 * by creating new implementations of {@link ContentTypeResolverPort}. The use case remains
 * unchanged.</p>
 *
 * <p><strong>Resolution Strategy:</strong></p>
 * <ol>
 *   <li>If declared type is provided and non-blank, use it</li>
 *   <li>Future: infer from file extension (e.g., ".pdf" → "application/pdf")</li>
 *   <li>Fallback to default type (application/octet-stream)</li>
 * </ol>
 *
 * <p>Future enhancement: integrate Apache Tika for magic bytes detection.</p>
 */
@Component
public class DefaultContentTypeResolver implements ContentTypeResolverPort {

    @Override
    public String resolve(String filename, String declaredType) {
        // If declared type is provided and non-blank, use it
        if (declaredType != null && !declaredType.isBlank()) {
            return declaredType;
        }

        // Future enhancement: infer from file extension
        // if (filename != null && filename.endsWith(".pdf")) {
        //     return "application/pdf";
        // }
        // if (filename != null && filename.endsWith(".txt")) {
        //     return "text/plain";
        // }

        // Fallback to default
        return DEFAULT_CONTENT_TYPE;
    }
}

