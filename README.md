# mp3tracker

Microservice application for uploading, storing, and retrieving MP3 files along with their metadata.

## Architecture

| Service | Port | Responsibility |
|---|---|---|
| **api-gateway** | **8080** | **Single entry point — routes all external traffic via Eureka** |
| resource-service | 8081 | MP3 upload/download/delete, binary storage in S3 |
| resource-processor | 8084 | Async MP3 metadata extraction via Apache Tika |
| song-service | 8082–8083 | Song metadata CRUD (name, artist, album, duration, year) — 2 replicas |
| service-registry | 8761 | Eureka service discovery |
| **config-server** | **8888** | **Centralized configuration (Spring Cloud Config, git-backed)** |
| rabbitmq | 5672 / 15672 | Message broker (AMQP + Management UI) |
| localstack | 4566 | AWS S3 emulator |
| resource-db | 5442 | PostgreSQL — resource records |
| song-db | 5443 | PostgreSQL — song metadata |

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
  ├── saves binary to S3
  ├── saves resource record to resource-db
  └── publishes ResourceUploadedEvent {resourceId} to RabbitMQ
                                        │
                                        │  resources.queue
                                        ▼
                              resource-processor
                                  ├── GET /resources/{id}  →  resource-service (binary)
                                  ├── extracts ID3 metadata via Apache Tika
                                  └── POST /songs  →  song-service (metadata)
```

All synchronous HTTP calls include exponential-backoff retry (3 attempts, 1 s / 2 s / 4 s).
After 3 failed processing attempts the message is routed to `resources.queue.dlq` for manual inspection.

## Startup order

Services start in dependency order managed by Docker Compose healthchecks:

```
PostgreSQL DBs  ──┐
RabbitMQ        ──┤
LocalStack      ──┤──► service-registry ──► config-server ──► resource-service
                                       │                  └──► song-service
                                       └──────────────────────► api-gateway
                                       └──────────────────────► resource-processor
```

## Prerequisites

- Docker
- Docker Compose v2

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
curl http://localhost:8761/actuator/health   # service-registry
curl http://localhost:8888/actuator/health   # config-server
```

Expected response for each:
```json
{"status":"UP"}
```

**Eureka dashboard:**

Open http://localhost:8761 in a browser — `RESOURCE-SERVICE`, `SONG-SERVICE`, `RESOURCE-PROCESSOR`, `API-GATEWAY`, and `CONFIG-SERVER` should appear in the list of registered instances.

**Config Server — inspect loaded configuration:**
```bash
curl http://localhost:8888/resource-service/default
curl http://localhost:8888/song-service/default
```

**RabbitMQ Management UI:**

Open http://localhost:15672 in a browser (login: `guest` / `guest`).

- **Exchanges** — `resources.direct` (publisher), `resources.dlx` (dead-letter)
- **Queues** — `resources.queue` (active), `resources.queue.dlq` (dead-letter)

## Inspecting LocalStack S3 data

**List objects in the bucket (via Docker):**
```bash
docker exec localstack awslocal s3 ls s3://mp3-resources
```

**List with size and date:**
```bash
docker exec localstack awslocal s3 ls s3://mp3-resources --human-readable
```

**Browse via browser (XML listing):**
```
http://localhost:4566/mp3-resources
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
| `ddl-auto: none` — table missing | DB recreated but init SQL not run | `docker compose down -v && docker compose up -d --build` |
| Config Server shows stale values | Git commit not made after editing config file | `git -C config-repo commit -am "..."` then call `/actuator/refresh` |
| `No instances of X registered in Eureka` | Service not yet registered | Wait; `@Retryable` handles this automatically |
| Port conflict on 8080 | Another process uses the API Gateway port | Stop conflicting process or change port in `compose.yaml` |
| Build fails: Java version | Wrong JDK active | Ensure JDK 21: `java -version` |
