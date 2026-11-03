# Local Docker demo

This is a **local-only** image of the Quarkus forum. It binds to loopback, keeps its SQLite file
in a named volume and is never pushed to a registry or deployed to a VPS. The canonical source is
this product repository, not the observability demo's mirror.

## Build and run

From the product repository root:

```bash
export AGORA_SESSION_SECRET=$(openssl rand -hex 32)
docker compose -f demo/forum-app/compose.yaml up --build -d
```

The app refuses to start without a non-empty `AGORA_SESSION_SECRET`; the variable above is an
ephemeral local value, is not written to the repository and is not printed by these commands.
The Compose file publishes only `127.0.0.1:8081`, stores the database in the `forum-data` volume
and runs the container as UID/GID `10001`, not as root.

## Verify

```bash
curl -fsS http://127.0.0.1:8081/healthz
curl -sS -D - -o /dev/null http://127.0.0.1:8081/healthz
```

Expected health body:

```json
{"status":"ok","release":"local-docker","runtime":"25.0.5"}
```

Expected security headers on both `/healthz` and a refusal such as
`/api/threads/not-a-number`:

```text
Content-Security-Policy: default-src 'none'; frame-ancestors 'none'; base-uri 'none'; form-action 'none'
X-Content-Type-Options: nosniff
Referrer-Policy: no-referrer
Strict-Transport-Security: max-age=31536000; includeSubDomains
```

`Content-Security-Policy-Report-Only` must be absent. The JSON API has no HTML surface, which is
why the pinned 013 brief scopes out nonces and a violation-report endpoint.

## Provenance of feature 013

The container includes `SecurityHeadersFilter.java` and its test copied from the **offline**
feature-013 qualification artifact (build `4e40fcfff3b9486bb22e17271ff985a9`, artifact digest
`6b78a27ad99b073c2a078167dad6e530473af58312f6ab6ad752d8f9e76741b1`). That run used the injected
deterministic builder, the real protected Gradle check and a fake forge; it spent no money and
did not publish. This is a local demonstration artifact, not evidence of a paid model build.

## Stop and reset

```bash
docker compose -f demo/forum-app/compose.yaml down
# Delete the local database as well:
docker compose -f demo/forum-app/compose.yaml down -v
```

## Build without Compose

The build context is the product repository root:

```bash
docker build -f demo/forum-app/Dockerfile -t aeion-autonomous-forum:local .
docker run --rm -p 127.0.0.1:8081:8081 \
  -e AGORA_SESSION_SECRET="$(openssl rand -hex 32)" \
  -e AEION_RELEASE=local-docker \
  aeion-autonomous-forum:local
```

A named volume is recommended for persistence; the Compose file already provides one.
