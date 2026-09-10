-- The single, system-wide event log shared by every bounded context: rows are only ever
-- inserted, never updated or deleted. One global `id` sequence orders every event any
-- bounded context has ever appended, in the exact order it was created.
CREATE TABLE events
(
    id             BIGSERIAL PRIMARY KEY,
    aggregate_type VARCHAR(100)             NOT NULL,
    aggregate_id   UUID                     NOT NULL,
    version        BIGINT                   NOT NULL,
    event_type     VARCHAR(100)             NOT NULL,
    payload        TEXT                     NOT NULL,
    occurred_at    TIMESTAMP WITH TIME ZONE NOT NULL,
    UNIQUE (aggregate_type, aggregate_id, version)
);

CREATE INDEX events_aggregate_idx ON events (aggregate_type, aggregate_id);

-- Derived, disposable read model for the "notes" bounded context: everything here is folded
-- from events (aggregate_type = 'Note') and can be dropped and rebuilt at any time (see
-- NoteProjectionRebuilder). It exists purely so reads don't have to replay the event log.
CREATE TABLE notes_projection
(
    id           UUID PRIMARY KEY,
    title        VARCHAR(255)             NOT NULL,
    body         TEXT                     NOT NULL,
    status       VARCHAR(20)              NOT NULL,
    created_at   TIMESTAMP WITH TIME ZONE NOT NULL,
    published_at TIMESTAMP WITH TIME ZONE,
    version      BIGINT                   NOT NULL
);
