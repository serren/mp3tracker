# mp3tracker

Microservice application for uploading, storing, and retrieving MP3 files along with their metadata.

## Architecture

| Service | Port | Responsibility |
|---|---|---|
| resource-service | 8081 | MP3 upload/download/delete, binary storage in S3 |
| resource-processor | 8084 | Async MP3 metadata extraction via Apache Tika |
| song-service | 8082–8083 | Song metadata CRUD (name, artist, album, duration, year) |
| service-registry | 8761 | Eureka service discovery |
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
```

## Verifying services

**Check container status:**
```bash
docker compose ps
```

All services should show `healthy` or `running`.

**Health endpoints:**
```bash
curl http://localhost:8081/actuator/health   # resource-service
curl http://localhost:8082/actuator/health   # song-service
curl http://localhost:8084/actuator/health   # resource-processor
curl http://localhost:8761/actuator/health   # service-registry
```

Expected response for each:
```json
{"status":"UP"}
```

**Eureka dashboard:**

Open http://localhost:8761 in a browser — `RESOURCE-SERVICE`, `SONG-SERVICE`, and `RESOURCE-PROCESSOR` should appear in the list of registered instances.

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

### resource-service

**Upload an MP3 file:**
```bash
curl -X POST http://localhost:8081/resources \
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
curl http://localhost:8081/resources/1 --output downloaded.mp3
```

**Delete resources by ID (comma-separated):**
```bash
curl -X DELETE "http://localhost:8081/resources?id=1,2,3"
```
Response:
```json
{"ids": [1, 2, 3]}
```

---

### song-service

**Get song metadata by ID:**
```bash
curl http://localhost:8082/songs/1
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
curl -X POST http://localhost:8082/songs \
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
curl -X DELETE "http://localhost:8082/songs?id=1,2"
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

Validation errors (song-service `POST /songs`) include a `details` field with per-field messages.

Request with invalid `duration` and `year`:
```bash
curl -X POST http://localhost:8082/songs \
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
