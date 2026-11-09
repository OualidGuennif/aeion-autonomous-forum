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

Feature 001 keeps the settings allowlisted and bounded: `AGORA_SESSION_SECRET` (>=32 bytes, no
placeholder) is required, `AGORA_DATABASE_ENABLED` (default `false`) gates database-backed features
and makes a connection setting required while it is on (`AEION_SQLITE_PATH`, or a
`quarkus.datasource.jdbc.url` the operator stated instead of the shipped development one), and the
three numeric caps are `AGORA_REQUEST_MAX_BYTES` (1..16384, default 16384),
`AGORA_TEXT_MAX_CODEPOINTS` (1..4000, default 4000) and `AGORA_RENDERED_MAX_BYTES` (1..32768,
default 32768), with `AGORA_BUSINESS_TIMEZONE` defaulting to `Europe/Paris`. `ForumSettings` validates
them and `StartupChecks` checks the secret first and the connection last, so an invalid range refuses
startup and names only the key. Any other environment variable is not looked up.

## Outputs

The existing health, settings, accounts, session/CSRF and thread API, and packaged Quarkus app.
Missing/invalid required secret refuses startup. A database-less boot (the default) serves liveness
without opening a connection: migrations, the outbox drain and the event consumer are skipped and
the datasource is looked up only when a database-backed route is called. Product artifacts CI runs
tests and packaging.

## Invariants

Product runtime credentials never enter browser code or the repository. Tests use disposable
databases. CI has read-only repository access; source checkout does not persist credentials.
Existing product tests cover refusal paths. Old SQLite receipts never certify PostgreSQL.

A required setting that is missing, unparseable or outside its bound stops the process instead of
being repaired by a default, and its refusal names the key and the range but never the value that
was supplied. `GET /healthz` is liveness only: one status field, no build, host or setting detail,
and no database connection. The settings contract is written up in `demo/forum-docs/settings.md`.

## Determinism

The import preserves committed source bytes. Required CI runs on the exact PR head; publication
confirms the resulting main SHA. Model output cannot change the imported bundle or policy.

## Acceptance checks

With Java25 and Gradle9.8.2: `gradle -p demo/forum-app test quarkusBuild --no-daemon`.
Run the knowledge/link/health and factory tool checks in the repository instructions.
Migration/restore, least-privilege PostgreSQL and React/browser acceptance are separate gates.

## Out of scope

Database cutover, changing/deleting existing operator data, deployment, a mobile client and
claiming that the rewritten 100-feature catalog has already been implemented.

PostgreSQL/React CI uses a disposable PostgreSQL17 fixture and fixed programs under
`tools/product-web/`, outside the feature worker write scopes. React dependencies install
with scripts disabled before typecheck, Vitest and production assets; assets are packaged
by Quarkus. A missing web package is not a frontend qualification. The isolated Factory
checker remains the acceptance authority; CI fixture credentials are not product secrets.
