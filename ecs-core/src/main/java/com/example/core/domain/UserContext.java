package com.example.core.domain;
import java.util.Objects;
/**
 * Immutable domain object representing an authenticated user.
 * Extracted from Bearer token in HTTP Authorization header.
 * Thread-safe: suitable for request-scoped injection.
 */
public class UserContext {
    private final String userId;
    private final String email;
    private final String token;
    public UserContext(String userId, String email, String token) {
        if (userId == null || userId.isBlank()) {
            throw new IllegalArgumentException("userId must not be blank");
        }
        this.userId = Objects.requireNonNull(userId);
        this.email = email;
        this.token = token;
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
