# Round 06: Docker and Compose

**Brief:** [`../../plan/06-docker.md`](../../plan/06-docker.md).
**Result:** done.
- `docker compose up` gives a healthy board on port 8000 in 6 s.
- The 5 container checks in `app/container_check.sh` pass in 16.5 s, including a rebuild.
- The 72 app tests still pass; the copy's factory checks are green after one fix (FB-004 again).
- The owner gave their visual yes for round 05 before this round started.

## 1. Plan
Slices:
1. The runtime spec: `knowledge/components/idea-board-runtime.md`, five invariants, each named by a
   check in one script.
2. `app/Dockerfile`, `app/.dockerignore` and `app/compose.yaml`.
3. `app/container_check.sh`: the brief's acceptance commands as a script that fails on the first
   broken check and always removes the stack and its volume.

## 2. Versions
- Deno: the latest release on GitHub is `v2.9.8` (2026-10-21), the version already installed. The
  image is `denoland/deno:2.9.8`, pulled for this round (the plan's approval covered that download).
- Docker 29.9.2, daemon `linux/aarch64`.

## 3. Spec first
Invariants and their checks:
1. Non-root: the process runs as `deno` (uid 1993). Check "non-root".
2. The health check uses `deno eval` with `fetch`, so the image needs no `curl`. Check "healthy".
3. Ideas survive `down` and `up` through the named volume `ideas-data`. Check "survives restart".
4. `deno install --frozen`: a specifier that the lock does not hold stops the build. Check "frozen
   lock".
5. No `.env*` file, no `data/` and no `curl` inside the image. Check "clean context".

## 4. Build: decisions and where I got stuck
| Point | Decision or cause | Result |
| --- | --- | --- |
| Run-time permissions | the brief only asks for non-root; `deno task start` uses `-A` | the image runs `--cached-only --allow-net --allow-env --allow-read=/app,/data --allow-write=/data`, so the server can neither download code nor write outside the volume; it went healthy on the first try |
| Volume ownership | a named volume copies the ownership of the image's directory on first use | `/data` is created and given to `deno` before `USER deno` |
| Layer order | `deno.json` and `deno.lock` are copied before the code | a code change reuses the install layer |
| Hardening | `cap_drop: [ALL]` and `no-new-privileges` in compose | no capability is needed to listen on 8000 |
| "survives restart" could pass for the wrong reason | a volume left by an earlier run would already hold the idea | the script starts with `docker compose down -v` |
| The frozen-lock check could pass for the wrong reason | any build failure would do | the check builds a scratch context whose `deno.json` asks for `@std/ulid@^1.0.1` and also requires the word "lockfile" in the log |
| `AGENTS.md` reached 121 lines | the Docker commands on the copy's build line | the two app lines became one and point to `app/README.md`; **FB-004** |

The frozen lock refusal, as the image build prints it:

```text
#9 2.249 error: The lockfile is out of date. Run `deno install --frozen=false`, or rerun with `--frozen=false` to update it.
#9 ERROR: process "/bin/sh -c deno install --frozen" did not complete successfully: exit code: 1
```

## 5. Batteries
```text
./container_check.sh
ok healthy
ok non-root
ok clean context
ok survives restart
ok frozen lock
16.512 total
```

The brief's acceptance commands, run by hand:
```text
docker compose up -d --build; wait for health      -> healthy after 6s
curl -s localhost:8000/api/health                  -> {"status":"ok","version":"0.1.0"}
POST /api/ideas {"title":"Survives restart"}       -> 201 {"idea":{…"title":"Survives restart"…}}
docker compose down && docker compose up -d
GET /api/ideas titles                              -> ['Survives restart']
docker compose exec web id -u / id -un             -> 1993 / deno
GET /                                              -> 200
cross-site form POST / (Origin: https://evil.example) -> 403
docker compose ps                                  -> idea-board-web-1 Up (healthy) 0.0.0.0:8000->8000/tcp
docker compose down -v                             -> volume idea-board_ideas-data removed
```

App and factory checks:
```text
deno task check          fmt + lint + check: clean (40 files)
deno test -A             ok | 72 passed | 0 failed
knowledge-check          PASS (0 problem(s))
doc-links                PASS (91 links checked)
repo-health              AGENTS.md 120 lines, work/current.md 13
```

## 6. Known limits
- The image is 420 MB: it keeps the development dependencies (Vite and its tree) that the build
  needed. A second stage copying only `_fresh/` and the runtime dependencies would shrink it; the
  brief does not ask for size, so it is noted, not done.
- The check script binds port 8000 on the host; it stops on a busy port rather than picking another.
- One replica only: Deno KV on a local SQLite file has one writer (out of scope in the brief).

## 7. Feedback from this round
- **FB-004** (low), seen again: the 120-line cap evicted part of the copy's build line, which is the
  line the template itself asks projects to extend.
- What worked: writing the checks as one script made the brief's "the commands are the tests"
  runnable again in one call, and each check was tightened against passing for the wrong reason
  before it was trusted.
