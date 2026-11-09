---
type: Component
title: "Application skeleton and settings"
description: "Feature 001: the typed configuration of the forum, its fail-closed refusals and a liveness-only health endpoint."
status: draft
sources:
  - resource: ../../demo/forum-app/src/main/java/local/aeion/forum/ForumSettings.java
    title: "Typed configuration with bounded defaults"
  - resource: ../../demo/forum-app/src/main/java/local/aeion/forum/DatabaseConnection.java
    title: "Database connection settings"
  - resource: ../../demo/forum-app/src/main/java/local/aeion/forum/SessionSecret.java
    title: "Fail-closed session secret"
  - resource: ../../demo/forum-app/src/main/java/local/aeion/forum/StartupChecks.java
    title: "Startup refusal order"
  - resource: ../../demo/forum-app/src/main/java/local/aeion/forum/ForumHealth.java
    title: "Liveness endpoint"
  - resource: ../../demo/forum-app/src/main/java/local/aeion/forum/DatabaseModeFilter.java
    title: "Database-backed routes when the database is off"
---

# Application skeleton and settings

## Intent

Feature 001 gives the forum a typed configuration, refuses a deployment that is not configured,
and answers a probe that does not need a database. The Quarkus application and its Gradle build
are kept: this feature adds the settings around them, it does not replace the backend.

The rules are small and each one is checkable:

- a required setting that is missing or out of range stops the **start**, not the first request;
- a refusal names the **key** it refused and never the value it did not get;
- `GET /healthz` answers liveness only: one field, no build detail, no host detail, no database;
- an environment variable nobody reads is ignored, and it appears in no log and on no page.

## Data sources

| Source | What is read | Owner | Freshness |
| --- | --- | --- | --- |
| `AGORA_SESSION_SECRET` | the session key | the deployment | at every start |
| `AGORA_DATABASE_ENABLED` | whether the database-backed features run | the deployment | at every start |
| `AGORA_DATABASE_URL`, `AGORA_DATABASE_USER`, `AGORA_DATABASE_PASSWORD` | the database connection | the deployment | at every start |
| `AEION_SQLITE_PATH` | the transitional baseline's database file | the deployment | at every start |
| `AGORA_REQUEST_MAX_BYTES` | the request body ceiling | the deployment | at every start |
| `AGORA_TEXT_MAX_CODEPOINTS` | the cap of a normalized text | the deployment | at every start |
| `AGORA_RENDERED_MAX_BYTES` | the cap of a rendered text | the deployment | at every start |
| `agora.business.timezone` | the business timezone (`Europe/Paris`) | the product | with the build |

The settings are read from the environment through the Quarkus configuration. Only the keys above
are read; the rest of the environment is invisible to the application.

## Inputs

| Key | Default | Accepted | Refusal |
| --- | --- | --- | --- |
| `AGORA_SESSION_SECRET` | none | at least 32 bytes, not a placeholder | `session_secret_missing`, `session_secret_too_short`, `session_secret_placeholder` |
| `AGORA_DATABASE_ENABLED` | `false` | `true`/`false` (also `yes`/`no`, `1`/`0`, `on`/`off`) | `settings_database_enabled_invalid` |
| `AGORA_REQUEST_MAX_BYTES` | `16384` | 1..16384 | `settings_request_max_bytes_invalid` |
| `AGORA_TEXT_MAX_CODEPOINTS` | `4000` | 1..4000 | `settings_text_max_codepoints_invalid` |
| `AGORA_RENDERED_MAX_BYTES` | `32768` | 1..32768 | `settings_rendered_max_bytes_invalid` |
| `AGORA_DATABASE_URL` | none | a JDBC URL | `settings_database_url_missing`, `settings_database_url_invalid` |
| `AGORA_DATABASE_USER`, `AGORA_DATABASE_PASSWORD` | none | required for a PostgreSQL URL | `settings_database_credentials_missing` |

An empty value is read as an absent value: the default applies. A value that is present but is not
a number, or is outside its range, refuses the start.

## Outputs

- A deployment that starts without a database serves `GET /healthz` with `200` and the body
  `{"status":"ok"}`. It applies no migration, opens no connection and runs no outbox drain.
- A deployment whose `AGORA_SESSION_SECRET` is missing or too short does not start. The refusal
  names `AGORA_SESSION_SECRET` and nothing else, and never a character of the value.
- A deployment whose bounds are invalid does not start. The refusal names the key and the range it
  accepts.
- The database-backed routes (`/api/**`) answer `503` with `{"error":"database_disabled"}` while
  the database is off, instead of failing deeper with an error that says nothing.
- The request body ceiling, the text cap and the rendered cap are read from the settings instead
  of being constants in the code.

## Invariants

1. A refusal names the key it refused and never a value (`SessionSecretTest`, `ForumSettingsTest`,
   `DatabaseConnectionTest`).
2. The session secret is checked before every other setting, so a deployment that lost it is told
   about that key alone (`StartupChecks`).
3. `GET /healthz` answers exactly `{"status":"ok"}` (`ForumHealthTest`, the frozen acceptance
   contract).
4. The health endpoint opens no database connection (`DatabaseLessBootTest`).
5. An environment variable that is not read changes nothing and appears in no log and on no page.
6. No setting value is written to a log, a page or an exception message.

## Determinism

Explicit bounds, fixed defaults and a frozen clock in the tests. The refusals are decided from
the configuration alone, so the same environment always produces the same start. Nothing in this
feature reads the wall clock, the network or a model output.

## Acceptance checks

Run the product tests and the supervisor's own external check:

- `gradle -p demo/forum-app test` — the suite above, including the database-less boot.
- The frozen acceptance contract: with only a non-placeholder 32-byte `AGORA_SESSION_SECRET`, the
  packaged application answers `200` and `{"status":"ok"}` on `GET /healthz`.
- Restarting the packaged application without `AGORA_SESSION_SECRET`, with a short secret and
  with an invalid bound refuses the start and names the key. The logs carry no value.

## Out of scope

The PostgreSQL cutover (the driver is not part of this build), database migrations, forum
behaviour, authentication changes, the React face and the deployment of any of it. The
`AEION_FORUM_PORT` default follows the frozen acceptance contract, which addresses the forum on
port 8080; the local Compose demo still pins `QUARKUS_HTTP_PORT=8081` and is unchanged.

Because the database-backed features are off unless a deployment asks for them, the local Compose
demo needs `AGORA_DATABASE_ENABLED=true` in its environment to keep its SQLite file in use.
