# mp3tracker

Microservice application for uploading, storing, and retrieving MP3 files along with their metadata.

## Architecture

| Service | Port | Responsibility |
|---|---|---|
| resource-service | 8081 | MP3 upload/download/delete, binary storage in S3 |
| song-service | 8082–8083 | Song metadata CRUD (name, artist, album, duration, year) |
| resource-processor | 8084 | MP3 metadata extraction via Apache Tika |
| service-registry | 8761 | Eureka service discovery |
| localstack | 4566 | AWS S3 emulator |
| resource-db | 5442 | PostgreSQL — resource records |
| song-db | 5443 | PostgreSQL — song metadata |

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
docker compose logs -f song-service
docker compose logs -f resource-processor
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

## REST API examples

### resource-service

**Upload an MP3 file:**
```bash
curl -X POST http://localhost:8081/resources \
  -H "Content-Type: audio/mpeg" \
  --data-binary @sample-mp3-file/your-file.mp3
```
Response:
```json
{"id": 1}
```

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

**Create song metadata:**
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
