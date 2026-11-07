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
    title: "Database-backed gate"
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
  - resource: ../../demo/forum-app/src/main/resources/application.properties
    title: "Allowlisted settings wiring and defaults"
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
    title: "Database-gate defaults and refusals"
  - resource: ../../demo/forum-app/src/test/java/local/aeion/forum/StartupChecksTest.java
    title: "Startup validation order"
  - resource: ../../demo/forum-app/src/test/java/local/aeion/forum/SessionSecretTest.java
    title: "Secret refusal regressions"
  - resource: ../../demo/forum-app/src/test/java/local/aeion/forum/RequestValidationTest.java
    title: "Standing request-validation regressions at the default cap"
  - resource: ../../demo/forum-app/src/test/java/local/aeion/forum/AuthServiceTest.java
    title: "Auth regressions over the validated secret"
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
enabled, the Europe/Paris business timezone, and the fail-closed session secret. Where those facts
live: the validated beans are `SessionSecret`, `ForumBounds`, `DatabaseMode` and `StartupChecks`;
the caps are enforced where they apply (`RequestValidationFilter` for request bytes, `ThreadService`
and `SettingsResource` for normalized text); the defaults sit in `application.properties`; and the
regressions are the HTTP boots `HealthLivenessBootTest` and `ConfiguredCapsBootTest` with their
`LivenessSecretResource` and `ConfiguredCapsResource` fixtures and the disposable
`QuarkusTestDatabase`, plus the unit suites `ForumBoundsTest`, `DatabaseModeTest`,
`StartupChecksTest`, `SessionSecretTest` and `SettingsResourceTest`, and the standing
`RequestValidationTest` and `AuthServiceTest` regressions.

## Outputs

The existing health, settings, accounts, session/CSRF and thread API, and packaged Quarkus app.
Missing/invalid required secret refuses startup. Product artifacts CI runs tests and packaging.

Health behavior is part of the skeleton: `GET /healthz` returns exactly `{"status":"ok"}`,
acquires no database connection in any mode, and exposes no build, host or configuration detail.
Database-less boot is the default, so a deployment with only the session secret serves liveness.

## Invariants

Product runtime credentials never enter browser code or the repository. Tests use disposable
databases. CI has read-only repository access; source checkout does not persist credentials.
Existing product tests cover refusal paths. Old SQLite receipts never certify PostgreSQL.
Settings and the environment are never logged or echoed: a refusal names its key alone.

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
outside its documented range, spelling the database flag wrongly, or pointing the
business timezone away from `Europe/Paris` refuses the start naming its key and nothing else; an
unknown environment variable is ignored and absent from logs and rendered pages.

## Out of scope

Database cutover, changing/deleting existing operator data, deployment, a mobile client and
claiming that the rewritten 100-feature catalog has already been implemented.

PostgreSQL/React CI uses a disposable PostgreSQL17 fixture and fixed programs under
`tools/product-web/`, outside the feature worker write scopes. React dependencies install
with scripts disabled before typecheck, Vitest and production assets; assets are packaged
by Quarkus. A missing web package is not a frontend qualification. The isolated Factory
checker remains the acceptance authority; CI fixture credentials are not product secrets.

