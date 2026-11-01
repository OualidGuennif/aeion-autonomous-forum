# 06 — Docker and Compose

## Plan (and what was not done)
New: `app/Dockerfile`, `app/compose.yaml`, `app/.dockerignore`, and the spec
`knowledge/components/idea-board-runtime.md` with its index row. No app code changed. Not done: a
registry push, TLS, a reverse proxy, multi-replica KV.

## Order followed
1. Spec first: the image, the service, the volume, the four invariants and their commands.
2. Files, then the build, then the acceptance commands — the container is the test here.

## What went in
- **Image** `denoland/deno:2.9.8` (exact tag), `/app` and `/data` created for the `deno` user before
  `USER deno`, dependencies installed with `deno install --frozen` from `deno.json` + `deno.lock`
  before the source is copied, one `deno task build`, then `deno task start`.
- **Compose**: one `web` service, `8000:8000`, `KV_PATH=/data/ideas.sqlite3`, the named volume
  `ideas` on `/data`, and a health check that runs `deno eval` with `--allow-net` on the app's own
  port — no `curl` added to the image.
- **`.dockerignore`** keeps `node_modules`, `_fresh`, `data`, `.env*` and VCS files out of the
  context.

## Where I got stuck, and what fixed it
1. **`deno install` as the `deno` user could not write `/app`.** `WORKDIR /app` is created
   root-owned; `COPY --chown` fixes files, not the directory. First build failed with
   `failed to create directory '/app/node_modules/.deno/node_modules': Permission denied`. Fixed by
   creating `/app` and `/data` and chowning them to `deno` in one root `RUN` before switching user.

## Verification (the container is the test)
```text
docker compose build                      PASS (image app-web built)
docker compose up -d                       healthy after ~12 s
docker compose ps                          Up (healthy) 0.0.0.0:8000->8000/tcp
curl localhost:8000/api/health             200 {"status":"ok","version":"0.1.0"}
docker compose exec -T web id -u           1993 (the deno user, not 0)
docker compose exec -T web sh -c 'command -v curl || echo none'   none
POST /api/ideas {"title":"Survives restart"}  201
docker compose down && docker compose up -d   healthy again
GET /api/ideas                              ["Survives restart"]   (named volume kept it)
deno install --frozen with a mismatched deno.json   exit 1
deno install --frozen with the lock restored        exit 0
docker compose down -v                      volume app_ideas removed
deno fmt --check / lint / check / test -A   PASS / PASS / PASS / 79 passed
```
Factory checks in the copy: `knowledge-check` PASS, `doc-links` PASS (108 links), `repo-health` PASS.

## Guard refusals
None this round.

## Feedback
None new. The permission failure is a Docker-lifecycle detail, not a factory rule.

## Result

```text
Result:          done
Tests:           79 passed, 0 failed (deno test -A) plus the container checks above, all passing
Factory checks:  knowledge-check PASS, doc-links PASS, repo-health PASS
Guard refusals:  0
Human help:      0
Feedback:        none
Elapsed:         ~45 minutes
```
