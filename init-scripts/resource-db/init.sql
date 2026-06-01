CREATE TABLE IF NOT EXISTS resources
(
    id           BIGSERIAL    PRIMARY KEY,
    s3_key       VARCHAR(255) NOT NULL,
    storage_type VARCHAR(20)  NOT NULL DEFAULT 'STAGING'
);
