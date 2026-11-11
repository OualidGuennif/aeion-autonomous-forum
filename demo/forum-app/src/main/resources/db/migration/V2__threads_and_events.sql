-- Feature 010 (threads) and 011 (events leave the request path).
-- The outbox is written in the same transaction as the thread, so a broker outage cannot lose an
-- event and a crash cannot publish one that was never stored. The log makes the consumer
-- idempotent: the same event id twice is applied once.

CREATE TABLE thread_outbox (
    event_id     TEXT PRIMARY KEY,
    topic        TEXT NOT NULL,
    payload      TEXT NOT NULL,
    created_ns   BIGINT NOT NULL,
    attempts     INTEGER NOT NULL DEFAULT 0,
    published_ns BIGINT,
    last_error   TEXT
);

CREATE INDEX idx_outbox_pending ON thread_outbox(published_ns, created_ns);

CREATE TABLE thread_event_log (
    event_id    TEXT PRIMARY KEY,
    topic       TEXT NOT NULL,
    received_ns BIGINT NOT NULL,
    payload     TEXT NOT NULL
);
