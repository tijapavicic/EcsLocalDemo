package com.example.core.ports;

/**
 * Outbound port for UserContext validation strategy.
 *
 * <p><b>Single Responsibility:</b> Encapsulates user context validation rules
 *
 * <p><b>Open/Closed Principle:</b> New validation rules can be added by implementing
 * this interface without modifying UserContext or calling code
 *
 * @since 1.0
 */
public interface UserContextValidatorPort {

    /**
     * Validate user context pre-conditions.
     *
     * @param userId authenticated user ID (e.g., "alice")
     * @param email user email address (optional)
     * @param token bearer token (optional)
     * @throws IllegalArgumentException if validation fails
     */
    void validate(String userId, String email, String token);

    /**
     * Default validation strategy - basic non-null/blank checks on userId.
     *
     * @return validator enforcing minimum requirements
     */
    static UserContextValidatorPort defaultValidator() {
        return (userId, email, token) -> {
            if (userId == null || userId.isBlank()) {
                throw new IllegalArgumentException("userId must not be blank");
            }
            // email and token can be null/blank (optional)
        };
    }
}

