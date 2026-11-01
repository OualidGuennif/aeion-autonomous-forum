# FB-007: a live log in the session workspace makes every checkpoint receipt stale

- **Severity:** medium (the checkpoint can never verify while the run is logging)
- **Found in:** round 04 → 05
- **Status:** applied c3f586f

## Evidence

The run writes its headless event stream to `demo/dsh/journal/run.jsonl` with `tee`, as the README's
command does. That file is in the session workspace, so the checkpoint's tree hash includes it. The
log grows on every event, so the receipt written at checkpoint time is stale by the next tool call:

```text
$ python3 tools/context/budget.py checkpoint --session dsh-5dfb0e25… --verify
error: the tree changed since the checkpoint (recorded 3e87dc7c…, now 6c92d2a9…)
```

The recorded tree moved from `3e87dc7c…` to `6c92d2a9…` with no source edit between the two reads;
only the log had been appended. `budget.py checkpoint` also rewrites the same receipt, so the next
verify sees a different tree again. The owner's workaround was to git-ignore `demo/dsh/journal/run.jsonl`
before the first checkpoint; that fixed this run but the cause is general.

## Proposed fix

1. **Exclude append-only run logs from the tree hash.** Ignore paths the harness itself opens for
   append (`*.jsonl` under the journal, or a configurable `checkpoint_ignore` list) so the receipt
   covers source and docs, not the event stream.
2. Or **hash only tracked files** when the workspace is inside a git repository, and say so in
   context-safety.md.
3. Or **document the requirement**: the run log must be git-ignored (or written outside the
   workspace) before the first checkpoint, in the README command and in the start brief.

Whichever is chosen, the verify step should not fail because the harness kept logging.
