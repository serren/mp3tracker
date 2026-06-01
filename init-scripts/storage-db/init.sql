CREATE TABLE IF NOT EXISTS storages
(
    id           BIGSERIAL    PRIMARY KEY,
    storage_type VARCHAR(20)  NOT NULL UNIQUE,
    bucket       VARCHAR(255) NOT NULL,
    path         VARCHAR(255) NOT NULL
);

INSERT INTO storages (storage_type, bucket, path)
VALUES ('STAGING', 'mp3-staging', '')
ON CONFLICT (storage_type) DO NOTHING;

INSERT INTO storages (storage_type, bucket, path)
VALUES ('PERMANENT', 'mp3-permanent', '')
ON CONFLICT (storage_type) DO NOTHING;
