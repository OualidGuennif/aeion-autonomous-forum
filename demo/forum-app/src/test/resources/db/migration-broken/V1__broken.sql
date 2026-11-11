-- A migration that creates a table and then fails: the probe table must not survive.
-- PostgreSQL DDL is transactional, so Flyway's rollback leaves nothing behind, not even history.

CREATE TABLE probe (id TEXT PRIMARY KEY);

CREATE TABLE probe (id TEXT PRIMARY KEY);
