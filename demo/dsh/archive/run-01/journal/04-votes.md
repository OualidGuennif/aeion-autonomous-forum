# Round 04 — Votes and sorting

**Agent:** dsh (DeepSeek Harness) 0.2.1-rc.3, one session. **Date:** 2026-10-29.
**Brief:** [`plan/04-votes.md`](../../../../plan/04-votes.md).

## Plan

Files: `app/store/ideas.ts` (+ `votes_test.ts`), `app/api/app.ts` (+ `votes_test.ts`),
`app/routes/index.tsx`, `app/islands/VoteButton.tsx`, `app/routes/ideas/[id]/vote.ts`,
`app/api/app_test.ts`; the two component specs; the copy's `CHANGELOG.md` and `work/current.md`.
Out of scope: one vote per person, un-voting, rate limiting.

## What was built

- `["ideas_by_votes", votes, id] → id`, written in the same atomic operation as the idea on create.
- `vote(id)`: read the idea, `atomic().check(entry)`, set the idea, delete the old index key, set the
  new one, commit; up to 10 attempts, then `VoteBusyError` → `503 {"error":"busy"}`.
- `list(limit, sort)`: `recent` is the reverse `["ideas"]` order; `votes` is the reverse vote index,
  so ties fall back to newest first.
- `POST /api/ideas/:id/vote`, `GET /api/ideas?sort=recent|votes`, `400` for any other sort.
- The page has sort links, a "Vote" form per idea with a hidden `sort`, and the `VoteButton` island
  updating the count in place. Without JavaScript the form posts to `/ideas/:id/vote` and gets a 303
  back to `/?sort=…`.

## The concurrency finding

The first 25-concurrent-votes test failed with `VoteBusyError` after 10 retries. That is the brief's
two rules colliding: each conflict lets one vote commit, so the last of 25 needs up to 24 retries.
I kept the 10-retry cap for other processes and serialized votes for one id inside the process (a
`Map<id, Promise>` chain) before the same versionstamp check. The test then passed 25/25, and the
transaction still refuses a stale read. Filed as FB-005 with the three possible fixes.

## Checks

```text
$ deno fmt --check   -> Checked 27 files, exit 0
$ deno lint          -> Checked 26 files, exit 0
$ deno check         -> exit 0
$ deno test -A       -> ok | 52 passed | 0 failed
   api/app_test 6, api/ideas_test 9, api/votes_test 4, domain/form_test 3,
   domain/idea_test 11, routes/index_test 10, store/ideas_test 3, store/votes_test 6
$ deno task build    -> exit 0
$ PORT=8000 deno task start
$ ID=$(curl -s -X POST ... -d '{"title":"Vote me"}' | jq -r .idea.id)
$ for i in 1 2 3; do curl -s -X POST localhost:8000/api/ideas/$ID/vote > /dev/null & done; wait
$ curl -s localhost:8000/api/ideas/$ID | jq .idea.votes              -> 3
$ curl -s 'localhost:8000/api/ideas?sort=votes' | jq -r '.ideas[0].title' -> Vote me
$ curl -s 'localhost:8000/api/ideas?sort=other'                      -> 400 field sort out_of_range
$ curl -s 'localhost:8000/?sort=votes' | grep -c 'action="/ideas/<id>/vote"' -> 1
$ curl -s -o /dev/null -w '%{http_code} %{redirect_url}' -X POST \
    localhost:8000/ideas/$ID/vote -d 'sort=votes'                    -> 303 http://localhost:8000/?sort=votes
$ curl -s localhost:8000/ | grep -o 'fresh-island__VoteButton[^"]*'  -> fresh-island__VoteButton-….js
```

## Where I got stuck

- The 25-vote test failure above is the round's one real design snag; the fix is in the code and
  FB-005 asks the brief to choose between the retry cap and the test size.
- `kv.getMany<T>` is typed for key tuples, not values; `Promise.all(ids.map((id) => kv.get(...)))`
  is the correct call for 50 ideas.

## Result

```text
Result:          done
Tests:           52 passed, 0 failed (deno test -A); fmt, lint, check, build PASS; vote/sort curls PASS
Factory checks:  knowledge-check PASS, doc-links PASS (90 links), repo-health PASS
Guard refusals:  0
Human help:      0
Feedback:        FB-005 new
Elapsed:         ~40 minutes
```

## Commit

The sandbox cannot write `.git`; the owner runs:

```bash
cd /Users/wallseven/Desktop/aeion-factory
git add demo/dsh/README.md demo/dsh/journal/*.md demo/dsh/feedback_for_aeion/*.md
git commit -m "demo(dsh): round 04 votes"
```
