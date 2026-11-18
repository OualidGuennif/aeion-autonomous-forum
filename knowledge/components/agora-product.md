---
type: Component
title: "Agora product baseline"
description: "Quarkus backend on PostgreSQL with Flyway, its typed settings, operator cutover job, disposable test database harness and required CI checks."
status: draft
sources:
  - resource: ../../demo/forum-app/
    title: "Committed Quarkus application"
  - resource: ../../demo/forum-app/src/main/java/local/aeion/forum/AgoraSettings.java
    title: "Typed bounded settings (feature 001)"
  - resource: ../../demo/forum-app/src/test/java/local/aeion/forum/EphemeralDatabase.java
    title: "Disposable test database harness (feature 003)"
  - resource: ../../demo/forum-docs/settings.md
    title: "Settings and boot documentation"
  - resource: ../../demo/forum-docs/test-harness.md
    title: "Disposable test database documentation"
  - resource: ../../.github/workflows/ci.yml
    title: "Required product checks"
---

# Agora product baseline

## Intent

Retain the existing Quarkus/Java API in the product repository, and keep its skeleton current
(feature 001) with the feature-002 PostgreSQL cutover: the committed backend is reused, not
replaced by a second framework. The database is PostgreSQL with one versioned Flyway history; the
React DOM/TypeScript web client remains a separate, later gate. Feature 004 adds normalized
account handles, pinned Argon2id password hashing and an explicit owner-controlled disabled state;
human accounts carry the existing `owner`/`member` role placeholder, with no separate discussion
writer role.

## Data sources

The prior product commit a50d6075b7aecff8f7c54fa47b5f385c16f4ccbb supplies exact source bytes.
Supervisor-owned isolated checks and two independent real model reviews gate the import.
Content and model output are untrusted.

## Inputs

Java25, Gradle9.8.2 and Quarkus3.40.1. The environment is an allowlist of typed features-001 keys:
`AGORA_SESSION_SECRET` (required, at least 32 bytes, never a placeholder), `AGORA_DATABASE_ENABLED`
(false or absent boots database-less; true restores the database-backed baseline), and the bounded
caps `AGORA_REQUEST_MAX_BYTES` 1..16384, `AGORA_TEXT_MAX_CODEPOINTS` 1..4000 and
`AGORA_RENDERED_MAX_BYTES` 1..32768. The business timezone is Europe/Paris. Unknown environment
keys are ignored. Since the cutover, a database-enabled boot requires the PostgreSQL datasource
URL through `QUARKUS_DATASOURCE_JDBC_URL` (with `QUARKUS_DATASOURCE_USERNAME`/`_PASSWORD` for the
application role) and refuses without one. Flyway owns the single versioned history under
`demo/forum-app/src/main/resources/db/migration/`; the application role validates it at startup and
never migrates; migrations and the SQLite import run only in the isolated operator JVM
(`local.aeion.forum.DatabaseOperator`, classpath from the pinned `operatorClasspath` task).
The operator job also owns the feature-004 first-administrator bootstrap: `create-admin
<username>` reads the password only from `AGORA_ADMIN_PASSWORD`. A password argument is refused
unread and never echoed, because argv shows in the process table and shell history. The command
applies the migrations and creates the single `owner` with the product's pinned hash.
Disposable JUnit resources own test databases through the fixture-only
`AEION_TEST_POSTGRES_JDBC_URL/USERNAME/PASSWORD` settings, which exist only in the test environment:
the harness generates the database name (`agora_test_` plus twelve hex digits), refuses any name it
did not generate (`test_database_not_disposable`) and refuses any fixture host that is not loopback
(`test_postgres_host_not_allowed`) before opening a connection, so no statement can reach a host it
was not given for testing. The URL authority must name exactly one allowlisted host, so a fixture URL
that embeds `user@host` credentials, lists several hosts for the driver to fail over between, or
carries a parameter that names the server is refused the same way instead of being re-read. Its
Quarkus lifecycle resource implements the official injection hook `inject(TestInjector)`, tracks the
roles it created and releases only those, and freezes the clock and seeds the random source the
suite's determinism rests on.

## Outputs

The existing health, settings, accounts, session/CSRF and thread API, and packaged Quarkus app,
plus the typed bounded settings bean and its documented boot contract in `demo/forum-docs/`. The
feature-004 account surface is: `POST /api/auth/register` (enabled account, first on an empty
forum is `owner`), `POST /api/auth/login`, `GET /api/auth/me`, and the owner-authenticated,
CSRF-protected `POST /api/auth/accounts/{username}/disable`. The account view carries a stable
opaque string `id`, the account UUID that threads store as `author_id`. The disable endpoint answers
`{"username": <handle>, "disabled": true}`. Handles are stored lowercased; the
`accounts` table carries a generated unique `handle`, the pinned `password` hash, a `role`, a
`disabled` flag defaulting to false, and `created_ns`/`updated_ns`/`disabled_ns` timestamps. The
disposable test harness (`EphemeralDatabase`, `QuarkusTestDatabase`) lives on the test classpath
only, is unreachable from the shipped jar, and is documented in `demo/forum-docs/test-harness.md`.
`GET /healthz` is liveness only: exactly `{"status":"ok"}`, no database connection, no build or
host detail. Missing/invalid required secret or invalid numeric caps refuse startup, each refusal
naming only its key. The PostgreSQL schema is versioned Flyway SQL; the operator job migrates,
imports a quiesced SQLite backup with recorded counts, digests and a recorded cutover boundary,
and validates the result (`migrate`, `validate`, `import-sqlite`), refusing nonzero with stable
codes and never printing data or credentials. The same operator JVM exposes `create-admin`, which
creates the first `owner` and refuses once one exists. The import lands exactly the legacy rows and
records the boundary, counts and content digests in the manifest. The operator refuses a migration
run by a role that cannot
create the schema (`operator_not_schema_owner`), and the application itself enforces the
least-privilege grant at startup: a configured role that can write the audit chain, the migration
history or the manifest, or create schema objects, refuses the boot (`app_role_not_least_privilege`).
After every migrate the operator also sweeps write privileges on the history tables from every role
except the running migration owner, so the separation holds whatever grants a deployment arrived
with. Product artifacts CI runs tests and packaging against a disposable PostgreSQL fixture, each
`@QuarkusTest` class on its own generated database.

