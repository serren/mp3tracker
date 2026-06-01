# mp3tracker

Microservice application for uploading, storing, and retrieving MP3 files along with their metadata.

## Architecture

| Service | Port | Responsibility |
|---|---|---|
| **api-gateway** | **8080** | **Single entry point — routes all external traffic via Eureka** |
| resource-service | 8081 | MP3 upload/download/delete, S3 storage (STAGING→PERMANENT), RabbitMQ publisher |
| resource-processor | 8084 | Async MP3 metadata extraction via Apache Tika |
| song-service | 8082–8083 | Song metadata CRUD (name, artist, album, duration, year) — 2 replicas |
| storage-service | 8085 | CRUD for S3 storage configurations (STAGING / PERMANENT buckets) |
| service-registry | 8761 | Eureka service discovery |
| **config-server** | **8888** | **Centralized configuration (Spring Cloud Config, git-backed)** |
| rabbitmq | 5672 / 15672 | Message broker (AMQP + Management UI) |
| localstack | 4566 | AWS S3 emulator |
| resource-db | 5442 | PostgreSQL — resource records |
| song-db | 5443 | PostgreSQL — song metadata |
| storage-db | 5444 | PostgreSQL — storage configurations |

## Processing flow

```
Client
  │
  │  POST /resources  (audio/mpeg)
  ▼
api-gateway (:8080)
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

All synchronous HTTP calls include exponential-backoff retry (3 attempts, 1 s / 2 s / 4 s).
After 3 failed processing attempts the message is routed to `resources.queue.dlq` for manual inspection.

## Startup order

Services start in dependency order managed by Docker Compose healthchecks:

```
PostgreSQL DBs  ──┐
RabbitMQ        ──┤
LocalStack      ──┤──► service-registry ──► config-server ──► resource-service
                                       │                  ├──► song-service
                                       │                  ├──► storage-service
                                       └──────────────────────► api-gateway
                                       └──────────────────────► resource-processor
```

`resource-service` additionally waits for `storage-service: condition: service_healthy`.

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
docker compose logs -f config-server
```

## Verifying services

**Check container status:**
```bash
docker compose ps
```

All services should show `(healthy)`.

**Health endpoints:**
```bash
curl http://localhost:8080/actuator/health   # api-gateway
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
curl http://localhost:8888/song-service/default
```

**RabbitMQ Management UI:**

Open http://localhost:15672 in a browser (login: `guest` / `guest`).

- **Exchanges** — `resources.direct` (main exchange for all resource events), `resources.dlx` (dead-letter)
- **Queues** — `resources.queue` (processor consumes), `resource.service.queue` (resource-service consumes processed events), `resources.queue.dlq` (dead-letter)

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

**Step 1 — start infra:**
```bash
docker compose up -d resource-db song-db storage-db rabbitmq localstack
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

All examples use the **API Gateway** on port **8080** as the single entry point.
Direct service ports (8081, 8082) remain accessible but are intended for internal/debug use only.

### resource-service (via gateway)

**Upload an MP3 file:**
```bash
curl -X POST http://localhost:8080/resources \
  -H "Content-Type: audio/mpeg" \
  --data-binary @your-file.mp3
```
Response:
```json
{"id": 1}
```

After a successful upload, resource-processor asynchronously extracts the metadata and saves it to song-service. Check `docker compose logs -f resource-processor` to confirm processing, then query `GET /songs/1` to verify the result.

**Download an MP3 file by ID:**
```bash
curl http://localhost:8080/resources/1 --output downloaded.mp3
```

**Delete resources by ID (comma-separated):**
```bash
curl -X DELETE "http://localhost:8080/resources?id=1,2,3"
```
Response:
```json
{"ids": [1, 2, 3]}
```

---

### storage-service (direct, port 8085)

Storage-service is not routed through the API Gateway — it is an internal service consumed by resource-service via Eureka.

**List all storage configurations:**
```bash
curl http://localhost:8085/storages
```
Response:
```json
[
  {"id": 1, "storageType": "STAGING",   "bucket": "mp3-staging",   "path": ""},
  {"id": 2, "storageType": "PERMANENT", "bucket": "mp3-permanent", "path": ""}
]
```

**Create a storage configuration:**
```bash
curl -X POST http://localhost:8085/storages \
  -H "Content-Type: application/json" \
  -d '{"storageType": "STAGING", "bucket": "mp3-staging", "path": ""}'
```
Response:
```json
{"id": 1}
```

**Delete storage configurations by ID:**
```bash
curl -X DELETE "http://localhost:8085/storages?id=1,2"
```
Response:
```json
{"ids": [1, 2]}
```

---

### song-service (via gateway)

**Get song metadata by ID:**
```bash
curl http://localhost:8080/songs/1
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

