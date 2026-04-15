# Demo API — comprehensive walkthrough

A Spring Boot 3.5 / Java 25 reference application that demonstrates a tight,
production-grade slice of an HTTP service: a single endpoint, full validation,
centralized error handling (JSON for API errors, HTML for 404s), Swagger
documentation, an `@Around` audit aspect with pluggable sinks, and a layered
test suite.

---

## Table of contents

1. [What this app does](#what-this-app-does)
2. [Project layout](#project-layout)
3. [Maven dependencies (pom.xml)](#maven-dependencies-pomxml)
4. [What happens when the app starts](#what-happens-when-the-app-starts)
5. [Configuration: `application.properties`](#configuration-applicationproperties)
6. [Class-by-class walkthrough](#class-by-class-walkthrough)
   - [`ApiApplication`](#apiapplication)
   - [`Person`](#person--zacdemoapimodelperson)
   - [`GreetingService`](#greetingservice--zacdemoapiservicegreetingservice)
   - [`HelloController`](#hellocontroller--zacdemoapicontrollerhellocontroller)
   - [`GlobalControllerAdvice`](#globalcontrolleradvice--zacdemoapiadviceglobalcontrolleradvice)
   - [`OpenApiConfig`](#openapiconfig--zacdemoapiconfigopenapiconfig)
   - [`AuditEvent` / `AuditEventType`](#auditevent--auditeventtype)
   - [`AuditProperties`](#auditproperties)
   - [`AuditAspect`](#auditaspect--zacdemoapiauditauditaspect)
   - [`AuditSink` interface](#auditsink-interface)
   - [`LogFileAuditSink`](#logfileauditsink)
7. [The audit subsystem in depth](#the-audit-subsystem-in-depth)
8. [Validation in depth](#validation-in-depth)
9. [Error handling in depth](#error-handling-in-depth)
10. [Swagger / OpenAPI](#swagger--openapi)
11. [Testing strategy](#testing-strategy)
12. [Running the app](#running-the-app)
13. [Security profiles](#security-profiles)
14. [Extending: adding a database or Elasticsearch audit sink](#extending-adding-a-database-or-elasticsearch-audit-sink)

---

## What this app does

The functional surface is intentionally tiny — one POST endpoint that takes a
`Person` (name, id, role) and returns a greeting:

```
POST /api/hello                    →   { "message": "Hello, Ada Lovelace! (id=42, role=ADMIN)",
                                          "person":  { "name": "Ada Lovelace", "id": 42, "role": "ADMIN" } }
```

Around that one endpoint sits everything you'd expect from a real service:

| Concern               | Where it lives                                      |
|-----------------------|-----------------------------------------------------|
| HTTP routing          | `HelloController`                                   |
| Business logic        | `GreetingService`                                   |
| Input validation      | Jakarta Bean Validation annotations on `Person`     |
| Error responses       | `GlobalControllerAdvice` (JSON + HTML 404 page)     |
| API documentation     | springdoc-openapi → Swagger UI at `/swagger-ui.html`|
| Auditing & timing     | `AuditAspect` (`@Around`) → pluggable `AuditSink`s  |
| File logging          | Spring Boot's `logging.file.name` → `logs/api.log`  |
| Tests                 | JUnit 5, Mockito, `@WebMvcTest`, `@SpringBootTest`  |

---

## Project layout

```
starter/
├── pom.xml
└── src/
    ├── main/
    │   ├── java/zac/demo/api/
    │   │   ├── ApiApplication.java
    │   │   ├── advice/
    │   │   │   └── GlobalControllerAdvice.java
    │   │   ├── audit/
    │   │   │   ├── AuditAspect.java
    │   │   │   ├── AuditEvent.java
    │   │   │   ├── AuditEventType.java
    │   │   │   ├── AuditProperties.java
    │   │   │   └── sink/
    │   │   │       ├── AuditSink.java
    │   │   │       └── LogFileAuditSink.java
    │   │   ├── config/
    │   │   │   ├── OpenApiConfig.java
    │   │   │   ├── SecurityBasicConfig.java
    │   │   │   ├── SecurityOffConfig.java
    │   │   │   └── SecurityUserProperties.java
    │   │   ├── controller/
    │   │   │   └── HelloController.java
    │   │   ├── model/
    │   │   │   └── Person.java
    │   │   └── service/
    │   │       └── GreetingService.java
    │   └── resources/
    │       ├── application.properties
    │       ├── application-security-basic.properties
    │       ├── application-security-off.properties
    │       └── static/
    │           ├── index.html
    │           └── tailwinds.css
    └── test/
        ├── java/zac/demo/api/
        │   ├── ApiApplicationTests.java
        │   ├── advice/GlobalControllerAdviceIT.java
        │   ├── audit/AuditAspectIT.java
        │   ├── config/
        │   │   ├── SecurityBasicProfileIT.java
        │   │   └── SecurityOffProfileIT.java
        │   ├── controller/HelloControllerTest.java
        │   ├── model/PersonValidationTest.java
        │   └── service/GreetingServiceTest.java
        └── resources/
            └── application.properties        (test-only overrides)
```

Package conventions:

- `model` — DTOs and validation-annotated payloads
- `service` — business logic
- `controller` — HTTP endpoints
- `advice` — `@RestControllerAdvice` exception handlers
- `config` — `@Configuration` beans (OpenAPI metadata, etc.)
- `audit` — cross-cutting audit concern (aspect, properties, events, sinks)

---

## Maven dependencies (pom.xml)

Spring Boot's parent POM manages the versions of every Spring-managed
artifact, so most of our `<dependency>` blocks have no explicit `<version>`.

| Dependency | Version | What it brings | Maven Central |
|---|---|---|---|
| `spring-boot-starter-parent` | 3.5.13 | BOM + plugin management for every Spring Boot artifact | [link](https://mvnrepository.com/artifact/org.springframework.boot/spring-boot-starter-parent/3.5.13) |
| `spring-boot-starter-web` | (managed) | Embedded Tomcat, Spring MVC, Jackson, validation auto-config triggers | [link](https://mvnrepository.com/artifact/org.springframework.boot/spring-boot-starter-web) |
| `spring-boot-starter-aop` | (managed) | Spring AOP + AspectJ runtime — required for `@Aspect` and `@Around` | [link](https://mvnrepository.com/artifact/org.springframework.boot/spring-boot-starter-aop) |
| `spring-boot-starter-validation` | (managed) | Hibernate Validator (the reference Jakarta Bean Validation impl) | [link](https://mvnrepository.com/artifact/org.springframework.boot/spring-boot-starter-validation) |
| `spring-boot-starter-security` | (managed) | Spring Security: filter chain, password encoders, in-memory user store | [link](https://mvnrepository.com/artifact/org.springframework.boot/spring-boot-starter-security) |
| `springdoc-openapi-starter-webmvc-ui` | 2.8.16 | Generates OpenAPI 3 docs from Spring MVC mappings + bundles Swagger UI | [link](https://mvnrepository.com/artifact/org.springdoc/springdoc-openapi-starter-webmvc-ui/2.8.16) |
| `org.projectlombok:lombok` | (managed) | Annotation processor: `@Data`, `@Slf4j`, `@RequiredArgsConstructor`, etc. | [link](https://mvnrepository.com/artifact/org.projectlombok/lombok) |
| `spring-boot-starter-test` (test scope) | (managed) | JUnit 5, AssertJ, Mockito, Hamcrest, Spring Test, MockMvc, JsonPath | [link](https://mvnrepository.com/artifact/org.springframework.boot/spring-boot-starter-test) |
| `spring-security-test` (test scope) | (managed) | `httpBasic(...)`, `user(...)` and other Spring Security test helpers | [link](https://mvnrepository.com/artifact/org.springframework.security/spring-security-test) |

A few notes on the pom plumbing that aren't dependencies but matter:

- `<java.version>25</java.version>` — Maven compiler is told to target Java 25.
- `spring-boot-maven-plugin` — produces an executable fat-jar via
  `mvn package`, and runs the app via `mvn spring-boot:run`. The
  `<excludes>` block strips Lombok from the runtime classpath (Lombok is a
  compile-time-only annotation processor).
- `maven-compiler-plugin` with two `<execution>` blocks — wires Lombok in as
  an `annotationProcessorPath` for both main and test compilation. Without
  this, `@Slf4j` and friends would not generate code.

---

## What happens when the app starts

When you run `mvn spring-boot:run` (or `java -jar api-0.0.1-SNAPSHOT.jar`),
the sequence is roughly:

1. **`ApiApplication.main`** calls `SpringApplication.run(...)`.
2. **Spring Boot bootstrap** detects starters on the classpath and applies
   their `AutoConfiguration` classes:
   - `WebMvcAutoConfiguration` wires Spring MVC, the `DispatcherServlet`,
     Jackson, content negotiation, and the static-resource handler.
   - `AopAutoConfiguration` enables `@EnableAspectJAutoProxy` so any
     `@Aspect`-annotated bean produces proxies for matched join points.
   - `ValidationAutoConfiguration` registers a `LocalValidatorFactoryBean`
     that backs `@Valid` on controller arguments.
   - `SpringDocConfiguration` registers the OpenAPI generator and serves
     `/v3/api-docs` and `/swagger-ui.html`.
   - `LoggingAutoConfiguration` reads `logging.file.name` and configures
     Logback to write to that file with a rolling policy.
3. **Component scanning** picks up everything in `zac.demo.api.**`:
   - `HelloController` (a `@RestController`)
   - `GreetingService` (a `@Service`)
   - `GlobalControllerAdvice` (a `@RestControllerAdvice`)
   - `OpenApiConfig` (a `@Configuration`)
   - `AuditAspect` (a `@Component @Aspect`)
   - `LogFileAuditSink` (a `@Component`, gated on
     `audit.sinks.file.enabled=true`)
4. **`@ConfigurationPropertiesScan`** on `ApiApplication` causes
   `AuditProperties` to be registered as a bean, with its fields populated
   from `application.properties` (or overrides in `application-*.properties`,
   environment variables, or command-line args).
5. **Bean wiring**:
   - `HelloController` gets `GreetingService` via constructor injection
     (`@RequiredArgsConstructor`).
   - `AuditAspect` gets `AuditProperties` and `List<AuditSink>` injected.
     Spring populates the list with every bean implementing `AuditSink` —
     today just `LogFileAuditSink`, tomorrow whatever else you add.
6. **Proxy creation** — for each `@RestController`, `@Service`, and
   `@RestControllerAdvice`, Spring creates a CGLIB subclass that intercepts
   methods matched by `AuditAspect`'s pointcuts.
7. **Embedded Tomcat starts** on port 8080 (default).
8. The app is ready. The first audit log line you'll see in `logs/api.log`
   typically comes from the first HTTP request that hits a matched bean.

---

## Configuration: `application.properties`

Walking the file top to bottom:

```properties
spring.application.name=api
```
Sets the app name shown in Spring Boot's banner and used by some auto-config
(e.g. service registry clients if you add them later).

### File logging block

```properties
logging.file.name=logs/api.log
```
Tells Logback to write to `logs/api.log` in the working directory. Spring
Boot's default Logback config uses a rolling-file appender as soon as this
property is set. Parent directories are created automatically.

```properties
logging.pattern.file=%d{yyyy-MM-dd HH:mm:ss.SSS} [%thread] %-5level %logger{36} - %msg%n
```
The conversion pattern for each file log line. `%logger{36}` truncates the
logger name to 36 characters — long enough to keep the class name readable
without dominating each line.

```properties
logging.logback.rollingpolicy.max-file-size=10MB
logging.logback.rollingpolicy.max-history=7
logging.logback.rollingpolicy.total-size-cap=200MB
```
When `api.log` reaches 10 MB it's rolled to `api.log.0.gz` (etc). 7 days of
history are retained, capped at 200 MB total disk usage.

```properties
logging.level.root=INFO
logging.level.zac.demo.api=INFO
```
Root level INFO; our package matches. To watch the audit aspect chatter
during local debugging, raise `logging.level.zac.demo.api.audit=DEBUG`. To
silence it entirely, set `WARN`.

### Audit aspect knobs

```properties
audit.enabled=true
```
Master kill switch. When `false`, the `@Around` advice immediately delegates
to `pjp.proceed()` without recording anything.

```properties
audit.log-args=true
audit.log-result=true
audit.log-timing=true
```
Field-level toggles. Useful patterns:
- Endpoints that receive PII or secrets: `audit.log-args=false`.
- Endpoints that return very large payloads: `audit.log-result=false`.
- Production with strict log-volume budgets: `audit.log-timing=false`.

```properties
audit.max-value-length=200
```
Any single `arg` or `result` rendered to a string longer than this is
truncated and tagged with the number of dropped characters
(e.g. `...(+1842 chars)`).

```properties
audit.include-swagger=true
```
When `false`, calls to anything in `org.springdoc..*` are *not* audited even
though the pointcut matches — the aspect short-circuits early. Useful in
production where Swagger gets polled by health checks.

### Sink wiring

```properties
audit.sinks.file.enabled=true
# audit.sinks.database.enabled=false
# audit.sinks.elasticsearch.enabled=false
```
Each sink implementation is gated by its own `@ConditionalOnProperty`.
Multiple can be active simultaneously; the aspect emits to all of them.

### Test-only override

`src/test/resources/application.properties` is loaded automatically when
tests run, overriding values from the main one:

```properties
logging.file.name=
```
Blanks out file logging during tests so test runs don't litter the working
directory with `logs/api.log`.

---

## Class-by-class walkthrough

### `ApiApplication`

```java
@SpringBootApplication
@ConfigurationPropertiesScan
public class ApiApplication {
    public static void main(String[] args) {
        SpringApplication.run(ApiApplication.class, args);
    }
}
```

- `@SpringBootApplication` is the meta-annotation that combines
  `@Configuration`, `@EnableAutoConfiguration`, and `@ComponentScan` on
  the current package.
- `@ConfigurationPropertiesScan` causes Spring to scan for
  `@ConfigurationProperties`-annotated classes (here, `AuditProperties`)
  and register them as beans without us writing
  `@EnableConfigurationProperties(AuditProperties.class)` anywhere.
- `SpringApplication.run` is the bootstrap call: it creates the
  `ApplicationContext`, runs auto-configuration, starts the embedded server,
  and blocks.

### `Person` — `zac.demo.api.model.Person`

A POJO that's the request body for `/api/hello`, with both Lombok and
validation annotations.

```java
@Data
@AllArgsConstructor
@NoArgsConstructor
@Schema(description = "A person with identifying information")
public class Person {

    @NotBlank(message = "name must not be blank")
    @Schema(description = "Full name of the person", example = "Ada Lovelace")
    private String name;

    @NotNull(message = "id must not be null")
    @Positive(message = "id must be a positive number")
    @Schema(description = "Unique numeric identifier", example = "42")
    private Long id;

    @NotBlank(message = "role must not be blank")
    @Schema(description = "Role assigned to the person", example = "ADMIN")
    private String role;
}
```

Line by line:

- `@Data` — Lombok generates getters, setters, `equals`, `hashCode`, and
  `toString`.
- `@AllArgsConstructor` — generates `new Person(name, id, role)`. Used
  heavily in tests.
- `@NoArgsConstructor` — generates `new Person()`. Required by Jackson for
  default JSON deserialization.
- `@Schema(description = ...)` on the class — surfaces a description on the
  Person model in Swagger UI.
- `@NotBlank` on `name` and `role` — the value must be non-null and contain
  at least one non-whitespace character.
- `@NotNull` + `@Positive` on `id` — the value must be present and `> 0`.
- Each field also carries `@Schema(description, example)` so Swagger UI can
  show the field's purpose and pre-populate the "Try it out" form.

### `GreetingService` — `zac.demo.api.service.GreetingService`

```java
@Slf4j
@Service
public class GreetingService {

    public String greet(Person person) {
        log.debug("Building greeting for: {}", person);
        return "Hello, %s! (id=%d, role=%s)".formatted(
                person.getName(), person.getId(), person.getRole());
    }
}
```

- `@Service` — registers as a Spring-managed bean and marks intent (business
  logic, not a controller or repository).
- `@Slf4j` — Lombok generates `private static final Logger log = LoggerFactory.getLogger(GreetingService.class);`.
- `greet(Person)` — pure formatting, no side effects beyond the debug log.
  Extracted from the controller specifically so the controller has a real
  collaborator to mock in slice tests.

### `HelloController` — `zac.demo.api.controller.HelloController`

```java
@Slf4j
@RestController
@RequiredArgsConstructor
@RequestMapping(value = "/api", produces = MediaType.APPLICATION_JSON_VALUE)
@Tag(name = "Hello", description = "Simple hello-world endpoint")
public class HelloController {

    private final GreetingService greetingService;

    @Operation(
            summary = "Greet a person",
            description = "Accepts a Person (name, id, role) and returns a hello-world greeting."
    )
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Greeting generated successfully"),
            @ApiResponse(responseCode = "400", description = "Validation failed",
                    content = @Content(schema = @Schema(implementation = Map.class)))
    })
    @PostMapping(value = "/hello", consumes = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<Map<String, Object>> hello(@Valid @RequestBody Person person) {
        log.info("Received /hello request: {}", person);
        String message = greetingService.greet(person);
        log.debug("Returning greeting: {}", message);
        return ResponseEntity.ok(Map.of(
                "message", message,
                "person", person
        ));
    }
}
```

- `@RestController` — combines `@Controller` and `@ResponseBody`. Every
  return value is serialized via Jackson by default.
- `@RequiredArgsConstructor` — Lombok generates a constructor for every
  `final` field, which Spring then uses for constructor injection.
  `GreetingService` is wired in this way.
- `@RequestMapping("/api", produces=APPLICATION_JSON_VALUE)` — base path
  prefix and default response content type for every handler in the class.
- `@Tag` — groups this controller's endpoints under "Hello" in Swagger UI.
- `@Operation` and `@ApiResponses` — Swagger-only metadata for the operation
  summary and the responses listed in the docs.
- `@PostMapping("/hello", consumes=APPLICATION_JSON_VALUE)` — only matches
  `POST /api/hello` with a JSON request body. A non-JSON body returns
  `415 Unsupported Media Type` automatically.
- `@Valid @RequestBody Person person` — `@RequestBody` triggers Jackson
  deserialization; `@Valid` triggers Bean Validation against `Person`'s
  constraints. If validation fails, Spring throws
  `MethodArgumentNotValidException` *before* the method body runs, and the
  advice handles it.
- The method itself is now small: log, delegate, log, respond. All input
  guarding is upstream (validation), all error rendering is downstream
  (advice).

### `GlobalControllerAdvice` — `zac.demo.api.advice.GlobalControllerAdvice`

This is where every uncaught exception in the controller layer ends up.

The class is `@RestControllerAdvice`, so each `@ExceptionHandler` returns an
HTTP response body via Jackson — except the 404 handler, which returns raw
HTML.

#### 404 → HTML page

```java
@ExceptionHandler({NoHandlerFoundException.class, NoResourceFoundException.class})
public ResponseEntity<String> handleNotFound(Exception ex) {
    log.warn("404 not found: {}", ex.getMessage());
    return ResponseEntity.status(HttpStatus.NOT_FOUND)
            .contentType(MediaType.TEXT_HTML)
            .body(notFoundHtml());
}
```

- `NoResourceFoundException` is what Spring Boot 3.2+ throws when the
  static resource handler fails to find a matching path.
- `NoHandlerFoundException` is the older variant; included for safety.
- We return `ResponseEntity<String>` with `contentType=text/html`. Spring
  notices the explicit content type and writes the body via
  `StringHttpMessageConverter` (raw string) instead of Jackson (which would
  wrap it in quotes).
- The HTML body uses Tailwind classes loaded from `/tailwinds.css` (which
  this project bundles as the runtime JIT compiler script — see the next
  section).

#### Validation failures

```java
@ExceptionHandler(MethodArgumentNotValidException.class)
public ResponseEntity<Map<String, Object>> handleValidation(MethodArgumentNotValidException ex) {
    Map<String, String> fieldErrors = new LinkedHashMap<>();
    for (FieldError fe : ex.getBindingResult().getFieldErrors()) {
        fieldErrors.put(fe.getField(), fe.getDefaultMessage());
    }
    Map<String, Object> body = baseEnvelope(HttpStatus.BAD_REQUEST, "Validation failed");
    body.put("fieldErrors", fieldErrors);
    return ResponseEntity.badRequest().body(body);
}
```

The `MethodArgumentNotValidException` carries a `BindingResult` with one
`FieldError` per failed constraint. We flatten that to
`{ "name": "name must not be blank", ... }` and add it to the standard
envelope. The result on a fully invalid `Person` looks like:

```json
{
  "timestamp": "2026-04-15T17:30:12.345Z",
  "status": 400,
  "error": "Bad Request",
  "message": "Validation failed",
  "fieldErrors": {
    "name": "name must not be blank",
    "id":   "id must not be null",
    "role": "role must not be blank"
  }
}
```

#### Other handlers

- `IllegalArgumentException` → 400. Defensive — kept around in case the
  controller ever throws this directly.
- `HttpMessageNotReadableException` → 400. Triggered by malformed JSON in
  the request body (Jackson can't parse it).
- `Exception.class` → 500. Catch-all so we never leak stack traces to the
  client; we log them server-side at ERROR level.

All four return the same JSON envelope shape via `baseEnvelope(...)`, with
keys `timestamp`, `status`, `error`, `message` (in `LinkedHashMap` order,
which Jackson preserves).

### `OpenApiConfig` — `zac.demo.api.config.OpenApiConfig`

```java
@Slf4j
@Configuration
public class OpenApiConfig {

    @Bean
    public OpenAPI apiInfo() {
        log.info("Initializing OpenAPI / Swagger configuration");
        return new OpenAPI().info(new Info()
                .title("Demo API")
                .description("Simple hello-world API with a single Person endpoint.")
                .version("v1"));
    }
}
```

Without this, springdoc would still generate a Swagger UI but with default,
generic metadata. The `OpenAPI` bean overrides the `info` block at the top
of the OpenAPI document.

### `AuditEvent` / `AuditEventType`

```java
public enum AuditEventType { ENTRY, EXIT, ERROR }

public record AuditEvent(
        Instant timestamp,
        AuditEventType type,
        String className,
        String methodName,
        String argsFormatted,      // populated on ENTRY
        String resultFormatted,    // populated on EXIT
        String exceptionType,      // populated on ERROR
        String exceptionMessage,   // populated on ERROR
        Long durationMs            // populated on EXIT and ERROR
) {}
```

A Java record gives us an immutable value object with auto-generated
constructor, accessors, `equals`/`hashCode`/`toString`. The `AuditEvent` is
deliberately structured (not just a string) so different sinks can render
it differently — text for log files, columns for SQL, JSON for ES.

Conventions on which fields are populated for each `type`:

| Field             | ENTRY | EXIT | ERROR |
|-------------------|-------|------|-------|
| `argsFormatted`   | ✓     |      |       |
| `resultFormatted` |       | ✓    |       |
| `exceptionType`   |       |      | ✓     |
| `exceptionMessage`|       |      | ✓     |
| `durationMs`      |       | ✓    | ✓     |

### `AuditProperties`

```java
@Data
@ConfigurationProperties(prefix = "audit")
public class AuditProperties {
    private boolean enabled = true;
    private boolean logArgs = true;
    private boolean logResult = true;
    private boolean logTiming = true;
    private int maxValueLength = 200;
    private boolean includeSwagger = true;
}
```

- `@ConfigurationProperties(prefix = "audit")` — Spring binds every
  property under `audit.*` to the matching field. Spring uses *relaxed
  binding*, so `audit.log-args`, `audit.logArgs`, and the env var
  `AUDIT_LOG_ARGS` all map to the same field.
- `@Data` — Lombok-generated setters are how Spring writes values during
  binding.
- Defaults are written inline so the app boots correctly even with an
  empty properties file.
- The class is bean-registered by `@ConfigurationPropertiesScan` on
  `ApiApplication`.

### `AuditAspect` — `zac.demo.api.audit.AuditAspect`

The heart of the cross-cutting concern. This is where AOP weaves auditing
into every controller, service, and advice call.

```java
@Slf4j
@Aspect
@Component
@RequiredArgsConstructor
public class AuditAspect {
    private final AuditProperties props;
    private final List<AuditSink> sinks;

    @Pointcut("@within(org.springframework.web.bind.annotation.RestController) "
            + "|| @within(org.springframework.web.bind.annotation.RestControllerAdvice) "
            + "|| @within(org.springframework.stereotype.Service)")
    public void appComponents() {}

    @Pointcut("within(org.springdoc..*)")
    public void swaggerComponents() {}

    @Around("appComponents() || swaggerComponents()")
    public Object audit(ProceedingJoinPoint pjp) throws Throwable { ... }
}
```

#### Annotations

- `@Aspect` — marks the class as containing pointcuts and advice. Required
  by AspectJ (and by Spring's `@EnableAspectJAutoProxy`).
- `@Component` — registers as a bean. Without this, Spring AOP wouldn't
  discover the aspect.
- `@RequiredArgsConstructor` — Lombok generates a constructor taking
  `AuditProperties` and `List<AuditSink>`. Spring uses it for constructor
  injection. The `List<AuditSink>` is automatically populated with every
  `AuditSink` bean in the context.

#### Pointcuts

A pointcut is a predicate that selects join points (method invocations).
This aspect has two:

- `appComponents()` uses `@within(...)` to match join points inside any
  type annotated with `@RestController`, `@RestControllerAdvice`, or
  `@Service`. That covers `HelloController`, `GlobalControllerAdvice`, and
  `GreetingService` — but does *not* cover `Person` (it's not a stereotype
  bean), so Lombok-generated getters/setters never get audited.
- `swaggerComponents()` uses `within(org.springdoc..*)` to match anything
  in springdoc's package tree. That picks up the controllers serving
  `/v3/api-docs` and `/swagger-ui.html`.

#### Advice

The `@Around` advice runs *instead of* the method, with `pjp.proceed()`
delegating to the real implementation. Pseudocode:

```
1. If audit disabled → just proceed, don't record anything.
2. If this is a swagger call AND include-swagger is false → just proceed.
3. Render args (or "<args hidden>" if log-args disabled).
4. Record an ENTRY event to all sinks. Note the start nanos.
5. try {
       result = pjp.proceed();
       elapsed = nanos elapsed (or null if log-timing disabled);
       Render result (or "<result hidden>" if log-result disabled).
       Record an EXIT event.
       return result;
   } catch (Throwable t) {
       elapsed = nanos elapsed;
       Record an ERROR event with t.getClass().getSimpleName() and t.getMessage().
       throw t;          // never swallow exceptions
   }
```

Two important properties:

- **Exceptions are rethrown verbatim.** Auditing observes; it does not
  alter behavior.
- **Sink failures don't break the request.** `publish(...)` wraps each
  `sink.record(event)` in its own try/catch.

#### How the proxy gets created

When Spring wires `HelloController`, it sees that an `@Aspect` matches
join points on it. Instead of injecting the raw `HelloController`, Spring
hands consumers a CGLIB-generated subclass whose overridden methods invoke
`AuditAspect.audit(...)` first, which then calls `super.method(...)` via
`pjp.proceed()`.

This means: `this.someInternalMethod()` calls inside `HelloController`
*do not* go through the proxy and aren't audited. Auditing only fires on
calls that come in from outside the bean (e.g. the dispatcher servlet
calling `controller.hello(...)`).

### `AuditSink` interface

```java
public interface AuditSink {
    void record(AuditEvent event);
}
```

Two-line strategy interface. The aspect knows about the interface, not
about any specific implementation.

### `LogFileAuditSink`

```java
@Slf4j
@Component
@ConditionalOnProperty(name = "audit.sinks.file.enabled", havingValue = "true", matchIfMissing = true)
public class LogFileAuditSink implements AuditSink {
    @Override
    public void record(AuditEvent event) {
        try {
            switch (event.type()) {
                case ENTRY -> log.info("→ {}.{}({})",
                        event.className(), event.methodName(), event.argsFormatted());
                case EXIT -> log.info("← {}.{} returned {}{}",
                        event.className(), event.methodName(),
                        event.resultFormatted(), formatTiming(event.durationMs()));
                case ERROR -> log.warn("✗ {}.{} threw {}: {}{}",
                        event.className(), event.methodName(),
                        event.exceptionType(), event.exceptionMessage(),
                        formatTiming(event.durationMs()));
            }
        } catch (Exception e) {
            log.warn("Failed to write audit event", e);
        }
    }
    ...
}
```

- `@ConditionalOnProperty(... matchIfMissing = true)` — sink is active by
  default; only disabled if `audit.sinks.file.enabled=false` is set
  explicitly.
- The sink uses SLF4J to log. Since Spring Boot routes SLF4J → Logback →
  the file appender configured by `logging.file.name`, the same lines
  appear both on console and in `logs/api.log`.
- ENTRY and EXIT log at INFO; ERROR logs at WARN. Tune via
  `logging.level.zac.demo.api.audit.sink.LogFileAuditSink=...` if you want
  a different threshold.

---

## The audit subsystem in depth

### Request flow with auditing

For a successful `POST /api/hello`:

```
HTTP request
   │
   ▼
DispatcherServlet
   │
   ▼
HelloController$$CGLIB.hello(person)             ← proxy
   │
   ▼ (intercept)
AuditAspect.audit(joinPoint)
   │
   ├──► sink.record(ENTRY ... HelloController.hello)
   │
   ▼ proceed()
HelloController.hello(person)                     ← real method
   │
   ▼
GreetingService$$CGLIB.greet(person)              ← proxy
   │
   ▼ (intercept)
AuditAspect.audit(joinPoint)
   │
   ├──► sink.record(ENTRY ... GreetingService.greet)
   │
   ▼ proceed()
GreetingService.greet(person)                     ← real method
   │
   ▼ returns "Hello, ..."
   │
   ▼ back in aspect
   ├──► sink.record(EXIT ... GreetingService.greet returned "Hello, ...")
   │
   ▼ back in HelloController.hello
   │
   ▼ returns ResponseEntity.ok(...)
   │
   ▼ back in aspect
   ├──► sink.record(EXIT ... HelloController.hello returned <ResponseEntity>)
   │
   ▼
Jackson serializes → HTTP response
```

So a single happy-path POST produces **four** audit lines (controller
entry, service entry, service exit, controller exit) plus whatever
Springdoc would log if you call its endpoints.

### Pointcut expression cheat sheet

| Expression                                              | Matches                                              |
|---------------------------------------------------------|------------------------------------------------------|
| `@within(SomeAnnotation)`                               | Methods inside types annotated with `SomeAnnotation` |
| `within(com.example..*)`                                | Methods inside any class in `com.example` or subpkg  |
| `execution(public * com.example..*(..))`                | Public methods in `com.example` (and subpackages)    |
| `@annotation(SomeAnnotation)`                           | Methods directly annotated with `SomeAnnotation`     |

This aspect uses `@within` for stereotype matching and `within` for
package matching. Both pick up all public methods of matched types.

### Caveats

- **CGLIB can't proxy `final` classes or `final` methods.** Springdoc's
  classes aren't final to my knowledge, but if the app fails to start with
  a `Cannot subclass final class` error, narrow the swagger pointcut or
  remove it.
- **Self-invocations bypass the proxy.** A method calling another method
  on `this` does not go through CGLIB and isn't audited.
- **Static resource requests don't hit a controller**, so they're not
  audited. If you need HTTP-level logging for assets too, write a
  `OncePerRequestFilter`.

---

## Validation in depth

The validation flow on `POST /api/hello`:

1. Jackson deserializes the JSON body into a `Person`.
2. Spring sees `@Valid` on the `@RequestBody` and runs Hibernate Validator
   against the constraints declared on `Person`.
3. If any constraint fails, Spring throws `MethodArgumentNotValidException`
   *before* the controller method is invoked.
4. `GlobalControllerAdvice.handleValidation(...)` catches it and returns
   the JSON envelope with per-field error messages.

The per-constraint behavior:

| Constraint                | Fails when                                  |
|---------------------------|---------------------------------------------|
| `@NotBlank` on `name`     | name is null, empty, or only whitespace     |
| `@NotNull` on `id`        | id is missing                               |
| `@Positive` on `id`       | id is `<= 0` (only checked if non-null)     |
| `@NotBlank` on `role`     | role is null, empty, or only whitespace     |

Note that `@NotNull` on `id` is required because `@Positive` allows nulls
(it only validates non-null values). The two together give "must be
present and `> 0`".

---

## Error handling in depth

Decision tree for any incoming request:

```
Request arrives
  │
  ├── Path matches controller mapping?
  │     │
  │     ├── No → NoResourceFoundException → handleNotFound → HTML 404
  │     │
  │     └── Yes
  │           │
  │           ├── Body parses as JSON?
  │           │     │
  │           │     └── No → HttpMessageNotReadableException
  │           │              → handleUnreadable → JSON 400
  │           │
  │           └── Yes
  │                 │
  │                 ├── @Valid passes?
  │                 │     │
  │                 │     └── No → MethodArgumentNotValidException
  │                 │              → handleValidation → JSON 400 with fieldErrors
  │                 │
  │                 └── Yes → controller method runs
  │                              │
  │                              ├── throws IllegalArgumentException → JSON 400
  │                              ├── throws anything else → JSON 500
  │                              └── returns normally → JSON 200
```

The HTML 404 page lives inside `notFoundHtml()` as a Java text block. It
loads `/tailwinds.css`, which in this app is the Tailwind runtime JIT
compiler bundle (a JavaScript file misnamed `.css` for backward
compatibility with the existing `index.html`). The browser executes the
script, which scans the rendered HTML for utility classes and injects the
needed CSS.

---

## Swagger / OpenAPI

Two URLs come for free with `springdoc-openapi-starter-webmvc-ui`:

- **OpenAPI document (JSON):** http://localhost:8080/v3/api-docs
- **Swagger UI:**             http://localhost:8080/swagger-ui.html

The document is generated by reading every `@RestController` mapping and
combining it with:

- `@Tag` / `@Operation` / `@ApiResponses` annotations on controllers and
  methods
- `@Schema` annotations on model classes and fields
- The `OpenAPI` bean from `OpenApiConfig` (sets the `info` block)

To try the endpoint from Swagger UI:

1. Open `/swagger-ui.html`.
2. Expand "Hello" → `POST /api/hello` → "Try it out".
3. Edit the example body if you want, then "Execute".

---

## Testing strategy

The test suite is layered to match the complexity of what's being tested:

| Test class                  | Annotation                | Loads                                   | Tests                                     |
|-----------------------------|---------------------------|-----------------------------------------|-------------------------------------------|
| `PersonValidationTest`      | (none — pure JUnit)       | Just JVM + Jakarta Validator            | Bean validation constraints in isolation  |
| `GreetingServiceTest`       | (none — pure JUnit)       | Just JVM                                | Service formatting logic                  |
| `HelloControllerTest`       | `@WebMvcTest`             | MVC slice + this controller + advice    | HTTP routing, validation, error JSON      |
| `GlobalControllerAdviceIT`  | `@SpringBootTest`         | Full app context + MockMvc              | HTML 404 page (needs full static-resource setup) |
| `AuditAspectIT`             | `@SpringBootTest`         | Full app context + MockMvc              | Aspect + sinks + properties               |
| `SecurityOffProfileIT`      | `@SpringBootTest` + `@ActiveProfiles("security-off")` | Full context, security excluded | Endpoints public, no `SecurityFilterChain` bean |
| `SecurityBasicProfileIT`    | `@SpringBootTest` + `@ActiveProfiles("security-basic")` | Full context with HTTP Basic | 401 vs 200 paths, swagger bypass, properties bound |

Suffix convention: `Test` for fast unit / slice tests, `IT` for
integration tests that load the full Spring context.

### `PersonValidationTest`

Pure JUnit 5 — uses `Validation.buildDefaultValidatorFactory()` to get a
`Validator` and validates `Person` instances directly. No Spring context.
Six test methods, one per constraint case plus a "valid" baseline and an
"all missing" case.

```java
@Test
void blank_name_isInvalid() {
    Person person = new Person("   ", 42L, "ADMIN");
    Set<String> badFields = fieldsInViolation(validator.validate(person));
    assertThat(badFields).containsExactly("name");
}
```

### `GreetingServiceTest`

Pure JUnit 5 — `new GreetingService()` and assert on output. No Mockito
needed; the service has no collaborators.

### `HelloControllerTest` — `@WebMvcTest`

Loads only Spring MVC infrastructure plus the controller class specified
in the annotation. Other `@Component` beans (including `AuditAspect`) are
*not* loaded, which keeps the slice fast.

```java
@WebMvcTest(HelloController.class)
class HelloControllerTest {

    @Autowired private MockMvc mockMvc;
    @Autowired private ObjectMapper objectMapper;
    @MockitoBean private GreetingService greetingService;

    @Test
    void hello_returnsGreeting_forValidPerson() throws Exception {
        when(greetingService.greet(any(Person.class)))
                .thenReturn("Hello, Ada Lovelace! (id=42, role=ADMIN)");

        mockMvc.perform(post("/api/hello")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(new Person("Ada Lovelace", 42L, "ADMIN"))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.message").value("Hello, Ada Lovelace! (id=42, role=ADMIN)"));

        verify(greetingService).greet(any(Person.class));
    }
}
```

Key pieces:

- **`@WebMvcTest(HelloController.class)`** — slice test annotation.
  Loads only `@Controller`, `@ControllerAdvice`, `@JsonComponent`,
  `Converter`, `Filter`, `HandlerInterceptor` beans, plus MVC
  infrastructure. Crucially, `@ControllerAdvice` *is* picked up
  automatically, so we can assert on the JSON error envelope from
  `GlobalControllerAdvice` without extra wiring.
- **`@Autowired MockMvc`** — Spring Boot auto-configures it under
  `@WebMvcTest`. MockMvc lets us perform HTTP requests against the
  controller without starting an actual server.
- **`@Autowired ObjectMapper`** — used to serialize Person to JSON for
  the request body.
- **`@MockitoBean GreetingService`** — Spring 6.2 / Boot 3.4+
  replacement for the older `@MockBean`. Replaces the real bean with a
  Mockito mock for the duration of the test.
- **`when(...).thenReturn(...)`** — standard Mockito stubbing.
- **`mockMvc.perform(...)`** — execute the request.
- **`andExpect(jsonPath("$..."))`** — assert on the response body using
  JsonPath syntax (`$` is the root, `.message` is a field).
- **`verify(...)`** — Mockito verification that the mock was actually
  called.

The class covers happy path, three validation failure scenarios, and
malformed JSON.

### `GlobalControllerAdviceIT` — `@SpringBootTest`

Why `@SpringBootTest` and not `@WebMvcTest`? The 404 handler depends on
Spring Boot's static resource auto-configuration to throw
`NoResourceFoundException` for unmapped paths. That auto-config isn't
fully active under the slice annotation. `@SpringBootTest` loads the
real context so behavior matches production.

```java
@SpringBootTest
@AutoConfigureMockMvc
class GlobalControllerAdviceIT {

    @Autowired private MockMvc mockMvc;

    @Test
    void unknownPath_returnsHtml404Page() throws Exception {
        mockMvc.perform(get("/this-path-does-not-exist"))
                .andExpect(status().isNotFound())
                .andExpect(content().contentTypeCompatibleWith(MediaType.TEXT_HTML))
                .andExpect(content().string(containsString("Page not found")))
                .andExpect(content().string(containsString("/tailwinds.css")));
    }
}
```

`@AutoConfigureMockMvc` is needed because `@SpringBootTest` doesn't bring
MockMvc by default.

### `AuditAspectIT` — `@SpringBootTest`

The most involved test. It hooks into Logback to capture what
`LogFileAuditSink` emits, exercises the `audit.*` properties at runtime
by mutating the autowired `AuditProperties` bean, and asserts on the
resulting log lines.

```java
@SpringBootTest
@AutoConfigureMockMvc
class AuditAspectIT {

    @Autowired private MockMvc mockMvc;
    @Autowired private ObjectMapper objectMapper;
    @Autowired private AuditProperties auditProperties;

    private ListAppender<ILoggingEvent> appender;
    private Logger sinkLogger;

    @BeforeEach
    void attachAppender() {
        sinkLogger = (Logger) LoggerFactory.getLogger(LogFileAuditSink.class);
        appender = new ListAppender<>();
        appender.start();
        sinkLogger.addAppender(appender);
    }

    @AfterEach
    void detachAppender() {
        sinkLogger.detachAppender(appender);
        appender.stop();
        // reset properties so test order doesn't matter
        auditProperties.setEnabled(true);
        auditProperties.setLogArgs(true);
        ...
    }
    ...
}
```

Each test mutates one property and verifies the resulting log behavior
changes — `audit.enabled=false` → no lines at all; `audit.log-args=false`
→ entry lines say `<args hidden>`; `audit.log-timing=false` → exit lines
have no `[Xms]` suffix; `audit.include-swagger=false` → swagger calls
produce no audit lines.

The `@AfterEach` resets every mutated property so individual tests are
independent — JUnit 5 doesn't recreate the Spring context between tests
in a class by default, so the `AuditProperties` bean is the same instance
across all tests.

### Test conventions used throughout

- **JUnit 5 (`org.junit.jupiter`)** — `@Test`, `@DisplayName`,
  `@BeforeEach`/`@AfterEach`/`@BeforeAll`/`@AfterAll`.
- **AssertJ** for fluent assertions: `assertThat(...).isEqualTo(...)`,
  `.containsExactly(...)`, `.anyMatch(...)`. Bundled with
  `spring-boot-starter-test`.
- **Mockito** for mocking: `when(...).thenReturn(...)`,
  `verify(...)`, `verifyNoInteractions(...)`. Bundled with
  `spring-boot-starter-test`.
- **`@MockitoBean`** for replacing context beans with mocks (Spring Boot
  3.4+).
- **MockMvc** for HTTP-level controller testing without an actual server.
- **JsonPath** (`jsonPath("$.field")`) for asserting on JSON response
  bodies.

---

## Running the app

Maven wrapper is recommended so everyone uses the same Maven version, but
plain `mvn` works too.

```bash
# Run all tests
./mvnw test

# Run a single test class
./mvnw test -Dtest=HelloControllerTest

# Run with INFO+audit logs streamed to console
./mvnw spring-boot:run

# Build an executable jar
./mvnw clean package
java -jar target/api-0.0.1-SNAPSHOT.jar
```

URLs once the app is running on the default port 8080:

- App home (the bundled K8s Studio HTML): http://localhost:8080/
- Hello endpoint: `POST http://localhost:8080/api/hello`
- Swagger UI: http://localhost:8080/swagger-ui.html
- OpenAPI JSON: http://localhost:8080/v3/api-docs
- Any unmapped path: returns the Tailwind-styled 404 page

Audit log lines appear on stdout and in `./logs/api.log` (rolling).

A quick smoke test from the command line:

```bash
curl -sS -X POST http://localhost:8080/api/hello \
     -H 'Content-Type: application/json' \
     -d '{"name":"Ada Lovelace","id":42,"role":"ADMIN"}' | jq

curl -sS -X POST http://localhost:8080/api/hello \
     -H 'Content-Type: application/json' \
     -d '{"name":"","id":-1,"role":""}' | jq
```

The first returns 200 with the greeting; the second returns 400 with
`fieldErrors` for all three fields.

---

## Security profiles

Two Spring profiles control whether security is enforced. The active
profile is selected by `spring.profiles.active` (default: `security-off`).

| Profile | Auth | SecurityAutoConfiguration | Configuration class | Static user |
|---|---|---|---|---|
| `security-off` | none — all endpoints public | excluded via property | `SecurityOffConfig` | n/a |
| `security-basic` | HTTP Basic on `/api/**` | active, overridden by our `SecurityFilterChain` | `SecurityBasicConfig` | `admin` / `changeme` |

Switch profiles at runtime:

```bash
./mvnw spring-boot:run -Dspring-boot.run.profiles=security-basic
# or
java -jar target/api-0.0.1-SNAPSHOT.jar --spring.profiles.active=security-basic
# or
SPRING_PROFILES_ACTIVE=security-basic ./mvnw spring-boot:run
```

### How `security-off` works

`application-security-off.properties` excludes Spring Boot's security
auto-configuration entirely:

```properties
spring.autoconfigure.exclude=\
  org.springframework.boot.autoconfigure.security.servlet.SecurityAutoConfiguration,\
  org.springframework.boot.autoconfigure.security.servlet.UserDetailsServiceAutoConfiguration,\
  org.springframework.boot.autoconfigure.security.servlet.SecurityFilterAutoConfiguration
```

The exclusion lives in the profile-specific properties file (not on
`@SpringBootApplication(exclude = ...)`) precisely so it only takes
effect when the profile is active.

`SecurityOffConfig` itself is mostly informational — a `@PostConstruct`
log line announces the profile so you can see at a glance from the boot
log that the app is running unauthenticated. It's also a stable hook for
adding profile-only beans later (e.g. a permissive CORS config that you'd
only want when there's no security in the way).

### How `security-basic` works

`SecurityBasicConfig` provides four beans:

**`SecurityFilterChain`** — the central authorization configuration.

```java
return http
        .csrf(csrf -> csrf.disable())
        .sessionManagement(sm -> sm.sessionCreationPolicy(STATELESS))
        .authorizeHttpRequests(auth -> auth
                .requestMatchers("/", "/index.html", "/tailwinds.css",
                        "/error", "/favicon.ico").permitAll()
                .anyRequest().authenticated()
        )
        .httpBasic(Customizer.withDefaults())
        .build();
```

- CSRF is disabled — this is a stateless JSON API authenticated with
  Basic auth, so CSRF tokens add no value and just complicate `curl`.
- Session creation is `STATELESS` — Spring Security won't create an
  `HttpSession` for authentication, matching how Basic auth is supposed
  to work.
- The static landing page and Tailwind asset are explicitly `permitAll`.
- Everything else, including `/api/**`, requires authentication.
- `httpBasic(Customizer.withDefaults())` enables HTTP Basic.

**`UserDetailsService`** — backed by an `InMemoryUserDetailsManager`
seeded from `SecurityUserProperties`:

```java
UserDetails user = User.withUsername(userProps.getName())
        .password(encoder.encode(userProps.getPassword()))
        .roles(userProps.getRoles().split("\\s*,\\s*"))
        .build();
return new InMemoryUserDetailsManager(user);
```

The credentials live in `application-security-basic.properties`:

```properties
app.security.user.name=admin
app.security.user.password=changeme
app.security.user.roles=USER
```

In real deployments you'd populate those from environment variables or a
secrets manager — never check them into `application.properties`.

**`PasswordEncoder`** — `BCryptPasswordEncoder`, the default-recommended
encoder. The plain-text password from properties is encoded once at
startup and the encoded form is what's stored.

**`WebSecurityCustomizer`** — bypasses the security filter chain entirely
for the swagger / OpenAPI endpoints:

```java
return web -> web.ignoring().requestMatchers(
        "/v3/api-docs", "/v3/api-docs/**", "/v3/api-docs.yaml",
        "/swagger-ui.html", "/swagger-ui/**", "/swagger-resources/**"
);
```

The distinction vs. `.permitAll()` matters:

- `.permitAll()` inside the filter chain — request still passes through
  every security filter, but authorization always allows it. The
  `SecurityContext` is populated, response headers (HSTS, frame
  options) are added.
- `WebSecurityCustomizer.ignoring(...)` — Spring Security's filter
  chain is *not installed* on these paths. No auth, no headers, no
  context. More efficient, but lose any security side effects.

For static API docs, ignoring is appropriate — you definitely don't
want to require auth to read API documentation, and you don't need
security headers on the JSON document.

### Trying both profiles

With `security-off` (the default):

```bash
./mvnw spring-boot:run
curl -X POST http://localhost:8080/api/hello \
     -H 'Content-Type: application/json' \
     -d '{"name":"Ada","id":1,"role":"ADMIN"}'
# → 200 with greeting JSON
```

With `security-basic`:

```bash
./mvnw spring-boot:run -Dspring-boot.run.profiles=security-basic

# No credentials → 401
curl -i -X POST http://localhost:8080/api/hello \
     -H 'Content-Type: application/json' \
     -d '{"name":"Ada","id":1,"role":"ADMIN"}'

# With credentials → 200
curl -u admin:changeme -X POST http://localhost:8080/api/hello \
     -H 'Content-Type: application/json' \
     -d '{"name":"Ada","id":1,"role":"ADMIN"}'

# Swagger still public → 200
curl http://localhost:8080/v3/api-docs
```

### Profile tests

`SecurityOffProfileIT` and `SecurityBasicProfileIT` are full-context
`@SpringBootTest` tests with `@ActiveProfiles("...")`.

`SecurityOffProfileIT` asserts:
- `POST /api/hello` succeeds without credentials
- Swagger and the 404 page are accessible without credentials
- No `SecurityFilterChain` bean is registered
- `SecurityOffConfig` is in the context, `SecurityBasicConfig` is not

`SecurityBasicProfileIT` asserts:
- `POST /api/hello` returns 401 with no credentials and 401 with bad ones
- `POST /api/hello` returns 200 with valid credentials (`admin/changeme`)
- `/v3/api-docs` is accessible without credentials (the
  `WebSecurityCustomizer` bypass works)
- `SecurityFilterChain` bean is registered
- `SecurityBasicConfig` is in the context, `SecurityOffConfig` is not
- `SecurityUserProperties` is populated from the profile properties

`HelloControllerTest` (the `@WebMvcTest` slice) gets
`@AutoConfigureMockMvc(addFilters = false)` so the now-on-classpath
security filter doesn't reject every request in the slice — the slice
isn't testing security, it's testing routing/validation/error mapping.

### What about the existing tests?

`@SpringBootTest`-based tests (`ApiApplicationTests`,
`GlobalControllerAdviceIT`, `AuditAspectIT`) all use the default profile,
which is `security-off`, so they continue to work unmodified — security
auto-config is excluded and no filter chain is in the way.

If you want one of them to run with security on, add
`@ActiveProfiles("security-basic")` and add credentials to every MockMvc
call (or use `.with(user("admin").roles("USER"))` from
`spring-security-test`).

---

## Extending: adding a database or Elasticsearch audit sink

The point of the `AuditSink` interface is that adding a new backend never
touches `AuditAspect`. Three steps:

**1. Implement the sink.**

```java
@Component
@ConditionalOnProperty(name = "audit.sinks.database.enabled", havingValue = "true")
@RequiredArgsConstructor
public class DatabaseAuditSink implements AuditSink {

    private final JdbcTemplate jdbc;

    @Override
    public void record(AuditEvent event) {
        jdbc.update("""
            INSERT INTO audit_event
                   (ts, type, class_name, method_name, args, result, exception_type, exception_message, duration_ms)
            VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?)
            """,
            Timestamp.from(event.timestamp()),
            event.type().name(),
            event.className(),
            event.methodName(),
            event.argsFormatted(),
            event.resultFormatted(),
            event.exceptionType(),
            event.exceptionMessage(),
            event.durationMs());
    }
}
```

**2. Add the dependency** (e.g. `spring-boot-starter-jdbc` plus a JDBC
driver) and configure the datasource via standard Spring Boot properties.

**3. Enable it in `application.properties`:**

```properties
audit.sinks.database.enabled=true
```

That's the entire change. The aspect now emits to *both* file and DB
(unless you also set `audit.sinks.file.enabled=false`). Same shape for
Elasticsearch — wrap an `ElasticsearchClient`, serialize the event as
JSON, gate with `audit.sinks.elasticsearch.enabled`.

The interface contract requires sinks to never throw; the aspect wraps
each `sink.record(...)` in a try/catch as a defensive backstop, so a
DB outage or Elasticsearch timeout will be logged but won't break the
audited request.
