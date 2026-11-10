---
type: Component
title: "Agora product baseline"
description: "Existing Quarkus backend, its typed settings and required CI checks, before PostgreSQL/React cutover."
status: draft
sources:
  - resource: ../../demo/forum-app/
    title: "Committed Quarkus application"
  - resource: ../../demo/forum-app/src/main/java/local/aeion/forum/AgoraSettings.java
    title: "Typed bounded settings (feature 001)"
  - resource: ../../demo/forum-docs/settings.md
    title: "Settings and boot documentation"
  - resource: ../../.github/workflows/ci.yml
    title: "Required product checks"
---

# Agora product baseline

## Intent

Retain the existing Quarkus/Java API in the product repository, and keep its skeleton current
(feature 001): the committed backend is reused, not replaced by a second framework. This bootstrap
still does not implement the newly specified PostgreSQL/React architecture; it keeps the same
entrypoint and maps only the documented configuration keys.

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
keys are ignored. A database-enabled boot requires its datasource URL; PostgreSQL connection
settings arrive with the cutover feature. Until then the transition baseline keeps SQLite, and
disposable JUnit resources own test databases (database-backed through `%test` profile values).

## Outputs

The existing health, settings, accounts, session/CSRF and thread API, and packaged Quarkus app,
plus the typed bounded settings bean and its documented boot contract in `demo/forum-docs/`.
`GET /healthz` is liveness only: exactly `{"status":"ok"}`, no database connection, no build or
host detail. Missing/invalid required secret or invalid numeric caps refuse startup, each refusal
naming only its key. Product artifacts CI runs tests and packaging.

## Invariants

Product runtime credentials never enter browser code or the repository. Tests use disposable
databases. CI has read-only repository access; source checkout does not persist credentials.
Existing product tests cover refusal paths. Old SQLite receipts never certify PostgreSQL.
Settings are never logged or rendered: refusals name the key, never the value. A database-less
boot initializes no datasource, no ORM and no migrations; its database-backed endpoints answer
503 and liveness stays independent of the datasource.

## Determinism

The import preserves committed source bytes. Required CI runs on the exact PR head; publication
confirms the resulting main SHA. Model output cannot change the imported bundle or policy.
The settings bounds, defaults and refusals are fixed code, not environment-shaped.

## Acceptance checks

With Java25 and Gradle9.8.2: `gradle -p demo/forum-app test quarkusBuild --no-daemon`.
With only a non-placeholder 32-byte `AGORA_SESSION_SECRET`, the packaged app answers
`GET /healthz` with 200 and `{"status":"ok"}` without opening a database connection; removing the
secret or supplying an invalid numeric bound stops startup naming only that key; an unknown
environment variable is ignored and absent from logs and rendered pages.
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
