-- Feature 002: the frozen shape of the legacy SQLite database, as the last SQLite commit owned it.
-- The import fixture builds files from this DDL; the operator must accept exactly this schema and
-- nothing else. This file is a test fixture only: it is never applied to PostgreSQL.

CREATE TABLE IF NOT EXISTS settings (
    name       TEXT PRIMARY KEY,
    value      TEXT NOT NULL,
    updated_ns INTEGER NOT NULL
);

CREATE TABLE IF NOT EXISTS accounts (
    id          TEXT PRIMARY KEY,
    username    TEXT NOT NULL UNIQUE,
    password    TEXT NOT NULL,
    role        TEXT NOT NULL DEFAULT 'member',
    created_ns  INTEGER NOT NULL,
    locked_ns   INTEGER,
    failures    INTEGER NOT NULL DEFAULT 0
);

CREATE TABLE IF NOT EXISTS sessions (
    id          TEXT PRIMARY KEY,
    account_id  TEXT NOT NULL REFERENCES accounts(id),
    issued_ns   INTEGER NOT NULL,
    expires_ns  INTEGER NOT NULL,
    revoked_ns  INTEGER
);

CREATE TABLE IF NOT EXISTS threads (
    id          TEXT PRIMARY KEY,
    title       TEXT NOT NULL,
    body        TEXT NOT NULL,
    author_id   TEXT NOT NULL REFERENCES accounts(id),
    created_ns  INTEGER NOT NULL
);

CREATE TABLE IF NOT EXISTS schema_events (
    id         INTEGER PRIMARY KEY AUTOINCREMENT,
    at_ns      INTEGER NOT NULL,
    migration  TEXT NOT NULL,
    detail     TEXT
);

CREATE INDEX IF NOT EXISTS idx_threads_created ON threads(created_ns DESC);
CREATE INDEX IF NOT EXISTS idx_sessions_account ON sessions(account_id);

CREATE TABLE IF NOT EXISTS thread_outbox (
    event_id     TEXT PRIMARY KEY,
    topic        TEXT NOT NULL,
    payload      TEXT NOT NULL,
    created_ns   INTEGER NOT NULL,
    attempts     INTEGER NOT NULL DEFAULT 0,
    published_ns INTEGER,
    last_error   TEXT
);

CREATE INDEX IF NOT EXISTS idx_outbox_pending ON thread_outbox(published_ns, created_ns);

CREATE TABLE IF NOT EXISTS thread_event_log (
    event_id    TEXT PRIMARY KEY,
    topic       TEXT NOT NULL,
    received_ns INTEGER NOT NULL,
    payload     TEXT NOT NULL
);
