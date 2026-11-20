-- Feature 005: the idle timeout a live session slides forward on every authenticated request.
-- The column is nullable and never guessed: a row without a recorded activity is measured from
-- its issuance (the service COALESCEs it with issued_ns), so imported sessions keep the history
-- the cutover recorded instead of inheriting a fabricated timestamp. The absolute lifetime stays
-- expires_ns; revocation stays revoked_ns.

ALTER TABLE sessions ADD COLUMN last_seen_ns BIGINT;
