---
type: Component
title: "Agora product baseline"
description: "Existing Quarkus backend and its required CI checks, before PostgreSQL/React cutover."
status: draft
sources:
  - resource: ../../demo/forum-app/
    title: "Committed Quarkus application"
  - resource: ../../.github/workflows/ci.yml
    title: "Required product checks"
  - resource: ../../demo/forum-app/src/main/java/local/aeion/forum/SessionSecret.java
    title: "Fail-closed session secret"
  - resource: ../../demo/forum-app/src/main/java/local/aeion/forum/ForumBounds.java
    title: "Bounded environment caps"
  - resource: ../../demo/forum-app/src/main/java/local/aeion/forum/DatabaseMode.java
    title: "Database-backed gate that proves its own required settings"
  - resource: ../../demo/forum-app/src/main/java/local/aeion/forum/ThreadPublisher.java
    title: "Outbox drain gated on the database mode"
  - resource: ../../demo/forum-app/src/main/java/local/aeion/forum/MigrationRunner.java
    title: "Migration banner gated on the database mode"
  - resource: ../../demo/forum-app/src/main/java/local/aeion/forum/StartupChecks.java
    title: "Startup validation order"
  - resource: ../../demo/forum-app/src/main/java/local/aeion/forum/ForumHealth.java
    title: "Healthz liveness only"
  - resource: ../../demo/forum-app/src/main/java/local/aeion/forum/ThreadService.java
    title: "Configured normalized-text cap"
  - resource: ../../demo/forum-app/src/main/java/local/aeion/forum/SettingsResource.java
    title: "Configured settings-value cap"
  - resource: ../../demo/forum-app/src/main/java/local/aeion/forum/RequestValidationFilter.java
    title: "Configured request-byte cap"
  - resource: ../../demo/forum-app/src/main/java/local/aeion/forum/AuthService.java
    title: "Session cookie signed with the validated secret"
  - resource: ../../demo/forum-app/src/main/java/local/aeion/forum/CsrfGuard.java
    title: "CSRF token derived from the validated secret"
  - resource: ../../demo/forum-app/src/main/resources/application.properties
    title: "Allowlisted settings wiring and defaults"
  - resource: ../../demo/forum-app/build.gradle
    title: "The retained Quarkus/Gradle build (no second framework)"
  - resource: ../../demo/forum-app/DOCKER.md
    title: "Inherited local runbook that feature 001 does not own"
  - resource: ../../demo/forum-app/src/test/java/local/aeion/forum/HealthLivenessBootTest.java
    title: "Database-less liveness regressions"
  - resource: ../../demo/forum-app/src/test/java/local/aeion/forum/ConfiguredCapsBootTest.java
    title: "Lowered-cap HTTP regressions"
  - resource: ../../demo/forum-app/src/test/java/local/aeion/forum/SettingsResourceTest.java
    title: "Settings wire shape over HTTP"
  - resource: ../../demo/forum-app/src/test/java/local/aeion/forum/LivenessSecretResource.java
    title: "Test fixture: boot with only the secret"
  - resource: ../../demo/forum-app/src/test/java/local/aeion/forum/ConfiguredCapsResource.java
    title: "Test fixture: boot with lowered caps"
  - resource: ../../demo/forum-app/src/test/java/local/aeion/forum/QuarkusTestDatabase.java
    title: "Test fixture: real server on a disposable database"
  - resource: ../../demo/forum-app/src/test/java/local/aeion/forum/ForumBoundsTest.java
    title: "Bound defaults and refusals"
  - resource: ../../demo/forum-app/src/test/java/local/aeion/forum/DatabaseModeTest.java
    title: "Database-gate defaults, refusals and refusal-before-dial"
  - resource: ../../demo/forum-app/src/test/java/local/aeion/forum/StartupChecksTest.java
    title: "Startup validation order"
  - resource: ../../demo/forum-app/src/test/java/local/aeion/forum/SessionSecretTest.java
    title: "Secret refusal regressions"
  - resource: ../../demo/forum-app/src/test/java/local/aeion/forum/RequestValidationTest.java
    title: "Standing request-validation regressions at the default cap"
  - resource: ../../demo/forum-app/src/test/java/local/aeion/forum/AuthServiceTest.java
    title: "Auth regressions over the validated secret"
  - resource: ../../demo/forum-app/src/test/java/local/aeion/forum/AuthResourceTest.java
    title: "Standing auth HTTP regressions on the fixture this feature changed"
  - resource: ../../demo/forum-app/src/test/java/local/aeion/forum/ThreadsResourceTest.java
    title: "Standing thread HTTP regressions on the fixture this feature changed"
  - resource: ../../demo/forum-app/src/test/java/local/aeion/forum/ThreadServiceTest.java
    title: "Standing outbox HTTP regressions on the fixture this feature changed"
  - resource: ../../demo/forum-app/src/test/java/local/aeion/forum/SecurityHeadersTest.java
    title: "Standing header HTTP regressions on the fixture this feature changed"
  - resource: ../../demo/forum-docs/settings.md
    title: "Settings contract for operators"
