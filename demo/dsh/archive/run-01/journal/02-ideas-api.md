# Round 02 — Ideas API on Deno KV

**Agent:** dsh (DeepSeek Harness) 0.2.1-rc.3, one session. **Date:** 2026-10-29.
**Brief:** [`plan/02-ideas-api.md`](../../../../plan/02-ideas-api.md).

## Plan

Files: `app/domain/idea.ts` (+ test), `app/store/ideas.ts`, `app/store/database.ts` (+ test),
`app/api/app.ts`, `app/api/ideas_test.ts`, `app/routes/api/[...path].ts`, `app/main.ts`,
`app/utils.ts`, `app/deno.json`; the spec `knowledge/components/idea-board-api.md`; the copy's
`CHANGELOG.md` and `work/current.md`. Out of scope: votes, filters, search, pagination, edit/delete.

## Spec before the code

The API spec now carries the idea shape, the input table with every reason code, the full output
table (201/400/413/415/404), and eight invariants each naming its test. It lists the domain, store
and database sources; every new code file has the `Spec:` header.

## What was built

- `app/domain/idea.ts`: pure `validateIdea`, first failing field in the table order, trimming,
  defaults, unknown-field refusal by name.
- `app/store/ideas.ts`: `createIdeaStore(kv)` with `create`, `get`, `list`; monotonic ULIDs so reverse
  key order is newest first; `votes: 0`, `createdAt` ISO.
- `app/store/database.ts`: one store per process, `KV_PATH` default `./data/ideas.sqlite3`, the data
  folder created on open and git-ignored.
- `app/api/app.ts`: `POST /api/ideas`, `GET /api/ideas?limit=`, `GET /api/ideas/:id`, the 8 KiB
  limit, JSON media-type check, and the stable error shapes.
- The catch-all passes the store from `main.ts` state; the home page is still the placeholder.

## Checks

```text
$ deno fmt --check   -> Checked 17 files, exit 0
$ deno lint          -> Checked 17 files, exit 0
$ deno check         -> exit 0
$ deno test -A       -> ok | 29 passed | 0 failed
   api/app_test 6, api/ideas_test 9, domain/idea_test 11, store/ideas_test 3
$ deno task build    -> exit 0
$ PORT=8000 deno task start
$ curl -s -X POST localhost:8000/api/ideas -H 'content-type: application/json' \
    -d '{"title":"Dark mode","tags":["ui"]}'
{"idea":{"id":"01M63C4H91Q6NDYVJ7DE99B73N","title":"Dark mode","description":"","tags":["ui"],"votes":0,"createdAt":"2026-10-29T08:24:01.057Z"}}
$ curl -s -X POST ... -d '{"title":"x"}'
400 {"error":"invalid","field":"title","reason":"too_short"}
$ curl -s 'localhost:8000/api/ideas?limit=5'
{"ideas":[...],"count":1}
$ curl -s localhost:8000/api/ideas/01ARZ3NDEKTSV4RRFFQ69G5FAV
404 {"error":"not_found"}
$ # stop, start again, GET /api/ideas -> the same idea (KV file persisted)
```

The tests were written in the same slice as the code, not before it. To prove the key test can fail I
changed `TITLE_MIN` from 3 to 2 once: `a title shorter than three characters is too_short ... FAILED`,
19 passed / 1 failed; restored, 20 passed. The mutation was reverted immediately.

## Where I got stuck

- `deno check` failed on `node:path` because `nodeModulesDir: "manual"` wanted `@types/node`; using
  `@std/path` for `dirname` removed the Node types dependency. Not a factory defect, but worth
  knowing for a Deno-native app.
- The first `deno check` after adding `@std/ulid` still needed `deno install` (same FB-004 pattern).
- `data/` is now ignored; the local KV file was removed after the restart check so later rounds start
  from a clean board.

## Result

```text
Result:          done
Tests:           29 passed, 0 failed (deno test -A); fmt, lint, check, build PASS; acceptance curls PASS
Factory checks:  knowledge-check PASS, doc-links PASS (81 links), repo-health PASS
Guard refusals:  0
Human help:      0
Feedback:        none new (FB-004 seen again)
Elapsed:         ~40 minutes
```

## Commit

The sandbox cannot write `.git`; the owner runs:

```bash
cd /Users/wallseven/Desktop/aeion-factory
git add demo/dsh/README.md demo/dsh/journal/*.md demo/dsh/feedback_for_aeion/*.md
git commit -m "demo(dsh): round 02 ideas api"
```
