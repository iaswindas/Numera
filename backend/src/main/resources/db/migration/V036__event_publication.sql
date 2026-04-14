-- V036: Spring Modulith Event Publication table
-- Required by spring-modulith-starter-jpa for persistent event publishing

CREATE TABLE IF NOT EXISTS event_publication (
    id               UUID PRIMARY KEY,
    listener_id      TEXT NOT NULL,
    event_type       TEXT NOT NULL,
    serialized_event TEXT NOT NULL,
    publication_date TIMESTAMPTZ NOT NULL,
    completion_date  TIMESTAMPTZ
);

CREATE INDEX idx_event_publication_incomplete
    ON event_publication (event_type, listener_id)
    WHERE completion_date IS NULL;
