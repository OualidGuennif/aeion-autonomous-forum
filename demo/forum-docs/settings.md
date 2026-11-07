# Agora forum settings — feature 001, app skeleton and settings

The deployment environment is allowlisted: the application reads only the keys below, ignores
every other variable, and never logs or renders a value. A required setting that is missing, or a
value that is outside its documented shape, refuses the start — the refusal names its key and
nothing else, so an operations log cannot become a mirror of the environment.

## The keys

| Environment key       | Property                    | Default   | Allowed shape                                   | Enforced at              |
| --------------------- | --------------------------- | --------- | ----------------------------------------------- | ------------------------ |
| `AGORA_SESSION_SECRET`| `agora.session.secret`      | none      | at least 32 bytes, not a known placeholder       | startup [`SessionSecret`](../forum-app/src/main/java/local/aeion/forum/SessionSecret.java) |
| `AGORA_REQUEST_MAX_BYTES` | `agora.request.max-bytes`   | `16384` | integer 1..16384                                 | every request body ([`RequestValidationFilter`](../forum-app/src/main/java/local/aeion/forum/RequestValidationFilter.java)) |
| `AGORA_TEXT_MAX_CODEPOINTS` | `agora.text.max-codepoints` | `4000` | integer 1..4000                                 | stored normalized text ([`ThreadService`](../forum-app/src/main/java/local/aeion/forum/ThreadService.java), [`SettingsResource`](../forum-app/src/main/java/local/aeion/forum/SettingsResource.java)) |
| `AGORA_RENDERED_MAX_BYTES` | `agora.rendered.max-bytes` | `32768` | integer 1..32768                                 | startup; carried for the rendering feature |
| `AGORA_DATABASE_ENABLED` | `agora.database.enabled`  | `false`   | `true` or `false`                                | startup ([`DatabaseMode`](../forum-app/src/main/java/local/aeion/forum/DatabaseMode.java)) |
| `AGORA_DATABASE_URL`  | `agora.database.url`        | absent    | required when the database plane is enabled      | startup (`DatabaseMode`) |
| `AGORA_DATABASE_USER` | `agora.database.user`       | absent    | required when the database plane is enabled      | startup (`DatabaseMode`) |
| `AGORA_DATABASE_PASSWORD` | `agora.database.password` | absent   | required when the database plane is enabled      | startup (`DatabaseMode`) |

The business timezone is part of this typed configuration but not an environment key: the property
`agora.time.business-zone` is fixed to `Europe/Paris`, and a different value refuses the start
([`StartupChecks`](../forum-app/src/main/java/local/aeion/forum/StartupChecks.java)). The request
byte cap is additionally bounded by the platform itself (`quarkus.http.limits.max-body-size`).

## Boot modes

* **Database-less (the default).** With `AGORA_DATABASE_ENABLED` absent or `false`, the deployment
  boots with only the session secret: no migration is initialized, no ORM connection is opened, and
  `/GET healthz` answers `200 {"status":"ok"}` even when the datasource points at something
  unreachable. The outbox drain and the consumer wait for the database-backed mode.
* **Database-backed.** With `AGORA_DATABASE_ENABLED=true` the committed database-backed plane
  applies (migrations, settings, accounts, threads, outbox), and the PostgreSQL connection settings
  above must all be provided: a database-backed deployment without them is half-configured and
  refuses to start. The settings are validated as present here; the connection engine itself is the
  separate PostgreSQL cutover feature (see [`agora-product.md`](../../knowledge/components/agora-product.md)).

## What a refusal looks like

Every refusal names one key (or the configured property) and the allowed range or shape:

* missing or placeholder secret — `session_secret_missing: AGORA_SESSION_SECRET is required …`
* short secret — `session_secret_too_short: AGORA_SESSION_SECRET must be at least 32 bytes …`
* non-integer or out-of-range cap — `bound_invalid: AGORA_REQUEST_MAX_BYTES must be an integer
  between 1 and 16384`
* non-boolean database flag — `database_flag_invalid: AGORA_DATABASE_ENABLED must be true or false`
* missing database setting while enabled — `database_setting_missing: AGORA_DATABASE_URL is
  required when AGORA_DATABASE_ENABLED=true`
* wrong business timezone — `business_timezone_invalid: agora.time.business-zone must be Europe/Paris`

No refusal ever echoes the offending value. Unknown environment variables do not cause refusals at
all: they are ignored, and they appear in neither logs nor responses.

## The health contract

`GET /healthz` returns exactly `{"status":"ok"}` with the enforcing security headers. It acquires no
database connection in any mode and exposes no release, runtime, host or configuration detail. The
sibling `GET /__release` stays an existing route and answers `{"status":"serving"}` for the same
reason.

## Proven by the tests

* [`HealthLivenessBootTest`](../forum-app/src/test/java/local/aeion/forum/HealthLivenessBootTest.java)
  boots with only the secret (plus one unknown key that must stay invisible) and pins the body byte
  for byte.
* [`ForumBoundsTest`](../forum-app/src/test/java/local/aeion/forum/ForumBoundsTest.java),
  [`DatabaseModeTest`](../forum-app/src/test/java/local/aeion/forum/DatabaseModeTest.java) and
  [`StartupChecksTest`](../forum-app/src/test/java/local/aeion/forum/StartupChecksTest.java) prove
  the defaults, the refusals and the fact that no value is echoed.
* [`ConfiguredCapsBootTest`](../forum-app/src/test/java/local/aeion/forum/ConfiguredCapsBootTest.java)
  boots with lowered caps and proves the byte cap boundary (413/401) and the codepoint accounting
  of the text cap.
* [`SessionSecretTest`](../forum-app/src/test/java/local/aeion/forum/SessionSecretTest.java) keeps
  the standing secret refusals.
