# Round 04 — Votes and sorting

Plan (skill `plan`): extend the API spec with the vote section (route, index keys, optimistic
transaction, busy refusal), then extend `app/store/ideas.ts` (atomic create with the secondary
index, `vote`, `list(sort)`) and `app/api/ideas.ts` (`POST /api/ideas/:id/vote`, `?sort=`), plus the
page's `VoteButton` island and the `/ideas/:id/vote` form route. Tests first (`store/votes_test.ts`,
`api/votes_test.ts`), seen red on the missing exports.

What happened:
- Spec extended first: the vote outputs table, the `["ideas_by_votes", votes, id]` index, and
  invariants 7–9 (exactly N votes under concurrency, one index key per idea, `busy` after retries).
- The interesting find of this round, worth recording: **bounded retries cannot satisfy the
  concurrency invariant by themselves.** The first implementation retried the optimistic
  transaction up to 10 times; a traced probe (`Promise.allSettled` of 25 votes) showed each retry
  round has exactly one winner, so only 10 of 25 concurrent votes land and 15 refuse with `busy`.
  The fix keeps the optimistic transaction (it still guards against any other writer) and chains
  votes per id (`voteTails` map), so same-process contention disappears; the trace then shows 25
  of 25, votes = 25, one index key. The chain drops its tail when idle.
- The in-memory KV does enforce versionstamp checks (a separate probe: a stale check commits to
  `ok: false`), so the atomic transaction is real, not theater.
- Type nits fixed on the way: `KvEntry` has `versionstamp`, not `version`; `KvKey` is readonly;
  the page vote route's import depth; `sort` widened to `SortOrder` after validation.
- Green: 42 tests (`deno test -A`), fmt/lint/check pass (evidence/04-quality.txt), build
  (evidence/04-build.txt). HTTP smoke from the brief: 3 concurrent votes → `votes: 3`,
  `sort=votes` puts the voted idea first, and the page form route votes and redirects (303, votes 4;
  evidence/04-smoke.txt). Factory checks: PASS (evidence/04-factory.txt).

Result:          done
Tests:           42 passed, 0 failed (deno test -A); build PASS; HTTP smoke PASS
Factory checks:  knowledge-check, doc-links, repo-health: PASS
Guard refusals:  1 (a debug-script heredoc `cat >` after cd — bounded intake again; wrote the
                 scratch file with the write tool instead)
Human help:      0 (all rounds pre-authorized)
Feedback:        none new
Elapsed:         25 minutes
