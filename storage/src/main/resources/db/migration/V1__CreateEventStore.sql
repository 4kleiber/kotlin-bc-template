-- The single, system-wide event log shared by every bounded context: rows are only ever
-- inserted, never updated or deleted. Every bounded context's current state (e.g. a Note) is
-- computed on the fly by replaying its rows here — there is no persisted read model to keep
-- in sync.
--
-- `id` uses Postgres 18's native uuidv7() (roughly time-ordered) as a stable row identifier.
-- `sequence_number` is a separate, purely-additive BIGSERIAL counter giving the *exact*
-- global order every event was ever created in, across every tenant, stream, and bounded
-- context — useful precisely because a UUID (even v7) is not a strict guarantee of order the
-- way a database sequence is.
CREATE TABLE events
(
    id              UUID PRIMARY KEY         DEFAULT uuidv7(),
    tenant_id       UUID                     NOT NULL,
    stream_id       UUID                     NOT NULL,
    stream_type     VARCHAR(255)             NOT NULL,
    version         BIGINT                   NOT NULL,
    event_type      VARCHAR(255)             NOT NULL,
    event_data      JSONB                    NOT NULL,
    metadata        JSONB,
    created_at      TIMESTAMPTZ              NOT NULL DEFAULT now(),
    sequence_number BIGSERIAL,

    CONSTRAINT uq_tenant_stream_version UNIQUE (tenant_id, stream_id, version)
);

-- Backs EventStore.listStreamIds() (find every stream of a type for a tenant) and
-- loadEvents()/append()'s currentVersion() lookup (find one stream's events/version).
CREATE INDEX events_tenant_stream_type_idx ON events (tenant_id, stream_type, stream_id);

-- Backs any "what happened, in what exact order" query across streams/tenants.
CREATE INDEX events_sequence_number_idx ON events (sequence_number);
