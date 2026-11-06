---
type: Component
title: "Agora product baseline"
description: "Existing Quarkus backend and its required CI checks, before PostgreSQL/React cutover."
status: draft
sources:
  - resource: ../../demo/forum-app/
    title: "Committed Quarkus application"
  - resource: ../../demo/forum-docs/settings.md
    title: "Feature 001 settings and liveness"
  - resource: ../../.github/workflows/ci.yml
    title: "Required product checks"
---

# Agora product baseline

## Intent

Retain the existing Quarkus/Java API in the product repository. This bootstrap imports the
committed backend; it does not implement the newly specified PostgreSQL/React architecture.
Feature 001 keeps that backend and adds the typed, allowlisted boot settings plus a liveness
probe that needs no database.

## Data sources

The prior product commit a50d6075b7aecff8f7c54fa47b5f385c16f4ccbb supplies exact source bytes.
Supervisor-owned isolated checks and two independent real model reviews gate the import.

## Inputs

Java25, Gradle9.8.2, Quarkus3.40.1 and a session secret from private runtime configuration.
The transition baseline uses SQLite; disposable JUnit resources own test databases.
Feature 001 adds and validates only the allowlisted keys: `AGORA_SESSION_SECRET` (required, at
least 32 bytes), `AGORA_DATABASE_ENABLED` (default false), `AGORA_REQUEST_MAX_BYTES` (1..16384),
`AGORA_TEXT_MAX_CODEPOINTS` (1..4000) and `AGORA_RENDERED_MAX_BYTES` (1..32768). An environment
variable outside that list is not read by the feature.

## Outputs

The existing health, settings, accounts, session/CSRF and thread API, and packaged Quarkus app.
`GET /healthz` is liveness only and returns exactly `{"status":"ok"}` with no release, runtime,
host or setting. Missing, short or placeholder session secrets and invalid numeric bounds refuse
startup. With `AGORA_DATABASE_ENABLED` absent or false the application boots without a database:
no migration or outbox drain runs, the persistence unit is inactive and no connection is
attempted, health still answers with an unreachable datasource, and `/api/*` answers 503
`database_disabled` before resolving a connection. With it true, the existing database-backed
behavior is unchanged.

## Invariants

Product runtime credentials never enter browser code or the repository. Settings are never logged
or rendered; a refusal names only the key. Tests use disposable databases.
CI has read-only repository access; source checkout does not persist credentials. Existing product
tests cover refusal paths. Old SQLite receipts never certify PostgreSQL.

## Determinism

The import preserves committed source bytes. Required CI runs on the exact PR head; publication
confirms the resulting main SHA. Model output cannot change the imported bundle or policy.
Settings are parsed once at startup with explicit bounds; the business timezone is the fixed
`Europe/Paris` zone, not the host default.

## Acceptance checks

With Java25 and Gradle9.8.2: `gradle -p demo/forum-app test quarkusBuild --no-daemon`.
Run the knowledge/link/health and factory tool checks in the repository instructions.
The independent `agora-001` check restarts the packaged app with only a session secret, with a
missing or short secret, with invalid bounds and with unknown environment variables, and asserts
that `GET /healthz` answers 200 without a database and that logs disclose no value.
Migration/restore, least-privilege PostgreSQL and React/browser acceptance are separate gates.

## Out of scope

Database cutover, changing/deleting existing operator data, deployment, a mobile client and
claiming that the rewritten 100-feature catalog has already been implemented.

PostgreSQL/React CI uses a disposable PostgreSQL17 fixture and fixed programs under
`tools/product-web/`, outside the feature worker write scopes. React dependencies install
with scripts disabled before typecheck, Vitest and production assets; assets are packaged
by Quarkus. A missing web package is not a frontend qualification. The isolated Factory
checker remains the acceptance authority; CI fixture credentials are not product secrets.
