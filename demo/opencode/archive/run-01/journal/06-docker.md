# Round 06 — Docker and Compose

Plan (skill `plan`): write the runtime spec first, then `app/Dockerfile` (denoland/deno:2.9.8,
non-root `deno`, `deno install --frozen` before the source copy, one `deno task build`),
`app/.dockerignore` and `app/compose.yaml` (port 8000, named `ideas-data` volume on `/data`, health
check with `deno eval`). The container commands are the tests; every output kept.

Image tag verified on Docker Hub: `denoland/deno:2.9.8` (2026-10-21), the same runtime the app is
tested on.

What happened:
- `docker compose config` valid; `docker compose up -d --build` built and started.
- Container checks (evidence/06-container-checks.txt):
  1. `docker compose ps --format '{{.Health}}'` → `healthy`;
  2. `curl /api/health` from the host → `{"status":"ok","version":"0.1.0"}`;
  3. `docker compose exec web id -u` → `1993` (not 0); image `Config.User` is `deno`;
  4. an idea created before `down` is still listed after `down && up` (named volume);
  5. lock enforcement: a scratch copy with a modified `deno.json` makes `deno install --frozen`
     exit 1, so the same line in the Dockerfile fails the build on drift.
- One deliberate choice: the health check runs `deno eval` fetching `/api/health`, so no `curl` is
  added to the image (invariant 2). Its 10 s start period covers the production build's boot.
- Green: 51 tests unchanged, fmt/lint/check pass, `deno task build` (evidence/06-build.txt),
  build log (evidence/06-docker-build.txt). Factory checks: PASS (evidence/06-factory.txt).

Result:          done
Tests:           51 passed, 0 failed (deno test -A); build PASS; 5 container checks PASS
Factory checks:  knowledge-check, doc-links, repo-health: PASS
Guard refusals:  0 this round
Human help:      0 (all rounds pre-authorized)
Feedback:        none new
Elapsed:         15 minutes
