package zac.demo.api.model;

import jakarta.validation.ConstraintViolation;
import jakarta.validation.Validation;
import jakarta.validation.Validator;
import jakarta.validation.ValidatorFactory;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.Set;
import java.util.stream.Collectors;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Pure unit test for the bean-validation constraints on Person.
 * No Spring context — uses jakarta.validation.Validator directly.
 */
class PersonValidationTest {

    private static ValidatorFactory factory;
    private static Validator validator;

    @BeforeAll
    static void setUp() {
        factory = Validation.buildDefaultValidatorFactory();
        validator = factory.getValidator();
    }

    @AfterAll
    static void tearDown() {
        factory.close();
    }

    @Test
    @DisplayName("a fully populated Person has no violations")
    void valid_person_hasNoViolations() {
        Person person = new Person("Ada Lovelace", 42L, "ADMIN");

        Set<ConstraintViolation<Person>> violations = validator.validate(person);

        assertThat(violations).isEmpty();
    }

    @Test
    @DisplayName("blank name produces a violation on 'name'")
    void blank_name_isInvalid() {
        Person person = new Person("   ", 42L, "ADMIN");

        Set<String> badFields = fieldsInViolation(validator.validate(person));

        assertThat(badFields).containsExactly("name");
    }

    @Test
    @DisplayName("null id produces a violation on 'id'")
    void null_id_isInvalid() {
        Person person = new Person("Ada", null, "ADMIN");

        Set<String> badFields = fieldsInViolation(validator.validate(person));

        assertThat(badFields).containsExactly("id");
    }

    @Test
    @DisplayName("non-positive id produces a violation on 'id'")
    void nonPositive_id_isInvalid() {
        Person person = new Person("Ada", -1L, "ADMIN");

        Set<String> badFields = fieldsInViolation(validator.validate(person));

        assertThat(badFields).containsExactly("id");
    }

    @Test
    @DisplayName("blank role produces a violation on 'role'")
    void blank_role_isInvalid() {
        Person person = new Person("Ada", 42L, "");

        Set<String> badFields = fieldsInViolation(validator.validate(person));

        assertThat(badFields).containsExactly("role");
    }

    @Test
    @DisplayName("everything missing produces violations on all three fields")
    void allMissing_yieldsAllThreeViolations() {
        Person person = new Person(null, null, null);

        Set<String> badFields = fieldsInViolation(validator.validate(person));

        assertThat(badFields).containsExactlyInAnyOrder("name", "id", "role");
    }

    private Set<String> fieldsInViolation(Set<ConstraintViolation<Person>> violations) {
        return violations.stream()
                .map(v -> v.getPropertyPath().toString())
                .collect(Collectors.toSet());
    }
}
