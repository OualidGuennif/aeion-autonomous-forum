# 04 — Votes and sorting

## Goal
A contributor votes for an idea; the list can be sorted by votes. Concurrent votes are never lost.

## User stories
- As a contributor, I click "Vote" and the count goes up by one.
- As a visitor, I sort the list by most voted or by newest.

## Spec
**Inputs.** `POST /api/ideas/:id/vote` (no body). `GET /api/ideas?sort=votes|recent` (default `recent`). On the page: `GET /?sort=votes`.

**Outputs.**

| Request | Response |
| --- | --- |
| `POST /api/ideas/:id/vote` | `200`, `{"idea": Idea}` with `votes` + 1 |
| unknown id | `404`, `{"error":"not_found"}` |
| `GET /api/ideas?sort=votes` | most votes first; ties: newest first |
| `GET /api/ideas?sort=other` | `400`, field `sort`, reason `out_of_range` |

**Storage.** A secondary index `["ideas_by_votes", votes, id] → id`, written in the same atomic
operation as the idea. A vote is an optimistic transaction: read the idea, `atomic().check(entry)`,
set the idea, move its index key, commit; retry on conflict (at most 10 times, then `503` `{"error":"busy"}`).
Votes on one idea are serialized inside the process (a per-id promise chain) before that transaction:
ten retries cannot land 25 truly concurrent commits, and the chain removes the self-contention while the
atomic check and the retry cap still guard other processes. Write the invariant test and the cap from
these two numbers, and test the `503` path with a store that always conflicts.

**Invariants.**
1. N concurrent votes on one idea give exactly N more votes (test: 25 votes with `Promise.all`).
2. The index never holds two keys for the same idea (test: after the concurrent votes, the index for that id has one key).
3. Voting for an unknown id writes nothing.

**Page.** Each idea shows its votes and a "Vote" button: an HTML form posting to `/ideas/:id/vote`
(redirect back, keeping `sort`), enhanced by the `VoteButton` island, which updates the count in place.

## Acceptance checks
```bash
cd app && deno test -A
ID=$(curl -s -X POST localhost:8000/api/ideas -H 'content-type: application/json' -d '{"title":"Vote me"}' | jq -r .idea.id)
for i in 1 2 3; do curl -s -X POST localhost:8000/api/ideas/$ID/vote > /dev/null & done; wait
curl -s localhost:8000/api/ideas/$ID | jq .idea.votes      # 3
curl -s 'localhost:8000/api/ideas?sort=votes' | jq '.ideas[0].title'
```

## Tests required
- `app/store/votes_test.ts`: one vote, unknown id, 25 concurrent votes, the index after them, sort order with ties.
- `app/api/votes_test.ts`: the statuses above.

## Out of scope
One vote per person (no accounts), un-voting, rate limiting.

## Expected result
Votes work under concurrency. Spec `knowledge/components/idea-board-api.md` gains the vote section.
