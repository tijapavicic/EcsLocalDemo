package com.example.tests.unit;

import com.example.core.domain.UserContext;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.*;

/**
 * Unit tests for UserContext factory method and validation strategy.
 *
 * <p><b>Refactoring #1 (Phase 3):</b> Extract validation strategy via factory method.
 * Tests verify that UserContext.of() enforces validation rules without coupling to
 * specific validation implementations.
 */
@DisplayName("UserContext — Factory Pattern & Validation Strategy Tests")
class UserContextTest {

    @Test
    @DisplayName("of() — creates UserContext when userId is valid")
    void of_createsUserContext_whenUserIdIsValid() {
        UserContext context = UserContext.of("alice", "alice@example.com", "token123");

        assertThat(context).isNotNull();
        assertThat(context.getUserId()).isEqualTo("alice");
        assertThat(context.getEmail()).isEqualTo("alice@example.com");
        assertThat(context.getToken()).isEqualTo("token123");
    }

    @Test
    @DisplayName("of() — creates UserContext when email is null")
    void of_createsUserContext_whenEmailIsNull() {
        UserContext context = UserContext.of("bob", null, "token456");

        assertThat(context).isNotNull();
        assertThat(context.getUserId()).isEqualTo("bob");
        assertThat(context.getEmail()).isNull();
        assertThat(context.getToken()).isEqualTo("token456");
    }

    @Test
    @DisplayName("of() — creates UserContext when token is blank")
    void of_createsUserContext_whenTokenIsBlank() {
        UserContext context = UserContext.of("charlie", "charlie@example.com", "");

        assertThat(context).isNotNull();
        assertThat(context.getUserId()).isEqualTo("charlie");
        assertThat(context.getToken()).isEmpty();
    }

    @Test
    @DisplayName("of() — throws IllegalArgumentException when userId is null")
    void of_throwsIllegalArgument_whenUserIdIsNull() {
        assertThatThrownBy(() -> UserContext.of(null, "email@test.com", "token"))
            .isInstanceOf(IllegalArgumentException.class)
            .hasMessageContaining("userId must not be blank");
    }

    @Test
    @DisplayName("of() — throws IllegalArgumentException when userId is blank")
    void of_throwsIllegalArgument_whenUserIdIsBlank() {
        assertThatThrownBy(() -> UserContext.of("", "email@test.com", "token"))
            .isInstanceOf(IllegalArgumentException.class)
            .hasMessageContaining("userId must not be blank");
    }

    @Test
    @DisplayName("of() — throws IllegalArgumentException when userId is whitespace")
    void of_throwsIllegalArgument_whenUserIdIsWhitespace() {
        assertThatThrownBy(() -> UserContext.of("   ", "email@test.com", "token"))
            .isInstanceOf(IllegalArgumentException.class)
            .hasMessageContaining("userId must not be blank");
    }

    @Test
    @DisplayName("equals() — returns true for same userId")
    void equals_returnsTrue_forSameUserId() {
        UserContext context1 = UserContext.of("alice", "alice@example.com", "token1");
        UserContext context2 = UserContext.of("alice", "different@example.com", "token2");

        assertThat(context1).isEqualTo(context2);  // Equality based on userId only
    }

    @Test
    @DisplayName("equals() — returns false for different userId")
    void equals_returnsFalse_forDifferentUserId() {
        UserContext context1 = UserContext.of("alice", "alice@example.com", "token1");
        UserContext context2 = UserContext.of("bob", "alice@example.com", "token1");

        assertThat(context1).isNotEqualTo(context2);
    }

    @Test
    @DisplayName("hashCode() — returns same hash for same userId")
    void hashCode_returnsSameHash_forSameUserId() {
        UserContext context1 = UserContext.of("alice", "alice@example.com", "token1");
        UserContext context2 = UserContext.of("alice", "different@example.com", "token2");

        assertThat(context1.hashCode()).isEqualTo(context2.hashCode());
    }

    @Test
    @DisplayName("toString() — includes userId and email")
    void toString_includesUserIdAndEmail() {
        UserContext context = UserContext.of("alice", "alice@example.com", "token123");

        String str = context.toString();
        assertThat(str).contains("UserContext")
                       .contains("alice")
                       .contains("alice@example.com");
    }
}

