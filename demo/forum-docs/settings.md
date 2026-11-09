# Settings and startup (feature 001)

The forum reads a short list of settings and refuses to start when one of them is wrong. Refusing
is the feature: a deployment that comes up half-configured is reachable, a probe calls it alive,
and the mistake surfaces later as damaged data or a session key that is really a placeholder.

## The keys the application reads

| Environment key | Meaning | Range | Default | Read by |
| --- | --- | --- | --- | --- |
| `AGORA_SESSION_SECRET` | Signs sessions and CSRF tokens | at least 32 bytes, no placeholder | none — required | `SessionSecret` |
| `AGORA_DATABASE_ENABLED` | Turns database-backed features on | `true` or `false` | `false` | `ForumSettings`, `DatabaseFeatureFilter` |
| `AEION_SQLITE_PATH` | The database this baseline opens | non-blank, required while the features above are on | none | `ForumSettings`, datasource |
| `AGORA_REQUEST_MAX_BYTES` | Largest request body the application may accept | 1..16384 bytes | 16384 | `ForumSettings` |
| `AGORA_TEXT_MAX_CODEPOINTS` | Longest normalized stored text | 1..4000 codepoints | 4000 | `ForumSettings`, `SettingsResource` |
| `AGORA_RENDERED_MAX_BYTES` | Budget for a rendered page | 1..32768 bytes | 32768 | `ForumSettings` |
| `AGORA_BUSINESS_TIMEZONE` | The business calendar days are counted in | a region-based zone id | `Europe/Paris` | `ForumSettings` |

`SessionSecret` owns the secret; `ForumSettings` owns the rest, and `StartupChecks` builds both
while the application is starting. Nothing else in the environment is looked up: an unknown key is
not read, not logged and not rendered.

The last two settings are declared and bounded here before anything can invent its own number. This
backend has no server-side renderer and no business-day rule yet, so `AGORA_RENDERED_MAX_BYTES` and
`AGORA_BUSINESS_TIMEZONE` are read as validated values (`renderedMaxBytes()`, `businessZone()`) by the
feature that renders and the feature that counts a day; a value outside its range stops the process on
the day it is configured, not on the day it is first used. The request ceiling is the same contract:
the transport ships at its documented 16 KiB maximum, and a deployment that asks to be wider is
refused at startup rather than given a wider forum by a config file.


## What each refusal says

A refusal names one key and the range that key may take, and never repeats the value that was
supplied — an operator who typed it already knows it, and a log is read by other people.

```text
session_secret_missing: AGORA_SESSION_SECRET is required and was not provided
session_secret_too_short: AGORA_SESSION_SECRET must be at least 32 bytes, got 12
session_secret_placeholder: AGORA_SESSION_SECRET must not be a placeholder value
database_connection_missing: AEION_SQLITE_PATH or quarkus.datasource.jdbc.url is required when
                             AGORA_DATABASE_ENABLED is true
setting_not_an_integer: AGORA_REQUEST_MAX_BYTES must be a whole number between 1 and 16384
setting_out_of_range: AGORA_TEXT_MAX_CODEPOINTS must be between 1 and 4000
setting_not_a_boolean: AGORA_DATABASE_ENABLED must be true or false
setting_invalid: AGORA_BUSINESS_TIMEZONE must be a region-based timezone such as Europe/Paris
```

`session_secret_too_short` is the one message that carries a length: it is a count, not a value. The
connection refusal is the last thing `StartupChecks` asks, so a deployment that is missing the secret
is told about the secret and nothing else.


## Database-less boot

`AGORA_DATABASE_ENABLED` is off unless it is turned on, so a deployment that supplies only a real
session secret starts, serves `GET /healthz` and holds no database connection at all. In that mode
the application does not run migrations, does not drain the outbox and does not subscribe to the
thread-events topic.

It also refuses the routes that would have needed one. Every data route of this application lives
under `/api/` — settings, accounts and sessions, threads, and the migration ledger — and each of them
reaches the database through the datasource, whose shipped development value is
`jdbc:sqlite:/tmp/agora.db`. Serving those routes in a database-less boot would mean creating that
file on a machine that was started to answer a probe, so `DatabaseFeatureFilter` refuses the whole
`/api/` surface before the request body is read, before authentication and before the pool is asked
for a connection:

```console
$ curl -sS -w '\nstatus %{http_code}\n' "http://127.0.0.1:${AEION_FORUM_PORT:-8081}/api/threads"
{"error":"database_not_configured"}
status 503
```

One status code and one fixed word, no path, no URL and no setting value — the same rule the startup
refusals follow. Liveness is deliberately outside the gate: `/healthz` and `/__release` answer whatever
the deployment states about a database, which is exactly the point of a minimal boot. Turn the features
on with a stated connection and the gate opens: no data route behaves differently from before.

Turned on, the flag makes the connection a required setting. Stating it either way satisfies the
check — `AEION_SQLITE_PATH` for this baseline, or `quarkus.datasource.jdbc.url` for a deployment that
configures its datasource directly and for the PostgreSQL URL that arrives with the cutover. What is
refused is the case that used to be silent: features on, nothing stated, and the application quietly
opening the `jdbc:sqlite:/tmp/agora.db` it ships for developers. The datasource line itself is
unchanged, so a deployment that states its connection behaves exactly as before.

The work that would touch a database asks the same question the refusal asks (`databaseWorkRequested()`:
features on *and* a stated connection), so a deployment that is about to be refused never opens the
development file first and leaves a half-migrated forum in somebody's `/tmp`.

The test suite runs database-enabled by default (`%test.agora.database.enabled=true`) against the
ephemeral database each class creates, and `DatabaselessHealthTest` overrides both names — and points
the datasource at a directory that cannot exist — to prove the minimal boot.

## Liveness

```console
$ curl -fsS "http://127.0.0.1:${AEION_FORUM_PORT:-8081}/healthz"
{"status":"ok"}
```

One field. A release name, a runtime version or any setting value would turn the endpoint a probe
reads into a page somebody has to trust, so they are not there. Which release is serving is a
separate route, `GET /__release`.

## Follow-ups outside this change

`demo/forum-app/DOCKER.md` still shows the old two-extra-field health body; that file is not in this
change's write scope. A compose deployment that wants the forum in its volume must now set
`AGORA_DATABASE_ENABLED=true` next to `AEION_SQLITE_PATH`, since the flag defaults to off.

`AGORA_REQUEST_MAX_BYTES` is bounded and refused at startup here, and the transport ceiling in
`application.properties` stays at its shipped 16 KiB — the maximum this range allows. The route-level
refusal lives in `RequestValidationFilter`, whose behaviour is specified by
`knowledge/components/request-validation.md`; narrowing that ceiling below 16384 bytes belongs to a
change that edits that document in the same pull request, not to this one.
