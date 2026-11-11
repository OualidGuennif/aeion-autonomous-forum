-- Feature 002: the operator-only cutover manifest. The import job writes one row per imported
-- table; validate recomputes counts and content digests from them and refuses any drift. The
-- application role holds no grant on this table at all.

CREATE TABLE import_manifest (
    table_name     TEXT PRIMARY KEY,
    row_count      BIGINT NOT NULL,
    content_digest TEXT NOT NULL,
    boundary_ns    BIGINT NOT NULL,
    imported_ns    BIGINT NOT NULL,
    source_digest  TEXT NOT NULL
);
