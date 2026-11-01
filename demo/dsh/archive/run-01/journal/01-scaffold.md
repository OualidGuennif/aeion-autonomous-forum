# Round 01 — Scaffold: Fresh 2 + Vite + Hono under /api

**Agent:** dsh (DeepSeek Harness) 0.2.1-rc.3, one session. **Date:** 2026-10-29.
**Brief:** [`plan/01-scaffold.md`](../../../../plan/01-scaffold.md).

## Plan

Files: `app/deno.json`, `app/version.ts`, `app/api/app.ts`, `app/api/app_test.ts`,
`app/routes/api/[...path].ts`, `app/routes/index.tsx`, `app/routes/_app.tsx`, `app/main.ts`,
`app/server.ts`, `app/vite.config.ts`, `app/client.ts`, `app/utils.ts`; the spec
`knowledge/components/idea-board-api.md` and its index row; the copy's `AGENTS.md`, `CHANGELOG.md`,
`work/current.md` and `.gitignore`. Out of scope: storage, ideas, votes, filters, Docker, styling.

## Spec before the code

`knowledge/components/idea-board-api.md` has the eight sections, the four outputs with their
refusals, five invariants each naming its test, and the acceptance commands. `app/api/app.ts`,
`app/routes/api/[...path].ts`, `app/version.ts`, `app/main.ts`, `app/server.ts`, `app/utils.ts`,
`app/routes/index.tsx`, `app/routes/_app.tsx`, `app/client.ts` and `app/vite.config.ts` carry the
`Spec:` header. The components index links it.

## What was built

- Deno 2.9.8, Fresh 2.3.3 with `@fresh/plugin-vite` 1.1.2, Vite 7.3.7, Preact 10.29.8, Hono 4.13.14,
  `@std/assert` 1.0.19; `deno install` wrote `deno.lock` and `node_modules/`.
- `app/api/app.ts` is a plain `createApi()` returning `new Hono().basePath("/api")`; it imports no
  Fresh module. `/api/health` returns the version from `app/version.ts`; a wrong method on a known
  path is a JSON 405; anything else below `/api` is a JSON 404; `onError` keeps unhandled failures
  JSON.
- `app/routes/api/[...path].ts` is the only API route and only calls `api.fetch(ctx.req)`.
- The home page is a placeholder with `<h1>Idea Board</h1>` and `<title>Idea Board</title>`.
- The copy's `AGENTS.md` now says what the project is and lists the app commands; its `CHANGELOG.md`
  has the Unreleased line; `work/current.md` holds this round; `.gitignore` covers `_fresh/` and
  `data/` so the build output does not enter the health scorecard.

## Checks

```text
$ deno fmt --check   -> Checked 12 files, exit 0
$ deno lint          -> Checked 11 files, exit 0
$ deno check         -> exit 0
$ deno test -A       -> ok | 6 passed | 0 failed
$ deno task build    -> built client and server entries, exit 0
$ PORT=8000 deno task start
$ curl -s localhost:8000/api/health
{"status":"ok","version":"0.1.0"}
$ curl -s -o /dev/null -w '%{http_code}' localhost:8000/api/nope        -> 404
$ curl -s -X POST localhost:8000/api/health                              -> 405 {"error":"method_not_allowed"}
$ curl -s localhost:8000/ | grep -c '<h1>Idea Board</h1>'                -> 1
$ deno task dev (Vite, 5173): /api/health 200, / title "Idea Board", /api/nope 404
```

The six tests are the three required behaviours, the JSON content type over four calls, the
Fresh-free API module, and the one-catch-all route. They passed on the first run: this round has the
spec first, but not a failing-test-first record.

## Where I got stuck

- `deno check` failed on a fresh app until `deno install` (Fresh's npm dependency
  `@opentelemetry/api` was not in `node_modules`). Filed as FB-004; the acceptance block should start
  with `deno install`.
- Port 8123 already had a Python service bound to `127.0.0.1:8123`; Deno bound the wildcard
  `*:8123`, so `curl localhost:8123` reached the other app while mine was listening. I used 8137,
  then the free default 8000 for the acceptance checks. Not a guard refusal; a local port shadow.
- `repo-health` first failed on the generated `_fresh/` files (over 1,500 lines, long comment
  blocks). Ignoring `_fresh/` and `data/` in `.gitignore` is the right fix: they are regenerated.
- `AGENTS.md` reached 122 lines after the project paragraph and app commands; trimmed to 120, the
  factory's cap.

## Result

```text
Result:          done
Tests:           6 passed, 0 failed (deno test -A); fmt, lint, check, build PASS; start/dev curls PASS
Factory checks:  knowledge-check PASS, doc-links PASS (75 links), repo-health PASS
Guard refusals:  0
Human help:      0
Feedback:        FB-004 new
Elapsed:         ~45 minutes
```

## Commit

The sandbox cannot write `.git`; the owner runs:

```bash
cd /Users/wallseven/Desktop/aeion-factory
git add demo/dsh/README.md demo/dsh/journal/*.md demo/dsh/feedback_for_aeion/*.md
git commit -m "demo(dsh): round 01 scaffold"
```
