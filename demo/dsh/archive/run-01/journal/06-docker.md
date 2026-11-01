# Round 06 — Docker and Compose

**Agent:** dsh (DeepSeek Harness) 0.2.1-rc.3, one session. **Date:** 2026-10-29.
**Brief:** [`plan/06-docker.md`](../../../../plan/06-docker.md).

## Plan

Files: `app/Dockerfile`, `app/compose.yaml`, `app/.dockerignore` (all new);
`knowledge/components/idea-board-runtime.md` (new) and the components index; the copy's
`CHANGELOG.md` and `work/current.md`. Out of scope: a registry push, TLS, a reverse proxy,
multi-replica KV.

## What was built

- `FROM denoland/deno:2.9.8`, the exact release the lock file was resolved with.
- `COPY deno.json deno.lock` then `deno install --frozen`, so a source edit does not refetch and a
  dependency without a lock update fails the build.
- `COPY . .`, `chmod -R a+rX /app` (the host files are mode 600), `deno task build`, one build in the
  image; `USER deno`; `CMD ["deno","task","start"]` → `server.ts` reads `PORT`.
- `RUN mkdir -p /data && chown deno:deno /data`: a named volume inherits that ownership when it is
  first created, so the non-root user can write the KV file.
- `compose.yaml`: one `web` service, `8000:8000`, `KV_PATH=/data/ideas.sqlite3`, the `ideas` volume on
  `/data`, and a health check `deno eval --allow-net=127.0.0.1:8000` calling `/api/health` — the
  app's own runtime, no `curl` in the image.
- `.dockerignore`: `_fresh`, `node_modules`, `data`, `*.sqlite3`, `.git`, the env file, and the build
  recipe itself (Dockerfile, compose.yaml, .dockerignore), so the image is the app.

## Two snags

1. **The sandbox blocks `~/.docker`.** `docker compose up -d --build` died writing buildx's activity
   file, and a fresh `DOCKER_CONFIG` lost the compose/buildx plugins. Fixed with
   `DOCKER_CONFIG=/tmp/dsh-docker` plus symlinks to the real plugins; every compose command in this
   round used it. Filed as FB-008.
2. **Mode 600 leaked into the image.** The first container restarted with
   `deno task couldn't find deno.json`: `COPY` preserves the host's private modes and the `deno` user
   could not read the file. `chmod -R a+rX /app` before the build fixed it.

## Checks

All commands ran with `DOCKER_CONFIG=/tmp/dsh-docker` from `app/`.

```text
$ docker compose up -d --build          -> idea-board:local built, app-web-1 started
$ docker compose ps --format '{{.Health}}'   -> healthy
$ curl -s localhost:8000/api/health     -> {"status":"ok","version":"0.1.0"}
$ curl -s -X POST localhost:8000/api/ideas -H 'content-type: application/json' \
    -d '{"title":"Survives restart","tags":["docker"]}'   -> 201, idea 01M63DKY…
$ docker compose down && docker compose up -d
$ docker compose ps --format '{{.Health}}'   -> healthy
$ curl -s 'localhost:8000/api/ideas' | jq -c '[.ideas[].title]'   -> ["Survives restart"]
$ curl -s 'localhost:8000/api/ideas?tag=docker' | jq -c '[.ideas[].title]' -> ["Survives restart"]
$ docker compose exec web id -u         -> 1993 (user deno)
$ docker run --rm idea-board:local ls -A /app   -> no data/, no build recipe
$ docker compose down -v                -> volume app_ideas removed
$ docker image inspect idea-board:local --format '{{.Size}}' -> 540 MB
```

The lock-file invariant, tested on a copy in `/tmp` with `@std/bytes` added to `deno.json`:

```text
$ docker build -t idea-board:locktest /tmp/applock
#8 1.757 error: The lockfile is out of date. Run `deno install --frozen=false`, or rerun with
      `--frozen=false` to update it.
#8 ERROR: process "/bin/sh -c deno install --frozen" did not complete successfully: exit code: 1
```

Factory checks on the copy:

```text
$ tools/knowledge/knowledge-check  -> RESULT: PASS (0 problems)
$ tools/knowledge/doc-links        -> RESULT: PASS (99 links checked)
$ tools/repo-health/repo-health    -> AGENTS.md 120 lines, work/current.md 12, 0 big files, 0 long comments
```

## Result

```text
Result:          done
Tests:           container acceptance PASS (healthy, create, restart persistence, uid 1993, stale lock refused, down -v)
Factory checks:  knowledge-check PASS, doc-links PASS (99 links), repo-health PASS
Guard refusals:  1 (docker CLI wrote outside the workspace) -> FB-008
Human help:      0
Feedback:        FB-008 new
Elapsed:         ~45 minutes
```

## Commit

The sandbox cannot write `.git`; the owner runs:

```bash
cd /Users/wallseven/Desktop/aeion-factory
git add demo/dsh/README.md demo/dsh/journal/*.md demo/dsh/feedback_for_aeion/*.md
git commit -m "demo(dsh): round 06 docker and compose"
```