---

# Agora product baseline

## Intent

Retain the existing Quarkus/Java API in the product repository. This bootstrap imports the
committed backend; it does not implement the newly specified PostgreSQL/React architecture.

## Data sources

The prior product commit a50d6075b7aecff8f7c54fa47b5f385c16f4ccbb supplies exact source bytes.
Supervisor-owned isolated checks and two independent real model reviews gate the import.

## Inputs

Java25, Gradle9.8.2, Quarkus3.40.1 and a session secret from private runtime configuration.
The transition baseline uses SQLite; disposable JUnit resources own test databases.

Feature 001 adds the typed, allowlisted deployment environment to the same application
([settings contract](../../demo/forum-docs/settings.md)): three bounded caps
(`AGORA_REQUEST_MAX_BYTES`, `AGORA_TEXT_MAX_CODEPOINTS`, `AGORA_RENDERED_MAX_BYTES`), the
database-backed gate `AGORA_DATABASE_ENABLED` with required PostgreSQL connection settings when
enabled, the Europe/Paris business timezone, and the fail-closed session secret. It changes no
build tool and no framework: the retained Gradle build is
[`build.gradle`](../../demo/forum-app/build.gradle) with the pinned
Java25/Gradle9.8.2/Quarkus3.40.1 coordinates. Where those facts live: the validated beans are
`SessionSecret`, `ForumBounds`, `DatabaseMode` and `StartupChecks`; the gate in database-less mode
(also the default when the key is absent) additionally keeps the outbox drain (`ThreadPublisher`)
and the migration runner (`MigrationRunner`) from touching the datasource, so neither the startup
path nor the liveness path ever dials it, while the committed datasource and persistence
configuration of the transition baseline stays in the bundle untouched until the PostgreSQL
cutover; an enabled gate validates its own connection settings the moment anything asks it a
question, because CDI leaves the order of the startup observers undefined and a half-configured
database-backed deployment must refuse before a migration or a drain dials the datasource, not after
it tried; the event consumer is outside that gate and keeps the broker flag it already had
(`AEION_KAFKA_ENABLED`, off by default); the caps are enforced where they apply
(`RequestValidationFilter` for request bytes, `ThreadService` and `SettingsResource` for normalized
text); the defaults sit in `application.properties`; and the regressions are the HTTP boots
`HealthLivenessBootTest` and `ConfiguredCapsBootTest` with their `LivenessSecretResource` and
`ConfiguredCapsResource` fixtures and the disposable `QuarkusTestDatabase`, plus the unit suites
`ForumBoundsTest`, `DatabaseModeTest`, `StartupChecksTest` and `SessionSecretTest`, the HTTP wire-shape
suite `SettingsResourceTest`, and the standing `RequestValidationTest` and `AuthServiceTest`
regressions.

