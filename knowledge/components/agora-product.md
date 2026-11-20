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
`AGORA_RENDERED_MAX_BYTES` 1..32768. Feature 005 adds the bounded session-lifetime keys
`AGORA_SESSION_IDLE_SECONDS` and `AGORA_SESSION_ABSOLUTE_SECONDS` (integer 1..2592000, each
defaulting to the 900 seconds `SESSION_SECONDS` keeps) and the optional
`AGORA_SESSION_VERIFICATION_SECRETS`, a comma-separated list of at most four previous signing keys
that still verify cookies. The business timezone is Europe/Paris. Unknown environment
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
`disabled` flag defaulting to false, and `created_ns`/`updated_ns`/`disabled_ns` timestamps.
Feature 005 completes the session plane: `POST /api/auth/login` issues a server-side session and
sets the signed `agora_session` cookie (`<token>.<hmac>`) with `HttpOnly`, `Path=/`,
`SameSite=Strict` and a `Max-Age` equal to the session's absolute lifetime (900 seconds until a
deployment configures another, so a configured lifetime is never capped by the cookie), plus
`Secure` exactly when the request was served over HTTPS —
the flag follows the request scheme Quarkus reports, so the plain-HTTP loopback deployment omits it
and a TLS-terminating proxy turns it on by having Quarkus forward the original scheme. The session
lives in the `sessions` table, keyed by the token's SHA-256, with `issued_ns`, the absolute
`expires_ns`, `revoked_ns` and the `last_seen_ns` (migration V5)
that slides on every authenticated request. `POST /api/auth/login` that presents a live cookie with
its `X-CSRF-Token` rotates: it revokes exactly the presented row, issues a fresh identifier and a
fresh CSRF token, and leaves unpresented sessions valid; a failed credential exchange revokes
nothing. `POST /api/auth/logout` (CSRF-protected) revokes the presented session and clears the
cookie; when the request names no live session to revoke — no cookie, an unusable value or an
already revoked row — it keeps the contract it had before feature 005: `200 {"revoked": false}`
and a cleared cookie. The CSRF-protected
`POST /api/auth/logout-all` revokes every unrevoked session of the caller's account and answers
`{"revoked": <count>}`, with `401 {"error":"unauthorized"}` without a session and the guard's
`403 {"error":"csrf_invalid"}` for a missing or wrong `X-CSRF-Token`. A
session stops authenticating once its absolute lifetime or its idle timeout passes (whichever is
first) or its row is revoked; expired, revoked and unknown cookies are indistinguishable 401s. The
disposable test harness (`EphemeralDatabase`, `QuarkusTestDatabase`) lives on the test classpath
only, is unreachable from the shipped jar, and is documented in `demo/forum-docs/test-harness.md`.
`GET /healthz` is liveness only: exactly `{"status":"ok"}`, no database connection, no build or
host detail. Missing/invalid required secret, invalid numeric caps or an invalid session-lifetime
key refuse startup, each refusal naming only its key; the lifetime bean is forced during the boot
check, so a bad value never waits for the first login. The PostgreSQL schema is versioned Flyway SQL; the operator job migrates,
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
Sessions are opaque and server-side: the cookie's signing keys come only from the environment and
are never logged, rendered or echoed, the stored row keeps the token's SHA-256 instead of the
value, and a rotated signing key keeps authenticating the sessions it issued through the configured
verification keys instead of logging every live session out at once. The identifier is rotated by
any login that presents the cookie again, and every revocation is exact: logout and rotation
revoke only the presented session, `logout-all` only the account's own sessions, and an untouched
account or session is never collateral. The idle timeout and the absolute lifetime are bounded
settings whose defaults keep the 900-second behaviour, and the frozen harness clock proves them in
JUnit rather than by sleeping.
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
The session gate (feature 005): a login sets the `HttpOnly`, `Path=/`, `SameSite=Strict`
`agora_session` cookie whose `Max-Age` is the session's absolute lifetime — 900 seconds by default
and the configured value once a deployment names one — with `Secure` on an HTTPS request only, the
flag omitted over the plain-HTTP loopback; an absent, forged or edited value answers 401 without
disturbing the live session; logout with its CSRF token revokes the presented session and clears
the cookie, while a logout that names no live session keeps its `200 {"revoked": false}` answer;
`logout-all` answers 401
without a session, 403 without or with a wrong `X-CSRF-Token` and revokes nothing then, and revokes
every session of the account and none of another account's when authorized; a login that presents a
live cookie and its CSRF token issues a rotated identifier with a new CSRF token and revokes only
that presented cookie, whose replay then answers 401; the frozen harness clock ends a session at its
idle and absolute deadlines while activity renews the idle deadline up to the absolute limit; and a
rotated signing key with a configured verification key keeps authenticating the sessions and CSRF
tokens a previous key issued, without breaking the secret's refusal codes.
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
