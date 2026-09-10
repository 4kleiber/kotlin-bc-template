-- Append-only source of truth: rows are only ever inserted, never updated or deleted.
CREATE TABLE notes_events
(
    id           BIGSERIAL PRIMARY KEY,
    aggregate_id UUID                     NOT NULL,
    version      BIGINT                   NOT NULL,
    event_type   VARCHAR(100)             NOT NULL,
    payload      TEXT                     NOT NULL,
    occurred_at  TIMESTAMP WITH TIME ZONE NOT NULL,
    UNIQUE (aggregate_id, version)
);

CREATE INDEX notes_events_aggregate_id_idx ON notes_events (aggregate_id);

-- Derived, disposable read model: everything here is folded from notes_events and can be
-- dropped and rebuilt at any time (see NoteProjectionRebuilder). It exists purely so reads
-- don't have to replay the whole event log per request.
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