Traceability for this change follows the repository convention: the owning document names each file
whose facts it states in its own `sources`, and a touched file whose *other* owner is a different
specification carries a `Docs-Checked` note saying which fact of that specification did not change —
`RequestValidationFilter` and `RequestValidationTest`, both owned by
[request validation](request-validation.md), whose rules feature 001 leaves intact: same routes, same
413/422/415 refusals, same 16384-byte boundary at the documented default cap.

Two more sets of files are named in `sources` here because feature 001 changed the ground they stand
on rather than the rules they enforce. The validated secret is consumed, unchanged in meaning, by
`AuthService` (it signs the session cookie) and `CsrfGuard` (it derives the token), so a deployment
that cannot boot cannot hold a session. And every standing suite that opens its own database —
`AuthResourceTest`, `ThreadsResourceTest`, `ThreadServiceTest`, `SecurityHeadersTest`,
`SettingsResourceTest` and `RequestValidationTest` — boots through `QuarkusTestDatabase`, whose
environment this feature extended with the required secret and the database-backed gate: those suites
were written before that typed startup existed, and they now start on it, while each of them still
asserts the feature it was written for. The two boots that test that path itself,
`HealthLivenessBootTest` and `ConfiguredCapsBootTest`, deliberately do not use that fixture: each
names its own.

## Outputs

The existing health, settings, accounts, session/CSRF and thread API, and packaged Quarkus app.
Missing/invalid required secret refuses startup. Product artifacts CI runs tests and packaging.

Health behavior is part of the skeleton: `GET /healthz` returns exactly `{"status":"ok"}`,
acquires no database connection in any mode, and exposes no build, host or configuration detail.
Database-less boot is the default, so a deployment with only the session secret serves liveness.
[`DOCKER.md`](../../demo/forum-app/DOCKER.md) is the inherited local runbook and still prints the
pre-001 health body and a "non-empty" secret; that path is outside feature 001's write scope, so it
is corrected against this document and the shipped behavior, never the other way round.

## Invariants

Product runtime credentials never enter browser code or the repository. Tests use disposable
databases. CI has read-only repository access; source checkout does not persist credentials.
Existing product tests cover refusal paths. Old SQLite receipts never certify PostgreSQL.
Settings and the environment are never logged or echoed: a refusal names its key alone, and the
liveness answer carries one status field. Knowledge and the settings contract state that shape, so
a document that asks the product for a richer body is stale rather than a second opinion.

## Determinism

The import preserves committed source bytes. Required CI runs on the exact PR head; publication
confirms the resulting main SHA. Model output cannot change the imported bundle or policy.

## Acceptance checks

With Java25 and Gradle9.8.2: `gradle -p demo/forum-app test quarkusBuild --no-daemon`.
Run the knowledge/link/health and factory tool checks in the repository instructions.
Migration/restore, least-privilege PostgreSQL and React/browser acceptance are separate gates.

Feature 001 additions run in the same suite: with only a non-placeholder 32-byte
`AGORA_SESSION_SECRET` the application boots database-less and `/healthz` answers `200
{"status":"ok"}` without a database connection; removing the secret, shortening it, setting a cap
outside its documented range, spelling the database flag wrongly, enabling the database plane without
one of its three connection settings, or pointing the
business timezone away from `Europe/Paris` refuses the start naming its key and nothing else; an
unknown environment variable is ignored and absent from logs and rendered pages.
"Acquires no connection" is tested the only way the claim means anything: the packaged application
is restarted with the datasource pointing at something undialable and must still answer `200`, so a
startup that dialed it — a migration, an ORM bootstrap connection, a validation probe — fails the
gate instead of serving.

## Out of scope

Database cutover, changing/deleting existing operator data, deployment, a mobile client and
claiming that the rewritten 100-feature catalog has already been implemented.

PostgreSQL/React CI uses a disposable PostgreSQL17 fixture and fixed programs under
`tools/product-web/`, outside the feature worker write scopes. React dependencies install
with scripts disabled before typecheck, Vitest and production assets; assets are packaged
by Quarkus. A missing web package is not a frontend qualification. The isolated Factory
checker remains the acceptance authority; CI fixture credentials are not product secrets.
