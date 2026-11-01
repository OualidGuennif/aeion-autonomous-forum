# Round 05 — Tags and search

**Agent:** dsh (DeepSeek Harness) 0.2.1-rc.3, one session. **Date:** 2026-10-29.
**Brief:** [`plan/05-tags-search.md`](../../../../plan/05-tags-search.md).

## Plan

Files: `app/domain/filters.ts` (new), `app/store/ideas.ts` (+ `search_test.ts`),
`app/api/app.ts` (+ `search_test.ts`), `app/routes/index.tsx` (+ `index_test.ts`),
`app/domain/messages.ts`; the two component specs; the copy's `CHANGELOG.md` and `work/current.md`.
Out of scope: filters beyond one tag and one query, a full-text index, pagination cursors.

## What was built

- `["ideas_by_tag", tag, id] → id` for every tag, written in the same atomic operation as the idea.
- `list(limit, sort, filter)`: a `tag` filter reads only that tag's index, then sorts and slices; a
  `q` filter scans at most the newest 500 ideas and keeps a case-insensitive literal substring of
  title or description; `tag` and `q` combine with AND.
- `app/domain/filters.ts`: `parseFilters` is the one rule set for the API and the page — `tag`
  `^[a-z0-9-]{1,20}$` (`bad_tag`), `q` trimmed and at most 50 characters (`too_long`), empty means no
  filter. `app/api/app.ts` returns the refusal as a `400`; the page shows the message.
- The page has a search box that keeps the query, tag links that keep the sort and query, hidden
  `tag`/`sort` fields, an "active filters" line and "Clear filters". A refused filter lists nothing
  rather than showing the whole board, and "No ideas yet." is not shown for a refusal.
- `describeError` gained `tag:bad_tag` and `q:too_long` wording.

## The search bound

"Search" here is a stated window, not an engine: the newest 500 ideas. The test creates 501 ideas
with the needle in the oldest and proves the match is not found, then adds a newer needle and proves
it is. `.*` is matched literally, not as a pattern; both are asserted in the store and API tests.

## Checks

```text
$ deno fmt --check   -> Checked 30 files, exit 0
$ deno lint          -> Checked 29 files, exit 0
$ deno check         -> exit 0
$ deno test -A       -> ok | 79 passed | 0 failed
   api/app_test 6, api/ideas_test 9, api/search_test 10, api/votes_test 4,
   domain/form_test 3, domain/idea_test 11, routes/index_test 17,
   store/ideas_test 3, store/search_test 10, store/votes_test 6
$ deno task build    -> exit 0
$ PORT=8000 KV_PATH=/tmp/dsh05.sqlite3 deno task start
$ curl -s localhost:8000/api/health                                  -> {"status":"ok","version":"0.1.0"}
$ curl -s 'localhost:8000/api/ideas?tag=ui' | jq -c '[.ideas[].title]'    -> ["Dark mode"]
$ curl -s 'localhost:8000/api/ideas?q=DARK' | jq -c '[.ideas[].title]'    -> ["Dark mode"]
$ curl -s 'localhost:8000/api/ideas?tag=dx&sort=votes' | jq -c '[.ideas[].title]' -> ["Faster tests"]
$ curl -s -o /dev/null -w '%{http_code}' 'localhost:8000/api/ideas?tag=UI'   -> 400
$ curl -s 'localhost:8000/api/ideas?tag=UI'                          -> field tag reason bad_tag
$ curl -s 'localhost:8000/api/ideas?q=<51 chars>'                    -> 400 field q reason too_long
$ curl -s 'localhost:8000/?tag=ui' | grep -c 'active-filters'        -> 1
$ curl -s 'localhost:8000/?q=DARK' | grep -o 'value="DARK"'          -> value="DARK"
$ curl -s 'localhost:8000/?q=DARK' | grep -c 'Clear filters'         -> 1
$ curl -s 'localhost:8000/?tag=UI' | grep -c 'lowercase letters, digits and dashes' -> 1
```

Factory checks on the copy:

```text
$ tools/knowledge/knowledge-check  -> RESULT: PASS (0 problems)
$ tools/knowledge/doc-links        -> RESULT: PASS (94 links checked)
$ tools/repo-health/repo-health    -> AGENTS.md 120 lines, work/current.md 12, 0 big files, 0 long comments
```

## Where I got stuck

- `kv.list` prefix typing: `["ideas_by_tag", filter.tag]` with `filter.tag: string | undefined` is
  not a `KvKeyPart`; `listByTag` now takes `tag: string` and an optional `q`.
- The page first listed the whole board when a filter was refused. That answers a question the page
  just refused, so it now lists nothing, keeps the typed query in the box (capped at 50 characters)
  and skips "No ideas yet.".
- The closure gate interrupted this round twice: the handover belongs to the parent `work/`, which
  the sandbox refuses (FB-006), and the live `run.jsonl` changed the checkpoint's tree hash on every
  event (FB-007). The owner git-ignored the log and verified the checkpoint; no source work was lost.

## Result

```text
Result:          done
Tests:           79 passed, 0 failed (deno test -A); fmt, lint, check, build PASS; filter curls PASS
Factory checks:  knowledge-check PASS, doc-links PASS (94 links), repo-health PASS
Guard refusals:  2 (handover outside the sandbox; stale checkpoint receipt)
Human help:      1 (owner git-ignored run.jsonl and verified the checkpoint)
Feedback:        FB-006, FB-007 new
Elapsed:         ~55 minutes across a compaction
```

## Commit

The sandbox cannot write `.git`; the owner runs:

```bash
cd /Users/wallseven/Desktop/aeion-factory
git add demo/dsh/README.md demo/dsh/journal/*.md demo/dsh/feedback_for_aeion/*.md
git commit -m "demo(dsh): round 05 tags and search"
```
