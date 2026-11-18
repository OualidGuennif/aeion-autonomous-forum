-- Feature 004: normalized account identity, an explicit owner-controlled disabled state and the
-- timestamps a disable needs. The handle is generated from the stored username, so case variants
-- are one identity whichever path wrote the row; the two unique indexes make that a database
-- invariant, not a convention the service could forget.

ALTER TABLE accounts ADD COLUMN handle TEXT GENERATED ALWAYS AS (lower(username)) STORED;
ALTER TABLE accounts ALTER COLUMN handle SET NOT NULL;
ALTER TABLE accounts ADD COLUMN disabled BOOLEAN NOT NULL DEFAULT FALSE;
ALTER TABLE accounts ADD COLUMN disabled_ns BIGINT;
ALTER TABLE accounts ADD COLUMN updated_ns BIGINT NOT NULL DEFAULT 0;

CREATE UNIQUE INDEX accounts_handle_key ON accounts (handle);
CREATE UNIQUE INDEX accounts_single_owner ON accounts (role) WHERE role = 'owner';
