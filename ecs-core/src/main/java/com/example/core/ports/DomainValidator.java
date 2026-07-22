package com.example.core.ports;

/**
 * Generic validation port for domain objects.
 *
 * <p><b>Generics Benefit:</b> Single validation interface reusable for any domain type
 * (UserContext, StorageObject, FileMetadata, etc.)
 *
 * <p><b>Open/Closed Principle:</b> New validators implement interface without modifying existing code
 *
 * @param <T> the type of object being validated
 * @since 3.0
 */
@FunctionalInterface
public interface DomainValidator<T> {

    /**
     * Validate a domain object.
     *
     * @param object the object to validate (may be null)
     * @throws IllegalArgumentException if validation fails (never null)
     */
    void validate(T object);

    /**
     * Chain validators: return a new validator that runs both validations.
     *
     * <p>Example:
     * <pre>
     * DomainValidator&lt;UserContext&gt; validator =
     *   userIdValidator.andThen(emailValidator);
     * </pre>
     *
     * @param other the next validator to chain
     * @return composed validator
     */
    default DomainValidator<T> andThen(DomainValidator<T> other) {
        return obj -> {
            validate(obj);
            other.validate(obj);
        };
    }
}

