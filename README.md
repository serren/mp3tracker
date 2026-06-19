# mp3tracker

Microservice application for uploading, storing, and retrieving MP3 files along with their metadata.

## Architecture

| Service | Port | Responsibility |
|---|---|---|
| **api-gateway** | **8080** | **Single entry point — JWT validation, routes all external traffic via Eureka** |
| resource-service | 8081 | MP3 upload/download/delete, S3 storage (STAGING→PERMANENT), RabbitMQ publisher |
| resource-processor | 8084 | Async MP3 metadata extraction via Apache Tika |
| song-service | 8082–8083 | Song metadata CRUD (name, artist, album, duration, year) — 2 replicas |
| storage-service | 8085 | CRUD for S3 storage configurations (STAGING / PERMANENT buckets) |
| service-registry | 8761 | Eureka service discovery |
| **config-server** | **8888** | **Centralized configuration (Spring Cloud Config, git-backed)** |
| **keycloak** | **8180** | **OAuth2/OIDC authorization server — issues and validates JWT tokens** |
| rabbitmq | 5672 / 15672 | Message broker (AMQP + Management UI) |
| localstack | 4566 | AWS S3 emulator |
| resource-db | 5442 | PostgreSQL — resource records |
| song-db | 5443 | PostgreSQL — song metadata |
| storage-db | 5444 | PostgreSQL — storage configurations |
| **prometheus** | **9090** | **Metrics collector — scrapes `/actuator/prometheus` on all 7 services** |
| **grafana** | **3000** | **Metrics dashboards (JVM Metrics, API Gateway Performance, Service Logs)** |
| **zipkin** | **9411** | **Distributed tracing UI — receives spans from all services** |
| elasticsearch | 9200 | Log storage (ELK stack) |
| logstash | 5000 | Log ingestion (TCP) — receives structured logs from all services |
| kibana | 5601 | Log search UI |
| mp3tracker-ui | 3001 | Browser UI (Nginx) |

## Security

All API requests must include a valid JWT token issued by Keycloak. The API Gateway is the single enforcement point — downstream services trust internal traffic.

### Roles

| Role | Allowed methods |
|------|----------------|
| `ADMIN` | GET, POST, DELETE |
| `USER` | GET only |

POST and DELETE on `/storages/**` are restricted to `ADMIN`. All other routes require any authenticated user.
`/actuator/**` is public (health checks, Prometheus scraping).

### Getting a token

**Admin token:**
```bash
curl -s -X POST http://localhost:8180/realms/mp3tracker/protocol/openid-connect/token \
  -d "client_id=mp3tracker-postman&grant_type=password&username=admin&password=admin" \
  | jq -r .access_token
```

**User token:**
```bash
curl -s -X POST http://localhost:8180/realms/mp3tracker/protocol/openid-connect/token \
  -d "client_id=mp3tracker-postman&grant_type=password&username=user&password=user" \
  | jq -r .access_token
```

Use the returned token as `Authorization: Bearer <token>` on every API request.

### Access matrix

| Request | No token | USER token | ADMIN token |
|---------|----------|------------|-------------|
| `GET /resources/**` | 401 | 200 | 200 |
| `GET /songs/**` | 401 | 200 | 200 |
| `GET /storages` | 401 | 200 | 200 |
| `POST /storages` | 401 | 403 | 201 |
| `DELETE /storages` | 401 | 403 | 200 |
| `GET /actuator/health` | 200 | 200 | 200 |

---

## Processing flow

```
Client  (with Bearer token)
  │
  │  POST /resources  (audio/mpeg)
  ▼
api-gateway (:8080)
  ├── validates JWT via Keycloak JWK endpoint
  │  routes /resources/** → lb://resource-service
  ▼
resource-service
  ├── GET /storages  →  storage-service  (fetch STAGING bucket config)
  ├── saves binary to S3 (mp3-staging bucket)
  ├── saves resource record to resource-db  (storageType=STAGING)
  └── publishes ResourceUploadedEvent {resourceId} to RabbitMQ
                                        │
                                        │  resources.queue
                                        ▼
                              resource-processor
                                  ├── GET /resources/{id}  →  resource-service (binary)
                                  ├── extracts ID3 metadata via Apache Tika
                                  ├── POST /songs  →  song-service (metadata)
                                  └── publishes ResourceProcessedEvent {resourceId} to RabbitMQ
                                                                        │
                                                                        │  resource.service.queue
                                                                        ▼
                                                              resource-service
                                                                  ├── GET /storages  →  storage-service  (fetch PERMANENT config)
                                                                  ├── copies binary  mp3-staging → mp3-permanent
                                                                  ├── updates resource record  (storageType=PERMANENT)
                                                                  └── deletes binary from mp3-staging
```

