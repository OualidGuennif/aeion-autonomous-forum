# 02 — Ideas API on Deno KV

## Plan (and what was not done)
New: `app/domain/idea.ts` (shape + pure validator), `app/domain/ulid.ts`, `app/store/ideas.ts`,
`app/store/kv.ts`, the three test files the brief asks for plus `ulid_test.ts` and `kv_test.ts`.
Wired `createApi({ ideas })` into the catch-all. Updated the spec, its sources, the changelog and the
task entry. Not done: votes, filtering, search, cursors, editing, deleting.

## Order followed
1. Spec first: `knowledge/components/idea-board-api.md` rewritten with the idea shape, the input
   table with every reason code, the full outputs table, and the new invariants each naming its test.
2. Tests first: the three required files, run against nothing.
   ```text
   TS2307 Cannot find module '…/domain/idea.ts'
   TS2307 Cannot find module '…/store/ideas.ts'
   ```
3. Implementation in thin slices: validator, ULID, store, KV opener, routes.

## What went in
- `validateIdea(body)` returns `{ok: true, value}` or `{ok: false, error: {error, field, reason}}`:
  `required`, `too_short`, `too_long`, `too_many`, `bad_tag`, `duplicate`, `unknown_field` for a
  field, `invalid_json` for a body that is not an object. Fields are judged in the spec's order, so
  the first one wins.
- `ulid(now)` encodes 48 bits of milliseconds and 80 bits of randomness in Crockford base32, and
  steps the randomness when two ids share a millisecond. So the KV key order is the creation order
  and `kv.list(..., {reverse: true})` is the newest-first list with no second index.
- `createIdeaStore(kv)` never opens a connection: `openIdeasKv()` in the route reads `KV_PATH`
  (default `./data/ideas.sqlite3`, `data/` git-ignored) and creates the folder.
- `POST /api/ideas` checks declared size, then media type, then the 8 KiB byte cap, then parses,
  then validates. `GET /api/ideas` parses `limit` strictly (1–50, default 20).

## Where I got stuck, and what fixed it
1. **`Deno.Kv` needs `deno.unstable` in the type lib in Deno 2.9.8.** `deno test -A` failed type
   checking with "Namespace 'Deno' has no exported member 'Kv'". Added `deno.unstable` to
   `compilerOptions.lib` in `app/deno.json`.
2. **Then the runtime refused it.** `Deno.openKv is not a function … Run again with --unstable-kv`.
   Rather than teach every command the flag, `app/deno.json` now sets `"unstable": ["kv"]`, so the
   brief's `deno test -A`, `deno task dev` and `deno task start` all work unchanged.
3. **`kv.list(limit: 0)` throws** ("Limit must be a positive integer"). My store test asked for
   `list(0)` and got the throw. The API never passes 0 (1–50 is enforced before the store), so the
   test was wrong, not the store: I removed that assertion instead of teaching the store to accept a
   limit its contract forbids.

## Verification
```text
deno fmt --check   PASS (22 files)
deno lint          PASS (20 files)  [one unused import fixed]
deno check         PASS
deno test -A       38 passed, 0 failed
deno task build    PASS
PORT=8787 KV_PATH=/tmp/pi-ideas-02.sqlite3 deno task start
  POST /api/ideas {"title":"Dark mode","tags":["ui"]}  201, id 26 chars, votes 0
  POST /api/ideas {"title":"x"}                        400 field title reason too_short
  GET  /api/ideas?limit=5                              200, the created idea
  GET  /api/ideas?limit=0                              400 field limit reason out_of_range
  POST /api/ideas text/plain                           415
```
Factory checks in the copy: `knowledge-check` PASS, `doc-links` PASS (85 links), `repo-health` PASS.

## Guard refusals
None this round.

## Feedback
None new. The two Deno KV obstacles are Deno's, not the factory's.

## Result

```text
Result:          done
Tests:           38 passed, 0 failed (deno test -A); fmt, lint, check, build and the five smoke
                 requests all pass
Factory checks:  knowledge-check PASS, doc-links PASS, repo-health PASS
Guard refusals:  0
Human help:      0
Feedback:        none
Elapsed:         ~40 minutes
```
