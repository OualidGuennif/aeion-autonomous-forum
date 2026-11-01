# FB-005: round 04 asks for 25 concurrent votes and a 10-retry cap that cannot keep them

- **Severity:** medium (the brief's own invariant test fails with a correct implementation of its
  transaction)
- **Found in:** round 04
- **Status:** applied in the run 2 brief (demo/plan/, demo/start/)

## Evidence

The brief requires "N concurrent votes on one idea give exactly N more votes (test: 25 votes with
`Promise.all`)" and, in the same section, "retry on conflict (at most 10 times, then `503`
`{"error":"busy"}`)". With the transaction exactly as written (read the idea, `atomic().check(entry)`,
set the idea, move the index key, commit, retry on conflict), 25 concurrent votes exhaust 10 retries:

```text
$ deno test -A store/votes_test.ts
twenty-five concurrent votes are all kept ... FAILED
error: VoteBusyError: the idea changed too often; try again
    at Object.vote (app/store/ideas.ts:78:13)
    at async Promise.all (index 10)
```

The cause is structural: each conflict lets one vote commit, so the last of 25 needs up to 24
retries, not 10. The two sentences cannot both hold under in-process contention.

## Proposed fix

Choose one and state it where the transaction is specified:

1. **Serialize per id in the process, keep the 10 retries for other processes** (what this run did):
   a `Map<id, Promise>` chain runs one vote at a time before the same `check`/commit, so 25 calls in
   one process commit in order and a second process still meets the 10-retry cap. Add that sentence to
   round 04's Storage paragraph and to the vote spec.
2. Or raise the retry budget with jitter (for example 50 attempts with backoff) and change "at most
   10" to the new bound; then the `503` case needs a test that actually reaches it.
3. Or lower the invariant's test to 10 concurrent votes and say the bound is the cap.

Whichever is chosen, the invariant test and the retry cap should be written from the same number.
