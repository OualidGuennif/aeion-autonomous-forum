# 01 — Scaffold: Fresh 2 + Vite + Hono under /api

## Plan (and what was not done)
Files: `app/` gets the Fresh shell (`main.ts`, `utils.ts`, `client.ts`, `routes/_app.tsx`,
`routes/index.tsx`), the API module (`api/app.ts`, `version.ts`, `api/app_test.ts`), the one
catch-all `routes/api/[...path].ts`, `server.ts`, `vite.config.ts`, `deno.json`, a stylesheet.
Docs: `knowledge/components/idea-board-api.md`, its index row, the changelog line, the task entry.
Not done, per the brief: no storage, no real page, no Docker, no input parsing.

Versions actually resolved (`app/deno.lock`): Deno 2.9.8, Fresh 2.3.3, `@fresh/plugin-vite` 1.1.2,
Vite 7.3.7, Preact 10.29.8, Hono 4.13.14.

## Order followed
1. Spec first: `knowledge/components/idea-board-api.md` with the eight sections, linked from
   `knowledge/components/index.md`.
2. Acceptance tests first, `app/api/app_test.ts`, run against nothing:
   ```text
   TS2307 Cannot find module '…/app/api/app.ts'
   TS2307 Cannot find module '…/app/version.ts'
   ```
3. Code in thin slices, then the test suite, formatter, linter, type checker, build, live server.

## What went in
- `app/api/app.ts`: `createApi(deps)` returns `new Hono().basePath("/api")` with `GET /health`,
  an `all /health` that answers 405, and `notFound` answering 404. No Fresh import.
- `app/routes/api/[...path].ts`: one catch-all handler, `define.handlers((ctx) => api.fetch(ctx.req))`.
- `app/routes/_app.tsx` titles the page "Idea Board"; `app/routes/index.tsx` is the placeholder home.
- `app/server.ts`: `PORT` (default 8000) and `Deno.serve` of the built entry.

## Where I got stuck, and what fixed it
1. **`basePath` is not a mutator.** Written as `const app = new Hono(); app.basePath("/api")`, both
   the health test and the 405 test failed with 404: Hono's `basePath()` returns a new instance, so
   the routes registered on the old one stayed at `/health`. Fixed by chaining
   `new Hono().basePath("/api")` and registering on that. The tests caught it before the build did.
2. **`deno task start` ignored `PORT`.** The Fresh 2.3.3 template ships
   `deno serve -A _fresh/server.js`, and `deno serve` reads only `--port`, not `PORT`; the first
   smoke run listened on 8000 while I curled 8123 (and got another local app's `/api/health`, a
   useful reminder to check the server's own log, not just the curl). `deno task`'s shell does not
   expand `${PORT:-8000}` either — it is `deno_task_shell`, not `sh`. Fixed with `app/server.ts`:
   reads `PORT` with its 8000 default and serves the built entry, so the deployment keeps one
   contract on every start path.
3. **Port clash.** 8123 belongs to another local service; the smoke run uses 8787.

## Verification
```text
deno fmt --check   PASS (13 files)
deno lint          PASS (11 files)
deno check         PASS (10 entry points)
deno test -A       6 passed, 0 failed
deno task build    PASS (client 45.80 kB, server 174.18 kB)
PORT=8787 deno task start
  GET  /api/health   200 {"status":"ok","version":"0.1.0"}
  GET  /api/nope     404 {"error":"not_found"}
  POST /api/health   405 {"error":"method_not_allowed"}
  GET  /             200 <title>Idea Board</title>
```
Factory checks in the copy: `knowledge-check` PASS (0 problems), `doc-links` PASS (76 links),
`repo-health` PASS (AGENTS.md 119, work/current.md 18, 8 top-level folders, 0 big files, 0 long
comment blocks). The first `knowledge-check` run failed on the new task entry (see FB-001); adding
the two fields it named turned it green.

## Guard refusals
Twice, both the same: a command that began `cd …` and later ran `cat` was refused by `guard-bash`
("cannot size cat after a cd"). Right calls — both chains ended in an unbounded read — and both were
fixed by giving `head -n` a literal path in its own pipeline.

## Feedback
- **FB-001** (new, low): the five fields of a `work/current.md` entry are enforced by
  `knowledge-check` and stated in no doc an agent reads.

## Result

```text
Result:          done
Tests:           6 passed, 0 failed (deno test -A); fmt, lint, check, build and the four smoke
                 requests all pass
Factory checks:  knowledge-check PASS, doc-links PASS, repo-health PASS
Guard refusals:  2 (both bounded-intake on a `cat` after a `cd`; both right, fixed by splitting)
Human help:      0
Feedback:        FB-001 new
Elapsed:         ~45 minutes
```
