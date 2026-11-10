# Forum settings and boot (feature 001)

The application is a Quarkus/Java service. This page documents the settings a deployment supplies
and how the skeleton boots; the canonical specification lives in
`knowledge/components/agora-product.md`.

## The environment is an allowlist

Only the keys below are read. An unknown environment variable is ignored: it is never parsed,
never logged and never rendered by any endpoint, and it cannot become a configuration value.

| Key | Meaning | Default |
| --- | --- | --- |
| `AGORA_SESSION_SECRET` | Required, at least 32 bytes, never a placeholder. Signs session cookies. | none |
| `AGORA_DATABASE_ENABLED` | Database-backed features. `false` or absent boots database-less. | `false` |
| `AGORA_REQUEST_MAX_BYTES` | Request cap, integer 1..16384. | `16384` |
| `AGORA_TEXT_MAX_CODEPOINTS` | Normalized-text cap, integer 1..4000. | `4000` |
| `AGORA_RENDERED_MAX_BYTES` | Rendered-page cap, integer 1..32768. | `32768` |

A non-numeric or out-of-range cap, or a database flag that is neither `true` nor `false`, refuses
startup. Every refusal names only its key: `setting_out_of_bounds: AGORA_REQUEST_MAX_BYTES must be
an integer between 1 and 16384`. No configured value is ever repeated in a log message, an error
text, an API response or a page.

A missing or short `AGORA_SESSION_SECRET` refuses startup the same way, naming only that key.

The business timezone is `Europe/Paris` (`agora.time-zone`): the process default zone is pinned at
startup, and an unset container clock zone can never change a display. Timestamps on the wire stay
epoch nanoseconds, so a timezone change cannot rewrite history either.

## Liveness

`GET /healthz` is the liveness endpoint and answers exactly:

```json
{"status":"ok"}
```

It exposes no build or host details, no configuration values and opens no database connection; a
deployment starts with an invalid or unreachable datasource and still answers. Security headers
(feature 013) apply as everywhere else. The release label keeps its operator route: `GET __release`
answers `{"status":"serving","release":"…","runtime":"…",}` with the `AEION_RELEASE` label.

## Database-less boot, by default

With `AGORA_DATABASE_ENABLED` absent or `false`, starting with only `AGORA_SESSION_SECRET` works:

* every database-backed code path gates itself on the same boolean before a driver is touched, the
  datasource and Hibernate are fed the deactivation over their own configuration keys
  (`quarkus.datasource.active=false`, `quarkus.hibernate-orm.active=false`), and no database
  connection is opened at boot or during any request;
* migrations are not run, and the outbox is not drained (`/api/schema`, `/api/settings`,
  `/api/threads` and the auth endpoints answer 503 `{"error":"database_disabled"}`);
* liveness (`/healthz`) is unaffected: it never touches the database.

With `AGORA_DATABASE_ENABLED=true`, the database-backed behaviour is unchanged: the datasource and
Hibernate start, the migration runner applies the versioned SQL folder, and the outbox drains as
documented. A database-enabled deployment must then carry its datasource URL; a boot without one
refuses, naming `AGORA_DATABASE_ENABLED` and the URL key and no values. PostgreSQL connection
settings arrive with the cutover feature; until then the transition baseline keeps its SQLite URL
as that required connection setting.

## Boundaries of this page

Requests are still capped on the wire at 16 KiB (feature 013's global body limit, enforced before
any handler runs), and the typed caps above are validated at startup and tracked for the features
that consume them. Agora factory checks — not this repository's docs — remain the acceptance
authority for the feature.
