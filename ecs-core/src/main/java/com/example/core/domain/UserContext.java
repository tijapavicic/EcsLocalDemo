package com.example.core.domain;
import java.util.Objects;
/**
 * Immutable domain object representing an authenticated user.
 * Extracted from Bearer token in HTTP Authorization header.
 * Thread-safe: suitable for request-scoped injection.
 *
 * <p><b>Design Pattern:</b> Factory method provides extensible validation without coupling
 * to specific validation strategies (SRP + OCP)
 */
public class UserContext {
    private final String userId;
    private final String email;
    private final String token;
    /**
     * Private constructor - enforces factory method usage.
     */
    private UserContext(String userId, String email, String token) {
        this.userId = userId;
        this.email = email;
        this.token = token;
    }
    /**
     * Factory method with default validation strategy.
     *
     * Validates userId is not null/blank; email and token optional.
     * @param userId authenticated user ID (required, not blank)
     * @param email user email address (optional)
     * @param token bearer token (optional)
     * @return immutable UserContext
     * @throws IllegalArgumentException if userId is blank or null
     */
    public static UserContext of(String userId, String email, String token) {
        if (userId == null || userId.isBlank()) {
            throw new IllegalArgumentException("userId must not be blank");
        }
        return new UserContext(userId, email, token);
    }
    public String getUserId() {
        return userId;
    }
    public String getEmail() {
        return email;
    }
    public String getToken() {
        return token;
    }
    @Override
    public String toString() {
        return "UserContext{userId='" + userId + "', email='" + email + "'}";
    }
    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        UserContext that = (UserContext) o;
        return Objects.equals(userId, that.userId);
    }
    @Override
    public int hashCode() {
        return Objects.hash(userId);
    }
}
