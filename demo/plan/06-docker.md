# 06 — Docker and Compose

## Goal
Run the production build in a container with one command, with a health check and data that
survives a restart.

## User stories
- As an operator, `docker compose up` gives me a running, healthy board on port 8000.
- As an operator, my ideas are still there after `docker compose down && docker compose up`.

## Spec
**Image.** `app/Dockerfile`, on the official `denoland/deno` image at an exact version (the latest
stable, recorded). It installs dependencies with the lock file, builds once, and runs as the
non-root `deno` user. No secret, no `.env` file in the image (`app/.dockerignore` excludes data,
`node_modules` and build output).

**Compose.** `app/compose.yaml`: one service `web`, port `8000:8000`, `KV_PATH=/data/ideas.sqlite3`,
a named volume on `/data`, a health check calling `/api/health` from inside the container.

**Invariants.**
1. The container runs as a non-root user (`docker compose exec web id -u` is not `0`).
2. The health check uses the app's own runtime (no `curl` added to the image).
3. Data written before `down` is readable after `up` (the named volume).
4. The image build uses the lock file (a dependency change without a lock update fails the build).

## Acceptance checks
```bash
cd app
docker compose up -d --build
docker compose ps --format '{{.Health}}'            # healthy (within 30 s)
curl -s localhost:8000/api/health
curl -s -X POST localhost:8000/api/ideas -H 'content-type: application/json' -d '{"title":"Survives restart"}'
docker compose down && docker compose up -d
curl -s 'localhost:8000/api/ideas' | jq '.ideas[].title'   # contains "Survives restart"
docker compose exec web id -u                        # not 0
docker compose down -v
```

In a sandbox that cannot write `~/.docker` (dsh run 1), point Docker at a config folder it can
write, for the commands above only: `DOCKER_CONFIG="$PWD/.docker-config" docker compose …`, with that
folder git-ignored. Do not change the user's Docker settings.

## Tests required
The commands above, run and kept in the round's journal (they are the container's tests).

## Out of scope
A registry push, TLS, a reverse proxy, multi-replica KV.

## Expected result
`docker compose up` gives a healthy board with persistent data. Spec `knowledge/components/idea-board-runtime.md`.