**Create song metadata manually:**
```bash
curl -X POST http://localhost:8080/songs \
  -H "Content-Type: application/json" \
  -d '{
    "id": 1,
    "name": "Bohemian Rhapsody",
    "artist": "Queen",
    "album": "A Night at the Opera",
    "duration": "05:55",
    "year": "1975"
  }'
```
Response:
```json
{"id": 1}
```

**Delete song metadata by ID (comma-separated):**
```bash
curl -X DELETE "http://localhost:8080/songs?id=1,2"
```
Response:
```json
{"ids": [1, 2]}
```

---

### Error responses

All services return a consistent error shape:
```json
{
  "errorMessage": "Song metadata for ID=99 not found",
  "errorCode": "404"
}
```

The API Gateway returns JSON errors for undefined routes or unreachable services:
```json
{
  "status": 404,
  "error": "Not Found",
  "message": "No route found for ..."
}
```

Validation errors (song-service `POST /songs`) include a `details` field with per-field messages:
```bash
curl -X POST http://localhost:8080/songs \
  -H "Content-Type: application/json" \
  -d '{
    "id": 102,
    "name": "We are the champions",
    "artist": "Queen",
    "album": "News of the world",
    "duration": "02:77",
    "year": "01977"
  }'
```
Response `400`:
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

Configuration for `resource-service` and `song-service` is served from `config-repo/` via Config Server.
To change a property at runtime without restarting:

```bash
# 1. Edit the config file
#    e.g. change logging.level.com.example from INFO to DEBUG
#    in config-repo/resource-service.yml

# 2. Commit the change
git -C config-repo commit -am "enable debug logging"

# 3. Trigger refresh on the target service
curl -X POST http://localhost:8081/actuator/refresh

# Response — list of changed properties:
# ["logging.level.com.example"]
```

---

## Troubleshooting

| Symptom | Likely cause | Fix |
|---|---|---|
| Container stuck in `health: starting` | Service slow to boot (JVM warmup + Eureka registration) | Wait up to 2 minutes; check `docker compose logs -f <service>` |
| `resource-service` / `song-service` won't start | `config-server` not healthy yet | `docker compose ps` — wait for `config-server (healthy)` |
| `Connection refused` on DB | DB container not healthy | `docker compose ps` — wait for DB healthcheck to pass |
| `Connection refused` to `song-service` | Not yet registered in Eureka | Wait ~30 s for registration; `@Retryable` retries automatically |
| `Invalid file format` on upload | Missing `Content-Type` header | Set `-H "Content-Type: audio/mpeg"` |
| Upload succeeds but `GET /songs/{id}` returns 404 | `resource-processor` not running or still processing | `docker compose logs -f resource-processor`; check RabbitMQ queue |
| API Gateway returns 503 | Target service not registered in Eureka yet | Wait for Eureka registration; retry the request |
| API Gateway returns HTML error instead of JSON | Route not matched | Ensure path starts with `/resources/` or `/songs/` |
| Message stuck in `resources.queue.dlq` | Processing failed after 3 retries | Inspect DLQ in RabbitMQ UI (http://localhost:15672); fix root cause; move message back to `resources.queue` |
| File never moves to PERMANENT storage | `resource.service.queue` not receiving events | Check `resource-processor` published `ResourceProcessedEvent`; verify queue binding in RabbitMQ UI |
| `resource-service` fails to start with storage-service lookup error | `storage-service` not healthy yet | Ensure `storage-service (healthy)` in `docker compose ps` before resource-service starts |
| `GET /storages` returns 404 or empty | Storage seed data not inserted | `docker compose down -v && docker compose up -d --build` to re-run `init-scripts/storage-db/init.sql` |
| `ddl-auto: none` — table missing | DB recreated but init SQL not run | `docker compose down -v && docker compose up -d --build` |
| Config Server shows stale values | Git commit not made after editing config file | `git -C config-repo commit -am "..."` then call `/actuator/refresh` |
| config-server `propertySources: []` (local run) | `CONFIG_REPO_PATH` not set or uses backslashes | `export CONFIG_REPO_PATH=D:/Projects/mp3tracker-config` (forward slashes) |
| `Could not resolve placeholder 'rabbitmq.exchange'` (local run) | config-server not serving properties | Check `curl http://localhost:8888/resource-service/default`; fix `CONFIG_REPO_PATH` |
| config-server fails: "Invalid config server configuration" | `local` profile replaced `native` profile | Ensure `spring.profiles.group.local: "native"` in `config-server/application.yml` |
| `No instances of X registered in Eureka` | Service not yet registered | Wait; `@Retryable` handles this automatically |
| Port conflict on 8080/8085 | Another process uses API Gateway or storage-service port | Stop conflicting process or change port in `compose.yaml` |
| Build fails: Java version | Wrong JDK active | Ensure JDK 21: `java -version` |
