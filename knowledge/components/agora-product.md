---
type: Component
title: "Agora product baseline"
description: "Existing Quarkus backend and its required CI checks, before PostgreSQL/React cutover."
status: draft
sources:
  - resource: ../../demo/forum-app/
    title: "Committed Quarkus application"
  - resource: ../../demo/forum-docs/feature-001-settings.md
    title: "Application skeleton and settings"
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
Feature 001 adds the typed configuration around that application: a required session secret of at
least 32 bytes, bounded request, text and rendered caps, and a database that is off unless
`AGORA_DATABASE_ENABLED` asks for it.

## Outputs

The existing health, settings, accounts, session/CSRF and thread API, and packaged Quarkus app.
Missing/invalid required secret refuses startup. Product artifacts CI runs tests and packaging.
`GET /healthz` answers liveness only: `{"status":"ok"}`, no build detail and no database.

## Invariants

Product runtime credentials never enter browser code or the repository. Tests use disposable
databases. CI has read-only repository access; source checkout does not persist credentials.
Existing product tests cover refusal paths. Old SQLite receipts never certify PostgreSQL.
A configuration value is never logged or rendered, and a refusal names the key it refused
without the value it did not get.

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
