# 04 — Votes and sorting

## Plan (and what was not done)
Store: the vote index and an atomic, optimistic vote. API: `POST /api/ideas/:id/vote` and
`?sort=recent|votes`. Page: sort links, a vote form per idea, the `VoteButton` island, and the
no-JavaScript vote route. Tests: `store/votes_test.ts`, `api/votes_test.ts`, `routes/ideas/[id]/vote_test.ts`
and the sort/vote cases in `routes/index_test.ts`. Not done: one vote per person, un-voting, rate
limiting.

## Order followed
1. Specs first: the vote section in `knowledge/components/idea-board-api.md` (storage, outputs,
   invariants) and the vote form in `knowledge/components/idea-board-page.md`.
2. Tests first: store, API and route tests, failing on the missing `vote` and `VoteBusyError`.
3. Implementation: store index + vote → API routes → page sort → vote route → island.

## What went in
- `["ideas_by_votes", votes, id] → id` is written in the same atomic operation as the idea; a vote is
  read, `atomic().check(entry)`, move the index key, set the idea, retry up to ten times, then
  `503 {"error":"busy"}`.
- Listing by votes reads the index in reverse (most votes first, ties by id = newest first) and
  fetches the ideas by id.
- `GET /api/ideas?sort=other` is `400` field `sort` reason `out_of_range`; the page is tolerant and
  treats anything but `votes` as `recent`.
- `POST /ideas/:id/vote` is the plain form path: same `store.vote`, then `303 /?sort=<posted sort>`;
  the island posts to the API and moves the count in place, `aria-live` on the number.

## Where I got stuck, and what fixed it
1. **25 concurrent votes exhausted ten retries.** The optimistic loop alone lost too many races:
   `VoteBusyError` fired under `Promise.all`. The brief's cap of ten attempts is right for real
   contention but a test that fires 25 at once needs the votes not to race in one process, so
   `createIdeaStore` now queues votes on the same id behind each other; the `check(entry)` transaction
   stays, and still guards a second instance (documented in the spec's Storage section).
2. **A page test asked for vote-form markup on an empty board.** The sort test asserted
   `name="sort"`/`value="votes"` with no ideas, so no vote form existed. Fixed by creating one idea in
   that test; the markup assertions stay.

## Verification
```text
deno fmt --check   PASS (33 files)
deno lint          PASS (31 files)
deno check         PASS
deno test -A       66 passed, 0 failed
deno task build    PASS
PORT=8787 KV_PATH=/tmp/pi-ideas-04.sqlite3 deno task start
  3 concurrent API votes           -> votes 3
  page vote (form, sort=votes)     -> 303 /?sort=votes, then votes 4
  GET /api/ideas?sort=votes        -> the voted idea first
  GET /api/ideas?sort=other        -> 400 field sort reason out_of_range
```
Factory checks in the copy: `knowledge-check` PASS, `doc-links` PASS (100 links), `repo-health` PASS.

## Guard refusals
None this round.

## Feedback
None new. The retry cap under 25-way contention is a property of the brief's own algorithm, not a
factory friction; the spec records how it is handled.

## Result

```text
Result:          done
Tests:           66 passed, 0 failed (deno test -A); fmt, lint, check, build and the five vote
                 smoke checks all pass
Factory checks:  knowledge-check PASS, doc-links PASS, repo-health PASS
Guard refusals:  0
Human help:      0
Feedback:        none
Elapsed:         ~45 minutes
```
