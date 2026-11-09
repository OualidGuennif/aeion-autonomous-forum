---
type: Component
title: "Agora product baseline"
description: "Existing Quarkus backend with bounded settings and liveness, its required CI checks, before the PostgreSQL/React cutover."
status: draft
sources:
  - resource: ../../demo/forum-app/src/main/java/local/aeion/forum/ForumSettings.java
    title: "Typed allowlisted settings and bounds"
  - resource: ../../demo/forum-app/src/main/java/local/aeion/forum/StartupChecks.java
    title: "Fail-closed startup checks"
  - resource: ../../demo/forum-app/src/main/java/local/aeion/forum/ForumHealth.java
    title: "Liveness endpoint"
  - resource: ../../demo/forum-app/src/main/java/local/aeion/forum/DatabaseFeatureFilter.java
    title: "Database-less data-surface gate"
  - resource: ../../demo/forum-app/src/main/java/local/aeion/forum/MigrationRunner.java
    title: "Migrations gated by database-backed features"
  - resource: ../../demo/forum-app/src/main/java/local/aeion/forum/SettingsResource.java
    title: "Bounded settings API"
  - resource: ../../demo/forum-app/src/main/java/local/aeion/forum/ThreadEventConsumer.java
    title: "Event consumer gated by database-backed features"
  - resource: ../../demo/forum-app/src/main/java/local/aeion/forum/ThreadPublisher.java
    title: "Outbox drain gated by database-backed features"
  - resource: ../../demo/forum-app/src/main/resources/application.properties
    title: "Documented configuration keys and fixed transport ceiling"
  - resource: ../../demo/forum-app/src/test/java/local/aeion/forum/ForumSettingsTest.java
    title: "Settings bounds and refusal regressions"
  - resource: ../../demo/forum-app/src/test/java/local/aeion/forum/DatabaselessHealthTest.java
    title: "Database-less liveness acceptance"
  - resource: ../../demo/forum-docs/settings.md
    title: "Settings and startup contract"
  - resource: ../../.github/workflows/ci.yml
    title: "Required product checks"
---

# Agora product baseline

## Intent

Retain the existing Quarkus/Java API in the product repository. This bootstrap imports the
committed backend and adds feature 001's fail-closed settings and liveness; it does not implement
the newly specified PostgreSQL/React architecture from scratch.

## Data sources

The prior product commit a50d6075b7aecff8f7c54fa47b5f385c16f4ccbb supplies exact source bytes.
Supervisor-owned isolated checks and two independent real model reviews gate the import.

Java25, Gradle9.8.2, Quarkus3.40.1 and a session secret from private runtime configuration.
The transition baseline uses SQLite; disposable JUnit resources own test databases.

Feature 001 keeps the settings allowlisted and bounded: `AGORA_SESSION_SECRET` (>=32 bytes, no
placeholder) is required, `AGORA_DATABASE_ENABLED` (default `false`) gates database-backed features
and makes a connection setting required while it is on (`AEION_SQLITE_PATH`, or a
`quarkus.datasource.jdbc.url` the operator stated instead of the shipped development one), and the
three numeric caps are `AGORA_REQUEST_MAX_BYTES` (1..16384, default 16384),
`AGORA_TEXT_MAX_CODEPOINTS` (1..4000, default 4000) and `AGORA_RENDERED_MAX_BYTES` (1..32768,
default 32768), with `AGORA_BUSINESS_TIMEZONE` defaulting to `Europe/Paris`. `ForumSettings` validates
them and `StartupChecks` checks the secret first and the connection last, so an invalid range refuses
startup and names only the key. Any other environment variable is not looked up. The shipped
`application.properties` maps exactly those keys: the connection key has no default, the three
numeric keys default to their ceilings and the database switch to `false`, and the transport ceiling
stays a literal 16 KiB so an out-of-range bound is refused by `ForumSettings` (naming the key)
rather than by a config converter that would quote the supplied value.

## Inputs

The documented environment keys only: the session secret, the database switch and connection, the
three numeric caps and the business timezone. Configuration in `application.properties` maps exactly
those keys and keeps the existing ports, routes and datasource settings unchanged.

## Outputs

The existing health, settings, accounts, session/CSRF and thread API, and packaged Quarkus app.
`GET /healthz` answers only `{"status":"ok"}`; the separate `GET /__release` route carries release
and runtime information. Missing/invalid required secret refuses startup. A database-less boot (the
default) serves liveness without opening a connection: migrations, the outbox drain and the event
consumer are skipped, and the datasource is looked up only when a database-backed route is called.
In that mode the `/api/` data surface answers `503 {"error":"database_not_configured"}` before a body
is read or a connection is requested. The gate covers `/api` itself as well as every path beneath
it, and it treats a datasource URL equal to the shipped development URL, or the blank expansion
`jdbc:sqlite:`, as an unstated connection. Product artifacts CI runs tests and packaging. The settings
contract is written up in [the settings and startup document](../../demo/forum-docs/settings.md).

## Invariants

Product runtime credentials never enter browser code or the repository. Tests use disposable
databases. CI has read-only repository access; source checkout does not persist credentials.
Existing product tests cover refusal paths. Old SQLite receipts never certify PostgreSQL.

A required setting that is missing, unparseable or outside its bound stops the process instead of
being repaired by a default, and its refusal names the key and the range but never the value that
was supplied. `GET /healthz` is liveness only: one status field, no build, host or setting detail,
and no database connection. The startup record about settings is one INFO line carrying the number
of documented keys and whether database-backed features are enabled; neither that line nor
`/healthz` or `/__release` carries a bounded setting value or the session secret. The settings
contract is written up in `demo/forum-docs/settings.md`.

## Determinism

The import preserves committed source bytes. Feature 001 reads a fixed, documented key list with
explicit integer bounds and a fixed Europe/Paris default; no model output or network call selects a
setting. Required CI runs on the exact PR head; publication confirms the resulting main SHA. Model
output cannot change the imported bundle or policy.

## Acceptance checks

With Java25 and Gradle9.8.2: `gradle -p demo/forum-app test quarkusBuild --no-daemon`.
Run the knowledge/link/health and factory tool checks in the repository instructions.
Migration/restore, least-privilege PostgreSQL and React/browser acceptance are separate gates.

Feature 001's acceptance runs with only a non-placeholder 32-byte `AGORA_SESSION_SECRET` and asserts
`GET /healthz` returns 200 without a database connection; removing the secret or supplying an
out-of-range numeric bound refuses startup naming only that key; an unknown environment variable is
ignored and appears in no log or rendered page. `DatabaselessHealthTest` supplies only the secret,
switches database-backed features off under both `agora.database.enabled` and
`%test.agora.database.enabled`, and points the datasource at a path that cannot exist, so any
connection attempt fails the suite; `ForumSettingsTest` asserts every bound, every refusal message
and the unstated-connection refusal.

## Out of scope

Database cutover, changing/deleting existing operator data, deployment, a mobile client and
claiming that the rewritten 100-feature catalog has already been implemented.

PostgreSQL/React CI uses a disposable PostgreSQL17 fixture and fixed programs under
`tools/product-web/`, outside the feature worker write scopes. React dependencies install
with scripts disabled before typecheck, Vitest and production assets; assets are packaged
by Quarkus. A missing web package is not a frontend qualification. The isolated Factory
checker remains the acceptance authority; CI fixture credentials are not product secrets.
