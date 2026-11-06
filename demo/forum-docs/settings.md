# Forum settings and liveness

Feature 001 keeps the committed Quarkus/Java application and its Gradle build. It adds one typed,
allowlisted set of boot settings and makes `GET /healthz` a liveness probe that never touches a
database. There is no second backend and no new framework.

## Allowlisted keys

| Environment key | Configuration property | Default | Accepted values |
| --- | --- | --- | --- |
| `AGORA_SESSION_SECRET` | `agora.session.secret` | none, required | at least 32 UTF-8 bytes; not a placeholder |
| `AGORA_DATABASE_ENABLED` | `agora.database.enabled` | `false` | `true` or `false` |
| `AGORA_REQUEST_MAX_BYTES` | `agora.request.max-bytes` | `16384` | integer `1..16384` |
| `AGORA_TEXT_MAX_CODEPOINTS` | `agora.text.max-codepoints` | `4000` | integer `1..4000` |
| `AGORA_RENDERED_MAX_BYTES` | `agora.rendered.max-bytes` | `32768` | integer `1..32768` |

These are the only keys this feature adds or validates. An environment variable outside this list
is not read by the feature and is never logged or rendered. The existing deployment keys
(`AEION_FORUM_PORT`, `AEION_SQLITE_PATH`, `AEION_RELEASE`, the Kafka keys and the migration
location) keep their documented behavior.

## Boot behavior

- A missing, blank, placeholder or shorter-than-32-byte `AGORA_SESSION_SECRET` refuses startup.
  The refusal names `AGORA_SESSION_SECRET` and does not echo the supplied value.
- A bound that is not an integer, or is outside its range, refuses startup. The refusal names the
  key only; the ranges are the ones in the table above. An `AGORA_DATABASE_ENABLED` value other
  than `true`/`false` refuses startup the same way.
- `AGORA_DATABASE_ENABLED` absent or `false` is a database-less deployment. It needs nothing but
  the session secret: migrations are not applied, the outbox is not drained, the Kafka consumer
  does not start, the Hibernate persistence unit is inactive and no database connection is
  opened. `GET /healthz` still answers when the configured datasource is invalid or unreachable,
  and any `/api/*` request is refused with `503 database_disabled` before it can resolve an
  entity manager or a connection.
- `AGORA_DATABASE_ENABLED=true` keeps the existing database-backed behavior: SQLite, Hibernate,
  the versioned migrations, the transactional outbox and the Kafka publisher/consumer.
- `Europe/Paris` is the forum's one business timezone. Stored times are epoch nanoseconds; a
  renderer must format them in that zone rather than in the host's default.

## Health contract

```http
GET /healthz
200 {"status":"ok"}
```

The body is exactly one field. It carries no release, runtime, host, build or setting, and the
handler performs no database call. The protected acceptance compares the body as a JSON object, so
an extra field is a regression even when `status` still says `ok`.

## Limits in effect

- **Requests.** `RequestValidationFilter` reads at most `AGORA_REQUEST_MAX_BYTES + 1` bytes and
  refuses a larger body with `413 request_too_large` before authentication or JSON coercion. The
  Quarkus HTTP ceiling remains 16 KiB, the maximum the key permits.
- **Text.** Thread bodies and stored setting values are bounded by `AGORA_TEXT_MAX_CODEPOINTS`
  code points, not UTF-16 units: one emoji counts once.
- **Rendered output.** `AGORA_RENDERED_MAX_BYTES` is validated at boot and exposed by
  `AgoraSettings` for the layer that renders pages; this backend-only baseline has no HTML
  renderer to apply it to yet.

## Verification

```bash
gradle -p demo/forum-app test quarkusBuild --no-daemon
```

The product tests cover the defaults, both ends of every range, non-integer and out-of-range
refusals, and a database-less boot whose datasource cannot exist. The supervisor's independent
`agora-001` check restarts the packaged application with only a session secret, with a missing or
short secret, with invalid bounds and with unknown environment variables, and verifies that logs
never disclose a value.

## Notes for operators

A database-backed launch must pass `AGORA_DATABASE_ENABLED=true` explicitly; the default is the
database-less liveness mode required for a boot with only the session secret.