All synchronous HTTP calls between services include exponential-backoff retry (3 attempts, 1 s / 2 s / 4 s).
After 3 failed processing attempts the message is routed to `resources.queue.dlq` for manual inspection.

`StorageServiceClient.getAllStorages()` is additionally protected by a **Resilience4j circuit breaker** (see [Fault tolerance](#fault-tolerance)).

## Fault tolerance

`resource-service` calls `storage-service` to resolve S3 bucket configuration on every upload and every file promotion. If `storage-service` is unavailable, `resource-service` continues operating using hardcoded stub data (`mp3-staging` / `mp3-permanent`).

The protection is layered in `StorageServiceClient.getAllStorages()`:

| Layer | Mechanism | Behaviour |
|---|---|---|
| Timeout | `SimpleClientHttpRequestFactory` (5 s connect + 5 s read) | Prevents indefinite hang on a stopped service |
| Retry | `@Retryable` (3 attempts, exponential backoff 1 s → 2 s → 4 s) | Retries transient errors before escalating |
| Circuit Breaker | `@CircuitBreaker` — Resilience4j `storageService` instance | After ≥ 50 % failures in a 5-call window, opens the circuit for 10 s; subsequent calls go directly to the fallback |
| Fallback | `getAllStoragesFallback(Throwable)` | Returns hardcoded `STAGING / mp3-staging` and `PERMANENT / mp3-permanent` stub rows |

AOP order: CB aspect (order 1) is the **outer** wrapper; Retry aspect (order `MAX_VALUE-5`) is **inner**. This means one CB failure is recorded only after all retries are exhausted.

Circuit breaker YAML (`application.yml`):
```yaml
resilience4j:
  circuitbreaker:
    circuit-breaker-aspect-order: 1
    instances:
      storageService:
        slidingWindowType: COUNT_BASED
        slidingWindowSize: 5
        failureRateThreshold: 50        # opens after ≥3 failures in 5 calls
        waitDurationInOpenState: 10s
        permittedNumberOfCallsInHalfOpenState: 2
        automaticTransitionFromOpenToHalfOpenEnabled: true
```

---

## Startup order

Services start in dependency order managed by Docker Compose healthchecks:

```
PostgreSQL DBs  ──┐
RabbitMQ        ──┤
LocalStack      ──┤──► service-registry ──► config-server ──► resource-service
                                       │                  ├──► song-service
                                       │                  ├──► storage-service
                                       │                  └──► resource-processor
Keycloak ─────────────────────────────────────────────────────► api-gateway ◄── config-server
```

`resource-service` additionally waits for `storage-service: condition: service_healthy`.
`api-gateway` additionally waits for `keycloak: condition: service_healthy` — it fetches the JWK set at startup.

## Prerequisites

- Docker + Docker Compose v2
- JDK 21 (for local run)
- Maven 3.9+ (for local run)

## Running services

**Start all services** (build images if not built yet):
```bash
docker compose up -d --build
```

**Start without rebuilding images:**
```bash
docker compose up -d
```

**Stop all services** (volumes are preserved):
```bash
docker compose down
```

**Stop and wipe all data** (full reset):
```bash
docker compose down -v && docker compose up -d --build
```

**View logs of a specific service:**
```bash
docker compose logs -f resource-service
docker compose logs -f resource-processor
docker compose logs -f song-service
docker compose logs -f api-gateway
docker compose logs -f keycloak
docker compose logs -f config-server
```

## Verifying services

**Check container status:**
```bash
docker compose ps
```

All services should show `(healthy)`. Keycloak takes up to 3 minutes on first startup.

**Health endpoints:**
```bash
curl http://localhost:8080/actuator/health   # api-gateway (no token required)
curl http://localhost:8081/actuator/health   # resource-service
curl http://localhost:8082/actuator/health   # song-service
curl http://localhost:8084/actuator/health   # resource-processor
curl http://localhost:8085/actuator/health   # storage-service
curl http://localhost:8761/actuator/health   # service-registry
curl http://localhost:8888/actuator/health   # config-server
```

Expected response for each:
```json
{"status":"UP"}
```

**Eureka dashboard:**

Open http://localhost:8761 in a browser — `RESOURCE-SERVICE`, `SONG-SERVICE`, `RESOURCE-PROCESSOR`, `STORAGE-SERVICE`, `API-GATEWAY`, and `CONFIG-SERVER` should appear in the list of registered instances.

**Config Server — inspect loaded configuration:**
```bash
curl http://localhost:8888/resource-service/default
curl http://localhost:8888/api-gateway/default
```

**RabbitMQ Management UI:**

Open http://localhost:15672 in a browser (login: `guest` / `guest`).

---

## Observability

### Prometheus — http://localhost:9090

Scrapes metrics every 15 s from all seven application services via `/actuator/prometheus`:

| Scrape target | Internal address |
|---|---|
| resource-service | `resource-service:8080` |
| song-service | `song-service:8080` |
| storage-service | `storage-service:8080` |
| resource-processor | `resource-processor:8080` |
| api-gateway | `api-gateway:8080` |
| service-registry | `service-registry:8761` |
| config-server | `config-server:8888` |

Use the Prometheus expression browser to query raw metrics (e.g. `http_server_requests_seconds_count`, `jvm_memory_used_bytes`).

### Grafana — http://localhost:3000

Login: `admin` / value of `GF_ADMIN_PASSWORD` in `.env` (default: `admin`).

Three dashboards are provisioned automatically:

| Dashboard | Datasource | What it shows |
|---|---|---|
| **JVM Metrics** | Prometheus | Heap / non-heap memory, GC pause time, thread count — one panel per service |
| **API Gateway Performance** | Prometheus | Request rate (req/s) and 5xx error rate through the gateway |
| **Service Logs** | Elasticsearch | Structured log stream from all services (level, logger, message, traceId) |

Datasources are provisioned from `grafana/provisioning/datasources/` (Prometheus at `http://prometheus:9090`, Elasticsearch at the URL from `ELASTICSEARCH_HOSTS`).

### Zipkin — http://localhost:9411

Receives distributed traces from **all seven application services** via `ZIPKIN_URL` (set in `compose.yaml` for each service). Traces are sent automatically through Micrometer Tracing / Spring Cloud Sleuth.

Use the Zipkin UI to:
- Search traces by service, trace ID, or time range
- Inspect the full span tree for a request across multiple services (e.g. upload → resource-processor → song-service)
- Identify latency hotspots between service hops

Services that send traces: `api-gateway`, `resource-service`, `resource-processor`, `song-service`, `storage-service`, `service-registry`, `config-server`.

### ELK Stack

Structured JSON logs from all services are shipped via Logstash TCP appender (`LOGSTASH_URL`) to Elasticsearch and surfaced in Kibana (http://localhost:5601) and the **Service Logs** Grafana dashboard.

- **Exchanges** — `resources.direct` (main exchange for all resource events), `resources.dlx` (dead-letter)
- **Queues** — `resources.queue` (processor consumes), `resource.service.queue` (resource-service consumes processed events), `resources.queue.dlq` (dead-letter)

**Keycloak Admin Console:**

Open http://localhost:8180 in a browser (login from `.env`: `KC_ADMIN_USERNAME` / `KC_ADMIN_PASSWORD`). Realm `mp3tracker` contains roles `ADMIN` / `USER` and test users `admin` / `user`.

## Inspecting LocalStack S3 data

Three buckets are provisioned at startup: `mp3-resources` (legacy), `mp3-staging`, `mp3-permanent`.

**List all buckets:**
```bash
docker exec localstack awslocal s3 ls
```

**List objects per bucket:**
```bash
docker exec localstack awslocal s3 ls s3://mp3-staging
docker exec localstack awslocal s3 ls s3://mp3-permanent
```

After a successful upload the MP3 appears in `mp3-staging`. After resource-processor finishes and resource-service receives the processed event, the file moves to `mp3-permanent` and is removed from `mp3-staging`.

---

## Running services locally (hybrid mode)

Run all services on the host JVM while keeping only the infrastructure (DBs, RabbitMQ, LocalStack) in Docker. Each service has an `application-local.yml` with the correct port and settings for this mode.

In local mode the api-gateway uses `http://localhost:8180` as the default Keycloak JWK URI (set in `api-gateway.yml` in the config repo). Start Keycloak in Docker or override `KEYCLOAK_JWK_SET_URI`.

**Step 1 — start infra:**
```bash
docker compose up -d resource-db song-db storage-db rabbitmq localstack keycloak
```

**Step 2 — set the config-repo path** (forward slashes required on Windows):
```bash
export CONFIG_REPO_PATH=D:/Projects/mp3tracker-config
```

**Step 3 — start services in order** (each in its own terminal):
```bash
cd service-registry  && mvn spring-boot:run                                     # :8761
cd config-server     && mvn spring-boot:run -Dspring-boot.run.profiles=local    # :8888
cd storage-service   && mvn spring-boot:run -Dspring-boot.run.profiles=local    # :8085
cd resource-service  && mvn spring-boot:run -Dspring-boot.run.profiles=local    # :8081
cd song-service      && mvn spring-boot:run -Dspring-boot.run.profiles=local    # :8082
cd resource-processor && mvn spring-boot:run -Dspring-boot.run.profiles=local   # :8084
cd api-gateway       && mvn spring-boot:run -Dspring-boot.run.profiles=local    # :8080
```

config-server must be healthy before starting the app services. storage-service must be up before resource-service (Eureka lookup on first upload).

**Verify config-server is serving config:**
```bash
curl http://localhost:8888/resource-service/default
# propertySources should be non-empty
```

---

## REST API examples

All examples use the **API Gateway** on port **8080** as the single entry point and require a Bearer token.
Direct service ports (8081, 8082, 8085) remain accessible but bypass security and are intended for internal/debug use only.

**Get a token first** (see [Getting a token](#getting-a-token) above), then export it:
```bash
TOKEN=$(curl -s -X POST http://localhost:8180/realms/mp3tracker/protocol/openid-connect/token \
  -d "client_id=mp3tracker-postman&grant_type=password&username=admin&password=admin" \
  | jq -r .access_token)
```

### resource-service (via gateway)

**Upload an MP3 file:**
```bash
curl -X POST http://localhost:8080/resources \
  -H "Content-Type: audio/mpeg" \
  -H "Authorization: Bearer $TOKEN" \
  --data-binary @your-file.mp3
```
Response:
```json
{"id": 1}
```

After a successful upload, resource-processor asynchronously extracts the metadata and saves it to song-service. Check `docker compose logs -f resource-processor` to confirm processing, then query `GET /songs/1` to verify the result.

**Download an MP3 file by ID:**
```bash
curl http://localhost:8080/resources/1 \
  -H "Authorization: Bearer $TOKEN" \
  --output downloaded.mp3
```

**Delete resources by ID (comma-separated):**
```bash
curl -X DELETE "http://localhost:8080/resources?id=1,2,3" \
  -H "Authorization: Bearer $TOKEN"
```
Response:
```json
{"ids": [1, 2, 3]}
```

---

### storage-service (via gateway)

Storage-service is accessible through the API Gateway at `:8080/storages` (requires auth) and directly at `:8085/storages` (no auth, internal use only).

**List all storage configurations:**
```bash
curl http://localhost:8080/storages \
  -H "Authorization: Bearer $TOKEN"
```
Response:
```json
[
  {"id": 1, "storageType": "STAGING",   "bucket": "mp3-staging",   "path": ""},
  {"id": 2, "storageType": "PERMANENT", "bucket": "mp3-permanent", "path": ""}
]
```

**Create a storage configuration (ADMIN only):**
```bash
curl -X POST http://localhost:8080/storages \
  -H "Content-Type: application/json" \
  -H "Authorization: Bearer $TOKEN" \
  -d '{"storageType": "STAGING", "bucket": "mp3-staging", "path": ""}'
```
Response:
```json
{"id": 1}
```

**Delete storage configurations by ID (ADMIN only):**
```bash
curl -X DELETE "http://localhost:8080/storages?id=1,2" \
  -H "Authorization: Bearer $TOKEN"
```
Response:
```json
{"ids": [1, 2]}
```

---

### song-service (via gateway)

**Get song metadata by ID:**
```bash
curl http://localhost:8080/songs/1 \
  -H "Authorization: Bearer $TOKEN"
```
Response:
```json
{
  "id": 1,
  "name": "Bohemian Rhapsody",
  "artist": "Queen",
  "album": "A Night at the Opera",
  "duration": "05:55",
  "year": "1975"
}
```

**Delete song metadata by ID (comma-separated):**
```bash
curl -X DELETE "http://localhost:8080/songs?id=1,2" \
  -H "Authorization: Bearer $TOKEN"
```
Response:
```json
{"ids": [1, 2]}
```

---

### Error responses

**401 Unauthorized** — missing or expired token:
```json
{"status": 401, "error": "Unauthorized", "message": "Authentication required"}
```

**403 Forbidden** — valid token but insufficient role (e.g. USER attempting POST/DELETE):
```json
{"status": 403, "error": "Forbidden", "message": "Access denied"}
```

All services return a consistent error shape for application errors:
```json
{
  "errorMessage": "Song metadata for ID=99 not found",
  "errorCode": "404"
}
```

Validation errors (song-service `POST /songs`) include a `details` field with per-field messages:
```json
{
  "errorMessage": "Validation error",
  "details": {
    "duration": "Duration must be in mm:ss format with leading zeros",
    "year": "Year must be between 1900 and 2099"
  },
  "errorCode": "400"
}
```

---

## Dynamic configuration refresh

Configuration for `resource-service`, `song-service`, and `api-gateway` is served from `config-repo/` via Config Server.
To change a property at runtime without restarting:

```bash
# 1. Edit the config file in config-repo/
# 2. Commit the change
git -C config-repo commit -am "change description"

# 3. Trigger refresh on the target service
curl -X POST http://localhost:8081/actuator/refresh   # resource-service
curl -X POST http://localhost:8080/actuator/refresh   # api-gateway (no token needed for actuator)

# Response — list of changed properties:
# ["logging.level.com.example"]
```

Note: `jwk-set-uri` is not a `@RefreshScope` bean — changing it requires an api-gateway restart.

---

## Troubleshooting

| Symptom | Likely cause | Fix |
|---|---|---|
| Container stuck in `health: starting` | Service slow to boot (JVM warmup + Eureka registration) | Wait up to 2–3 minutes; check `docker compose logs -f <service>` |
| `resource-service` / `song-service` won't start | `config-server` not healthy yet | `docker compose ps` — wait for `config-server (healthy)` |
| `api-gateway` won't start | `keycloak` not healthy yet | Wait for Keycloak (~3 min on first start); check `docker compose logs -f keycloak` |
| `Connection refused` on DB | DB container not healthy | `docker compose ps` — wait for DB healthcheck to pass |
| `Connection refused` to `song-service` | Not yet registered in Eureka | Wait ~30 s for registration; `@Retryable` retries automatically |
| `Invalid file format` on upload | Missing `Content-Type` header | Set `-H "Content-Type: audio/mpeg"` |
| All API requests return 401 | Missing or expired Bearer token | Get a new token from Keycloak (see [Getting a token](#getting-a-token)) |
| Token request returns `Account is not fully set up` | Keycloak started with stale realm data | `docker compose stop keycloak && docker compose rm -f keycloak && docker compose up -d keycloak` |
| POST/DELETE returns 403 with valid token | User has `USER` role, not `ADMIN` | Use the `admin` / `admin` credentials to get an ADMIN token |
| Upload succeeds but `GET /songs/{id}` returns 404 | `resource-processor` not running or still processing | `docker compose logs -f resource-processor`; check RabbitMQ queue |
| API Gateway returns 503 | Target service not registered in Eureka yet | Wait for Eureka registration; retry the request |
| API Gateway returns HTML error instead of JSON | Route not matched | Ensure path starts with `/resources/`, `/songs/`, or `/storages/` |
| Message stuck in `resources.queue.dlq` | Processing failed after 3 retries | Inspect DLQ in RabbitMQ UI (http://localhost:15672); fix root cause; move message back to `resources.queue` |
| File never moves to PERMANENT storage | `resource.service.queue` not receiving events | Check `resource-processor` published `ResourceProcessedEvent`; verify queue binding in RabbitMQ UI |
| Upload returns 500 when `storage-service` is stopped | Docker image not rebuilt after code change | `docker compose up -d --build resource-service` |
| Upload hangs for 20+ seconds when `storage-service` is unreachable | Old image without 5 s timeout | Rebuild resource-service image |
| `GET /storages` returns 404 or empty | Storage seed data not inserted | `docker compose down -v && docker compose up -d --build` to re-run `init-scripts/storage-db/init.sql` |
| `ddl-auto: none` — table missing | DB recreated but init SQL not run | `docker compose down -v && docker compose up -d --build` |
| Config Server shows stale values | Git commit not made after editing config file | `git -C config-repo commit -am "..."` then call `/actuator/refresh` |
| config-server `propertySources: []` (local run) | `CONFIG_REPO_PATH` not set or uses backslashes | `export CONFIG_REPO_PATH=D:/Projects/mp3tracker-config` (forward slashes) |
| `Could not resolve placeholder 'rabbitmq.exchange'` (local run) | config-server not serving properties | Check `curl http://localhost:8888/resource-service/default`; fix `CONFIG_REPO_PATH` |
| `No instances of X registered in Eureka` | Service not yet registered | Wait; `@Retryable` handles this automatically |
| Port conflict on 8080/8180 | Another process uses API Gateway or Keycloak port | Stop conflicting process or change port in `compose.yaml` |
| Build fails: Java version | Wrong JDK active | Ensure JDK 21: `java -version` |