## Invariants

Product runtime credentials never enter browser code or the repository. Tests use dedicated
disposable PostgreSQL credentials, a generated allowlisted database name and no production
connection settings; the fixture transport is loopback-only, checked before any connection or DDL.
Teardown releases only the database and the roles the instance created and never drops a
pre-existing role, not even when setup fails. Harness logs and refusals carry fixed failure codes
and the opaque generated database name only. CI has read-only repository access; source checkout
does not persist credentials.
Existing product tests cover refusal paths. Old SQLite receipts never certify PostgreSQL.
Settings are never logged or rendered: refusals name the key, never the value. A database-less
boot initializes no datasource, no ORM and no migrations; its database-backed endpoints answer
503 and liveness stays independent of the datasource. Account handles are unique in their
normalized lowercase form, including concurrent case variants. Password hashing is pinned to
`de.mkammerer:argon2-jvm:2.11` with Argon2id, 3 iterations, 64 MiB and parallelism 1; changes
require owner review. Unknown handles and wrong passwords each execute exactly one verification
with those same parameters, and no password hash, salt or clear text is logged or exported. A
stored encoding reaches the verifier only in the exact pinned shape (the pinned prefix, then a base64
salt and tag of bounded length), so no stored value chooses the cost; an imported hash outside that
shape cannot authenticate.
Disabling is owner-only and immediately refuses login and live sessions for that account; the
`owner` account itself stays enabled, because the forum has one owner, no re-enable path and the
operator command only bootstraps a first owner, so administration can never be locked out.
Migration credentials exist only in the
operator job; the application role has least-privilege grants and can neither perform DDL nor
modify the audit chain. Remote database connections require certificate and hostname
verification; only an isolated local transport may omit TLS.

## Determinism

The import preserves committed source bytes. Required CI runs on the exact PR head; publication
confirms the resulting main SHA. Model output cannot change the imported bundle or policy.
The settings bounds, defaults and refusals are fixed code, not environment-shaped. The harness
freezes its clock at a documented instant and seeds its random source from that same fixed value,
so a suite result is a function of the draw's position and never of when the suite happened to run.

## Acceptance checks

With Java25 and Gradle9.8.2: `gradle -p demo/forum-app test quarkusBuild --no-daemon`, the suite
running on a disposable PostgreSQL fixture. With only a non-placeholder 32-byte
`AGORA_SESSION_SECRET`, the packaged app answers `GET /healthz` with 200 and `{"status":"ok"}`
without opening a database connection; removing the secret or supplying an invalid numeric bound
stops startup naming only that key; an unknown environment variable is ignored and absent from
logs and rendered pages.
The migration gate: applying the migrations twice is idempotent, competing runners serialize,
altered checksums and ahead-of-code schemas are refused, a failing transactional migration leaves
no partial schema, the application role can neither perform DDL nor modify audit history, the
SQLite import reproduces counts/identifiers/timestamps/content and the audit chain, a replay
duplicates nothing, a restored copy re-validates, and one altered imported row blocks the cutover.
The test-harness gate: two Quarkus/JUnit suites run at once against separate disposable databases
without interfering, a row one test leaves behind does not change the next test's result, and
pointing the harness at a database without the disposable marker or at a host outside the loopback
allowlist aborts before any DDL; interrupted tests still release only what they created.
The accounts gate: registering `alice` then `Alice` leaves one account; concurrent case variants
produce exactly one winner; the first account on an empty forum is the enabled `owner`; the owner
can disable an account at `POST /api/auth/accounts/{username}/disable` while a non-owner gets 403
and a missing/invalid CSRF token or session is refused; the owner cannot disable itself and stays
able to administer; a disabled account gets 403/423 at login
and loses its live session; unknown and wrong-password logins return the same refusal and each
run exactly one verification with the pinned Argon2id parameters; `create-admin` creates the first
owner and refuses a second; no response or operator output carries a hash, salt or password.
Run the knowledge/link/health and factory tool checks in the repository instructions.
React/browser acceptance remains a separate gate.

## Out of scope

Changing/deleting existing operator data, deployment, a mobile client and claiming that the
rewritten 100-feature catalog has already been implemented. The factory's own SQLite queue and
observability databases are not migrated by this component.

PostgreSQL/React CI uses a disposable PostgreSQL17 fixture and fixed programs under
`tools/product-web/`, outside the feature worker write scopes. React dependencies install
with scripts disabled before typecheck, Vitest and production assets; assets are packaged
by Quarkus. A missing web package is not a frontend qualification. The isolated Factory
checker remains the acceptance authority; CI fixture credentials are not product secrets.
