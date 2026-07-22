package com.example.core.ports;

/**
 * Generic processor for file-like objects with configurable rules.
 *
 * <p><b>Generics Benefit:</b> Single interface processes any file-like type
 * with type-safe configuration
 *
 * <p><b>Type Parameters:</b>
 * <ul>
 *   <li>T = the file-like object type (MultipartFile, InputStream, byte[], FileMetadata)</li>
 *   <li>R = the result type (StorageObject, ProcessingResult, etc.)</li>
 *   <li>C = the configuration type (ValidationConfig, ProcessingConfig, etc.)</li>
 * </ul>
 *
 * <p><b>Example Usage:</b>
 * <pre>
 * // Image processor
 * class ImageProcessor implements FileProcessor&lt;MultipartFile, ImageMetadata, ImageProcessingConfig&gt; {
 *   public ImageMetadata process(MultipartFile file, ImageProcessingConfig config) { ... }
 * }
 *
 * // Document processor
 * class DocumentProcessor implements FileProcessor&lt;MultipartFile, DocumentMetadata, DocumentConfig&gt; {
 *   public DocumentMetadata process(MultipartFile file, DocumentConfig config) { ... }
 * }
 * </pre>
 *
 * @param <T> the input file-like type
 * @param <R> the result/output type
 * @param <C> the configuration type
 * @since 3.0
 */
public interface FileProcessor<T, R, C> {

    /**
     * Process a file according to configuration.
     *
     * @param file the file to process
     * @param config the processing configuration
     * @return processing result
     * @throws IllegalArgumentException if validation fails
     */
    R process(T file, C config);

    /**
     * Validate a file against configuration.
     *
     * @param file the file to validate
     * @param config the validation configuration
     * @throws IllegalArgumentException if validation fails
     */
    void validate(T file, C config);
}

