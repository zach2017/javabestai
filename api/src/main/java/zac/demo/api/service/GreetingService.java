package zac.demo.api.service;

import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import zac.demo.api.model.Person;

/**
 * Builds greeting strings. Extracted so the controller has a
 * collaborator that can be mocked in slice tests.
 */
@Slf4j
@Service
public class GreetingService {

    public String greet(Person person) {
        log.debug("Building greeting for: {}", person);
        return "Hello, %s! (id=%d, role=%s)".formatted(
                person.getName(), person.getId(), person.getRole());
    }
}
