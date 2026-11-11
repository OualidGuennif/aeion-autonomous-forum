# PostgreSQL cutover runbook (feature 002)

The product database is PostgreSQL, applied by Flyway. This page is the operator procedure the
feature's brief demands: migration owner and application roles, a bounded import, a verified
backup/restore pass, and a cutover that never silently discards committed writes. Everything here
is run by a human operator or the isolated operator JVM (`local.aeion.forum.DatabaseOperator`);
nothing in this repository deploys, drops a database or destroys data by itself.

## Roles and credentials

* **Migration owner**: owns the schema, runs the versioned migrations and the import. Its
  credentials exist only in the operator JVM's environment (`QUARKUS_DATASOURCE_*`).
* **Application role**: DML on `settings`, `accounts`, `sessions`, `threads`, `thread_outbox`,
  `thread_event_log`; read-only on `schema_events` and `flyway_schema_history`; no grant at all on
  `import_manifest`; no `CREATE` anywhere. The operator grants this after every `migrate`
  (`AEION_DB_APP_ROLE` names it) and strips schema `CREATE` from `PUBLIC` on that database, so the
  role's status never depends on a cluster default. The application role cannot migrate and cannot
  alter audit history.
* **No other role may write the recorded history.** After every `migrate` the operator sweeps the
  audit chain, the migration history and the manifest by privilege, not by name: any role other
  than the running migration owner that arrived holding `INSERT`, `UPDATE`, `DELETE`, `TRUNCATE`,
  `REFERENCES` or `TRIGGER` on those tables loses them, so a grant nobody remembered cannot create
  a second migration authority.
* **The application enforces the same contract at startup**: a boot whose configured role can write
  the audit chain, the migration history or the manifest, or create schema objects, refuses with
  `app_role_not_least_privilege` instead of serving through a migration authority.

Credentials are never logged, echoed or committed. A refusal names a stable code, never a value.

## Transport

Remote database connections require `sslmode=verify-full` (certificate and hostname
verification). Only an explicitly isolated local development transport — loopback or a unix
socket — may omit TLS. The application and the operator both refuse a non-local URL that cannot
verify them, naming `transport_unverified` only.

## The migration job

```bash
java -cp "$(gradle -p demo/forum-app -q operatorClasspath)" local.aeion.forum.DatabaseOperator migrate
java -cp "..." local.aeion.forum.DatabaseOperator validate
java -cp "..." local.aeion.forum.DatabaseOperator import-sqlite /secure/path/agora-backup.db
```

* `migrate` applies `db/migration` once, in order, and is idempotent: competing operators
  serialize on the database lock, an altered checksum refuses (`schema_invalid`), and a schema
  newer than the code refuses. A failed transactional migration leaves no partial schema. It runs
  only as a role that can create the schema it applies: a role without `CREATE` on `public`
  refuses before touching the database (`operator_not_schema_owner`), so an application-side role
  can never be talked into running the job.
* `validate` is the cutover gate: the schema must match the shipped migrations and, when an import
  manifest exists, every imported table must still reproduce its recorded count and content
  digest, and the audit chain in `schema_events` must verify link by link. Any drift, extra or
  missing row refuses the cutover (`manifest_mismatch`, `audit_chain_broken`).
* `import-sqlite` reads an **immutable, quiesced backup** and never opens it with SQLite: it copies
  the file together with its `-wal`/`-shm` sidecars into a private scratch directory (bounded in
  bytes as well as in rows) and reads the copy, so no statement, journal or WAL recovery can write
  the source, and the newest committed rows of a backup whose WAL was never checkpointed are not
  read as absent. Where no scratch space exists, the source is opened through a handle SQLite cannot
  write. It imports into a **fresh** database (any pre-existing row refuses), requires the tables
  every product row hangs from (`accounts`, `sessions`), imports a table the snapshot does not carry
  as empty, fills a column the snapshot predates only where the target allows a value to be absent,
  refuses a required column that is missing, verifies identifiers byte for byte (their format belongs
  to the legacy product, not to the import), checks relations, UTC epoch timestamps and normalized
  content, records the boundary (`boundary_ns` and the counts) in the manifest, and lands exactly the
  legacy rows — the audit chain carries the imported events, linked by digests the operator recomputes
  and re-verifies on every `validate`. A replay of the same backup verifies and changes nothing; a
  different backup refuses.

## Cutover order

1. Quiesce application writes against the SQLite volume.
2. Copy the SQLite file once; never import from the live file.
3. `import-sqlite <copy>` into the fresh PostgreSQL database, then `validate` — it must exit zero.
4. Run a disposable backup/restore pass: dump the imported database, restore it to a second
   disposable instance, `validate` the restored copy. The manifest digests must reproduce exactly.
5. Switch `QUARKUS_DATASOURCE_*` to the new database and start the application; startup validates
   the schema and refuses anything incompatible.
6. Keep the original SQLite volume unchanged.

## Rollback

The manifest freezes the imported boundary. If the PostgreSQL database accepted any write after
the boundary, `validate` refuses — deliberately: a rollback that silently discarded committed
post-cutover writes is exactly what the boundary exists to prevent. Export those rows first; only
then may the operator route back to the preserved SQLite volume. Deleting the old volume is never
authorized by generating or running this artifact.

## Local images

The local Compose demo pins the PostgreSQL image (currently `postgres:17.5-alpine`, never a
moving tag) and keeps the preserved SQLite volume (`forum-data`) untouched. Compose and Dockerfile
changes are reviewed artifacts on their own; this page documents the contract they must satisfy.
