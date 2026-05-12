# Testing Strategy

## Philosophy

No single test type provides sufficient confidence alone. Each type addresses a distinct
confidence boundary. Together they form a pyramid: many fast unit tests at the base,
fewer slow end-to-end tests at the top.

---

## Test Pyramid Allocation

| Type        | Scope                               | Framework                                  | Count |
|-------------|-------------------------------------|--------------------------------------------|-------|
| Unit        | Single class, all deps mocked       | JUnit 5 + Mockito                          | ~15   |
| Integration | Spring slice + real DB (H2)         | `@DataJpaTest` / `@SpringBootTest` + MockMvc | ~10   |
| Component   | Business scenario, one service      | Cucumber 7 + `@SpringBootTest` + MockMvc   | ~4    |
| Contract    | Cross-service API boundary          | Spring Cloud Contract 4.2.x                | ~5    |
| E2E         | Full API lifecycle via HTTP         | Cucumber 7 + `@SpringBootTest(RANDOM_PORT)`| ~3    |

---

## Why This Combination?

**Unit tests** provide the fastest feedback on business logic. Every branch in the service
layer (invalid input, entity not found, duplicate creation) is exercised with mocked
dependencies. No Spring context is started.

**Integration tests** verify that the full Spring MVC stack, Bean Validation, and
GlobalExceptionHandler work together correctly. `@DataJpaTest` verifies that JPA entity
mappings and schema SQL are consistent. `@SpringBootTest + MockMvc` verifies HTTP request/
response shapes including error bodies.

**Component tests** (Cucumber) express business rules in natural language. They run against
a full Spring Boot context with an in-memory database, making scenarios human-readable and
independently auditable by non-developers.

**Contract tests** (Spring Cloud Contract) prevent API drift between services. `song-service`
is the HTTP producer: its contracts are the source of truth. `resource-processor` and
`resource-service` are consumers that verify their HTTP calls match the published stubs.
`resource-service` also acts as a messaging producer: its contract documents the
`ResourceUploadedEvent` message shape consumed by `resource-processor`.

**E2E tests** (Cucumber) drive the actual HTTP server (random port) and verify the complete
request/response lifecycle including serialization, deserialization, and HTTP status codes.
They exercise scenarios that span controller → service → repository.

---

## Infrastructure Choices

### Database
- **H2 in-memory** (PostgreSQL compatibility mode) for all unit/integration/component/E2E
  tests. The schema is simple (no PostgreSQL-specific types); H2's compatibility mode handles
  `BIGINT PRIMARY KEY` and `IDENTITY` columns identically.
- Schema initialised via `src/test/resources/schema.sql` (Spring auto-runs it before tests).

### Eureka / Service Discovery
All test `application-test.yml` files disable Eureka to eliminate the 30-second connection
timeout on startup:
```yaml
eureka:
  client:
    enabled: false
spring:
  cloud:
    discovery:
      enabled: false
```

### RabbitMQ
- Messaging contract test in `resource-service` uses `@MockBean RabbitTemplate` — the
  contract documents the message structure; no real broker is needed to verify it.

### S3
- `ResourceService` unit tests mock `S3StorageService` entirely — no AWS SDK or LocalStack
  dependency in unit tests.

---

## Contract Testing: Producer / Consumer Assignment

| Direction            | Producer         | Consumer(s)                          | Contract type |
|----------------------|------------------|--------------------------------------|---------------|
| POST /songs          | song-service     | resource-processor, resource-service | HTTP          |
| GET /songs/{id}      | song-service     | (read path)                          | HTTP          |
| DELETE /songs?id=... | song-service     | resource-service                     | HTTP          |
| ResourceUploadedEvent | resource-service | resource-processor                   | Messaging     |

Stubs are installed to the local Maven repository by `mvn verify` in `song-service`.
Consumers load them via `@AutoConfigureStubRunner(stubsMode = LOCAL)`.

---

## Execution Order (CI)

1. `cd song-service && mvn verify` — unit + integration + component + contract tests; installs stubs jar
2. `cd resource-processor && mvn test` — unit test; contract consumer test (reads song-service stubs)
3. `cd resource-service && mvn verify` — unit + messaging contract tests
