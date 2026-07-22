package com.example.tests.unit;

import com.example.core.ports.DomainValidator;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.*;

/**
 * Unit tests for generic domain validator.
 *
 * <p><b>Phase 3 Enhancement:</b> Demonstrates reusable generic validator for any type
 */
@DisplayName("DomainValidator — Generic Validator Tests")
class DomainValidatorTest {

    // ==================== String Validators ====================

    @Test
    @DisplayName("String validator — validates non-empty strings")
    void stringValidator_validatesNonEmpty() {
        DomainValidator<String> nonEmptyValidator = value -> {
            if (value == null || value.isBlank()) {
                throw new IllegalArgumentException("value must not be blank");
            }
        };

        // Valid
        assertThatNoException().isThrownBy(() -> nonEmptyValidator.validate("valid"));

        // Invalid
        assertThatThrownBy(() -> nonEmptyValidator.validate(""))
            .isInstanceOf(IllegalArgumentException.class)
            .hasMessageContaining("must not be blank");
    }

    @Test
    @DisplayName("String validator — chaining with andThen()")
    void stringValidator_chainsValidators_withAndThen() {
        DomainValidator<String> nonEmptyValidator = value -> {
            if (value == null || value.isBlank()) {
                throw new IllegalArgumentException("value must not be blank");
            }
        };

        DomainValidator<String> lengthValidator = value -> {
            if (value.length() < 3) {
                throw new IllegalArgumentException("value must be at least 3 characters");
            }
        };

        DomainValidator<String> composed = nonEmptyValidator.andThen(lengthValidator);

        // Both validations pass
        assertThatNoException().isThrownBy(() -> composed.validate("valid"));

        // First validation fails
        assertThatThrownBy(() -> composed.validate(""))
            .isInstanceOf(IllegalArgumentException.class)
            .hasMessageContaining("must not be blank");

        // Second validation fails (passes first, fails second)
        assertThatThrownBy(() -> composed.validate("ab"))
            .isInstanceOf(IllegalArgumentException.class)
            .hasMessageContaining("at least 3 characters");
    }

    // ==================== Integer Validators ====================

    @Test
    @DisplayName("Integer validator — validates range")
    void integerValidator_validatesRange() {
        DomainValidator<Integer> rangeValidator = value -> {
            if (value == null || value < 0 || value > 100) {
                throw new IllegalArgumentException("value must be between 0 and 100");
            }
        };

        assertThatNoException().isThrownBy(() -> rangeValidator.validate(50));
        assertThatThrownBy(() -> rangeValidator.validate(150))
            .hasMessageContaining("between 0 and 100");
    }

    // ==================== Custom Domain Objects ====================

    @Test
    @DisplayName("Custom validator — validates Person domain object")
    void customValidator_validatesPerson() {
        DomainValidator<Person> personValidator = person -> {
            if (person == null) {
                throw new IllegalArgumentException("person must not be null");
            }
            if (person.name == null || person.name.isBlank()) {
                throw new IllegalArgumentException("person.name must not be blank");
            }
            if (person.age < 0 || person.age > 150) {
                throw new IllegalArgumentException("person.age must be between 0 and 150");
            }
        };

        // Valid
        assertThatNoException().isThrownBy(() -> personValidator.validate(new Person("Alice", 30)));

        // Invalid name
        assertThatThrownBy(() -> personValidator.validate(new Person("", 30)))
            .hasMessageContaining("name must not be blank");

        // Invalid age
        assertThatThrownBy(() -> personValidator.validate(new Person("Alice", 200)))
            .hasMessageContaining("between 0 and 150");
    }

    @Test
    @DisplayName("Chained custom validators — validates Person with multiple rules")
    void chainedCustomValidators_validatesPerson_withMultipleRules() {
        DomainValidator<Person> nameValidator = person -> {
            if (person.name == null || person.name.isBlank()) {
                throw new IllegalArgumentException("name must not be blank");
            }
        };

        DomainValidator<Person> ageValidator = person -> {
            if (person.age < 18) {
                throw new IllegalArgumentException("age must be at least 18");
            }
        };

        DomainValidator<Person> emailValidator = person -> {
            if (person.email == null || !person.email.contains("@")) {
                throw new IllegalArgumentException("email must be valid");
            }
        };

        DomainValidator<Person> composed = nameValidator
            .andThen(ageValidator)
            .andThen(emailValidator);

        // All validations pass
        Person valid = new Person("Alice", 30);
        valid.email = "alice@example.com";
        assertThatNoException().isThrownBy(() -> composed.validate(valid));

        // Name fails
        assertThatThrownBy(() -> composed.validate(new Person("", 30)))
            .hasMessageContaining("name must not be blank");

        // Age fails
        assertThatThrownBy(() -> composed.validate(new Person("Alice", 16)))
            .hasMessageContaining("age must be at least 18");

        // Email fails
        Person invalidEmail = new Person("Alice", 30);
        invalidEmail.email = "no-at-sign";
        assertThatThrownBy(() -> composed.validate(invalidEmail))
            .hasMessageContaining("email must be valid");
    }

    // ==================== Helper Classes ====================

    /**
     * Example domain object for testing generic validator
     */
    static class Person {
        String name;
        int age;
        String email;

        Person(String name, int age) {
            this.name = name;
            this.age = age;
        }
    }
}

