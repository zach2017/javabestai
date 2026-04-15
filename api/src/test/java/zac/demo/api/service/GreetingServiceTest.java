package zac.demo.api.service;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import zac.demo.api.model.Person;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Pure JUnit 5 unit test — no Spring, no Mockito needed since
 * GreetingService has no collaborators.
 */
class GreetingServiceTest {

    private GreetingService greetingService;

    @BeforeEach
    void setUp() {
        greetingService = new GreetingService();
    }

    @Test
    @DisplayName("greet() formats name, id and role into the expected string")
    void greet_formatsAllFields() {
        Person person = new Person("Ada Lovelace", 42L, "ADMIN");

        String greeting = greetingService.greet(person);

        assertThat(greeting).isEqualTo("Hello, Ada Lovelace! (id=42, role=ADMIN)");
    }

    @Test
    @DisplayName("greet() handles different role values")
    void greet_handlesDifferentRoles() {
        Person person = new Person("Grace Hopper", 7L, "USER");

        String greeting = greetingService.greet(person);

        assertThat(greeting).contains("Grace Hopper", "id=7", "role=USER");
    }
}
