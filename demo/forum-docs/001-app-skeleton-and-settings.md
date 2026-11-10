# Feature 001 — App skeleton and settings

Status: implemented against the frozen factory check `agora-001` (this run is attempt 1, 2026-11-10).
Scope: the retained Quarkus/Java backend [demo/forum-app](../forum-app/); no new framework, no
database cutover, no forum-behaviour change.

## Boot modes

Database-less boot is the default and is selected explicitly with `AGORA_DATABASE_ENABLED=false`.
The process starts on the session secret alone: no migration, no outbox drain, and no database
connection is opened — the liveness probe answers even with an invalid or unreachable datasource
configured behind it. With `AGORA_DATABASE_ENABLED=true` the database-backed behaviour is kept
unchanged: migrations run at boot and the outbox drains.

| Env key | Property | Default | Accepted |
| --- | --- | --- | --- |
| `AGORA_SESSION_SECRET` | `agora.session.secret` | — | ≥ 32 bytes, not a placeholder; required |
| `AGORA_DATABASE_ENABLED` | `agora.database.enabled` | `false` | `true/false/1/0/yes/no/on/off`; anything else is invalid |
| `AGORA_REQUEST_MAX_BYTES` | `agora.requests.max-bytes` | `16384` | integer 1..16384 |
| `AGORA_TEXT_MAX_CODEPOINTS` | `agora.text.max-codepoints` | `4000` | integer 1..4000 |
| `AGORA_RENDERED_MAX_BYTES` | `agora.rendered.max-bytes` | `32768` | integer 1..32768 |
| — | `agora.business.timezone` | `Europe/Paris` | a valid IANA zone; property-only this feature |

`ForumSettings` is the single typed allowlist: every key above is validated once at startup
(`StartupChecks`), a refusal names only the key and the rule — never the offending value — and an
environment key outside the allowlist is ignored, never logged and never rendered.

## Endpoints in this feature

* `GET /healthz` — liveness only. Returns exactly `{"status":"ok"}`, opens no database connection,
  and exposes no build, host or configuration values.
* `GET /__release` — a status page, not a probe: release tag and runtime version belong here.

## Refusals proven by tests

* missing or short `AGORA_SESSION_SECRET`, or a placeholder value, refuses startup naming only the key
  (`SessionSecretTest`, `StartupChecks`, and the post-merge external restart checks);
* an out-of-range or non-integer bound refuses startup (`ForumSettingsTest`);
* the packaged application boots database-less on the secret alone and serves `{"status":"ok"}`
  (`DatabaseLessBootTest` with `DatabaseLessProfile`).

## Known boundary of this feature

The request envelope (413 above 16 KiB) is enforced by `RequestValidationFilter` and
`quarkus.http.limits.max-body-size=16K` at the documented default `16384`; converging that filter
with an operator-lowered `AGORA_REQUEST_MAX_BYTES`, and attaching the text/rendered caps to their
future consumers, belong to the features that own those surfaces. The connection settings for the
database-backed mode are those of its platform (SQLite today, PostgreSQL at cutover); an operator
must supply them, and a database-backed deployment that cannot reach its database fails its first
migration attempt loudly. `DOCKER.md` still shows the old multi-key health body and is outside this
feature's write scope: the frozen contract for `/healthz` is the single-key document above.
