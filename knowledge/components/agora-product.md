---
type: Component
title: "Agora product baseline"
description: "Retained Quarkus backend: typed settings allowlist, database-less liveness and required CI checks, before PostgreSQL/React cutover."
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

The settings allowlist is typed (`demo/forum-app/src/main/java/local/aeion/forum/ForumSettings.java`):
`AGORA_SESSION_SECRET` (required, at least 32 bytes, placeholder refused), `AGORA_DATABASE_ENABLED`
(default false), `AGORA_REQUEST_MAX_BYTES` (1..16384, default 16384), `AGORA_TEXT_MAX_CODEPOINTS`
(1..4000, default 4000), `AGORA_RENDERED_MAX_BYTES` (1..32768, default 32768) and the
`agora.business.timezone` property (Europe/Paris). Each is validated once at startup; a refusal
names only the key and the rule, never the value, and unknown environment keys are ignored, never
logged. Database-less boot is the default: no migration, no outbox drain and no connection is
opened, so liveness answers with an unreachable datasource behind it.

## Outputs

The existing health, settings, accounts, session/CSRF and thread API, and packaged Quarkus app.
`GET /healthz` is liveness only: exactly `{"status":"ok"}`, no build, host or configuration values;
the release tag stays on `GET /__release`. Missing/invalid required secret refuses startup, as does
a bound outside its range. Product artifacts CI runs tests and packaging.

## Invariants

Product runtime credentials never enter browser code or the repository, and settings are never
logged or rendered. `/healthz` exposes liveness, not build or host details. Tests use disposable
databases. CI has read-only repository access; source checkout does not persist credentials.
Existing product tests cover refusal paths. Old SQLite receipts never certify PostgreSQL.

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
