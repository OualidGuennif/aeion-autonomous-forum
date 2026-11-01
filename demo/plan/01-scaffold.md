# 01 — Scaffold: Fresh 2 + Vite + Hono under /api

## Goal
A Fresh 2 application in `app/`, built with Vite, whose `/api/*` requests are served by a Hono app,
with a health endpoint the later rounds (and Docker) rely on.

## User stories
- As an operator, I start the app with one command and `GET /api/health` tells me it is up.
- As a developer, I test the API without starting a server.

## Spec
**Inputs.** `PORT` (default `8000`) for `deno task start`. No other input.

**Outputs.**

| Request | Response |
| --- | --- |
| `GET /` | `200`, HTML page titled "Idea Board" |
| `GET /api/health` | `200`, `{"status": "ok", "version": "<app version>"}` |
| `GET /api/<unknown>` | `404`, `{"error": "not_found"}` (JSON, never the Fresh HTML 404 page) |
| `POST /api/health` | `405`, `{"error": "method_not_allowed"}` |

**Invariants.**
1. Every `/api/*` response is JSON with `content-type: application/json`.
2. The Hono app is a plain module (`app/api/app.ts`) with no Fresh import, so it is testable alone.
3. Fresh hands `/api/*` to Hono through one catch-all route (`app/routes/api/[...path].ts`); no API logic lives in it.

## Contract
- `app/api/app.ts` exports `createApi(deps)` returning a Hono app with `basePath("/api")`.
- `app/version.ts` exports the app version (`0.1.0`), the one place it is written.

## Acceptance checks
```bash
cd app
deno install                      # first: npm packages and deno.lock, before any check
deno fmt --check && deno lint && deno check
deno test -A                      # api/health, unknown route, wrong method
deno task build
deno task start &                 # then:
curl -s localhost:8000/api/health # {"status":"ok","version":"0.1.0"}
curl -s -o /dev/null -w '%{http_code}' localhost:8000/api/nope   # 404
```

`deno install` comes first because the Fresh template sets `nodeModulesDir: "manual"`: `deno check`
fails until the packages are installed, and the lock file is written there. Keep `manual` and say so
in the spec.

## Tests required
- `app/api/app_test.ts`: health body and content type; unknown route is a JSON 404; wrong method is a JSON 405.

## Out of scope
Storage, pages beyond a placeholder home page, Docker.

## Expected result
`deno task dev` serves the home page and `/api/health`. The factory spec
`knowledge/components/idea-board-api.md` exists and lists `app/api/app.ts` in its sources.
