CREATE TABLE notes
(
    id           UUID PRIMARY KEY,
    title        VARCHAR(255)             NOT NULL,
    body         TEXT                     NOT NULL,
    status       VARCHAR(20)              NOT NULL,
    created_at   TIMESTAMP WITH TIME ZONE NOT NULL,
    published_at TIMESTAMP WITH TIME ZONE
);
