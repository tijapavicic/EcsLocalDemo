package com.example.tests.unit;

import com.example.core.domain.GenericRuleConfiguration;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.*;

/**
 * Unit tests for generic rule configuration.
 *
 * <p><b>Phase 3 Enhancement:</b> Demonstrates generics for reusable configuration
 */
@DisplayName("GenericRuleConfiguration — Generic Configuration Tests")
class GenericRuleConfigurationTest {

    // ==================== String Rules (File Extensions) ====================

    @Test
    @DisplayName("String rules — creates config with file extensions")
    void stringRules_createsConfig_withExtensions() {
        GenericRuleConfiguration<String> config = new GenericRuleConfiguration<>(
            List.of("pdf", "docx", "xlsx")
        );

        assertThat(config.isEnforced()).isTrue();
        assertThat(config.getRules()).contains("pdf", "docx", "xlsx");
        assertThat(config.size()).isEqualTo(3);
    }

    @Test
    @DisplayName("String rules — checks if extension is allowed")
    void stringRules_checksExtension_ifAllowed() {
        GenericRuleConfiguration<String> config = new GenericRuleConfiguration<>(
            List.of("pdf", "jpg", "png")
        );

        assertThat(config.contains("pdf")).isTrue();
        assertThat(config.contains("exe")).isFalse();
    }

    @Test
    @DisplayName("String rules — empty list disables enforcement")
    void stringRules_emptyList_disablesEnforcement() {
        GenericRuleConfiguration<String> config = new GenericRuleConfiguration<>(List.of());

        assertThat(config.isEnforced()).isFalse();
        assertThat(config.size()).isZero();
    }

    // ==================== Long Rules (File Size Limits) ====================

    @Test
    @DisplayName("Long rules — creates config with file size limits")
    void longRules_createsConfig_withSizeLimits() {
        GenericRuleConfiguration<Long> config = new GenericRuleConfiguration<>(
            List.of(10485760L, 104857600L)  // 10MB, 100MB
        );

        assertThat(config.isEnforced()).isTrue();
        assertThat(config.getRules()).contains(10485760L, 104857600L);
    }

    @Test
    @DisplayName("Long rules — checks if size is within limits")
    void longRules_checkSize_withinLimits() {
        GenericRuleConfiguration<Long> config = new GenericRuleConfiguration<>(
            List.of(10485760L)  // 10MB limit
        );

        assertThat(config.contains(10485760L)).isTrue();
        assertThat(config.contains(104857600L)).isFalse();
    }

    // ==================== Custom Type Rules ====================

    @Test
    @DisplayName("Custom rules — creates config with custom constraint objects")
    void customRules_createsConfig_withCustomConstraints() {
        SizeConstraint constraint1 = new SizeConstraint("maxSize", 10485760L);
        SizeConstraint constraint2 = new SizeConstraint("minSize", 1024L);

        GenericRuleConfiguration<SizeConstraint> config = new GenericRuleConfiguration<>(
            List.of(constraint1, constraint2)
        );

        assertThat(config.isEnforced()).isTrue();
        assertThat(config.getRules()).contains(constraint1, constraint2);
    }

    // ==================== Null Handling ====================

    @Test
    @DisplayName("Null input — treats null as empty list")
    void nullInput_treatsAsEmpty() {
        GenericRuleConfiguration<String> config = new GenericRuleConfiguration<>(null);

        assertThat(config.isEnforced()).isFalse();
        assertThat(config.getRules()).isEmpty();
        assertThat(config.size()).isZero();
    }

    // ==================== Immutability ====================

    @Test
    @DisplayName("Immutability — returned rules list cannot be modified")
    void immutability_returnsUnmodifiableList() {
        GenericRuleConfiguration<String> config = new GenericRuleConfiguration<>(
            List.of("pdf", "docx")
        );

        List<String> rules = config.getRules();

        assertThatThrownBy(() -> rules.add("exe"))
            .isInstanceOf(UnsupportedOperationException.class);
    }

    // ==================== Helper Classes ====================

    /**
     * Example custom constraint for testing generics
     */
    static class SizeConstraint {
        final String name;
        final long value;

        SizeConstraint(String name, long value) {
            this.name = name;
            this.value = value;
        }

        @Override
        public boolean equals(Object o) {
            if (this == o) return true;
            if (o == null || getClass() != o.getClass()) return false;
            SizeConstraint that = (SizeConstraint) o;
            return value == that.value && name.equals(that.name);
        }

        @Override
        public int hashCode() {
            return java.util.Objects.hash(name, value);
        }
    }
}

