# FB-003 — concurrent demo runs share one working tree, and the checkpoint fingerprint covers all of it

- Severity: medium (can invalidate a verified compaction checkpoint; commits can sweep foreign work)
- Date: 2026-10-28
- Harness: OpenCode, demo run (rounds 00–07)

## What happened

1. Round 00 started with **30 changed paths already in the parent working tree** from the Codex run
   (uncommitted journals and evidence under `demo/codex/`), plus modified root files. The run
   protocol says "commit each round with only `opencode/` files", so every commit staged
   `demo/opencode/` explicitly — but a `git add -A` would have swept another harness's work.
2. Mid-run, a commit appeared that this session did not create:
   `219ad75 Task state: OpenCode run in progress, Codex rerun tomorrow`, touching only
   `work/current.md` (the file this session had just edited for the checkpoint). In other words, a
   second writer — the owner's monitor, or the Codex harness — committed into the same branch while
   this run was live.
3. `tools/context/budget.py checkpoint --verify` fingerprints **every tracked and untracked file**
   in the repository (`fingerprint(root)` minus `work/handovers/` and `.aeion/`). Any foreign write
   between the checkpoint and the compaction changes the tree hash and the hook refuses the
   compaction as unverified — the guard behaves correctly, but the cause is another harness, not
   this session.

## Evidence

- Startup hook output in this session listed 30 changed paths at round 00.
- `git log --oneline` on `develop`: `219ad75` sits between the Codex reset commit and the OpenCode
  round 00 commit; `git show --stat 219ad75` shows a single `work/current.md` line.
- `tools/context/budget.py`: `fingerprint()` walks `project_files(root, untracked=True)` for the
  whole root; `ready()` compares it to the receipt written at checkpoint time.

## Proposed fix

1. Demo protocol: one harness at a time, or a separate worktree/copy per run
   (`tools/opencode.worktree_create`, or `task/<slug>` worktrees the factory already supports). The
   plan's own rule "only `demo/<harness>/workspace/` is git-ignored" assumes sequential runs.
2. If parallel runs stay allowed, scope the checkpoint fingerprint to the session's own paths (e.g.
   the worktree root, or the changed set the session is allowed to touch) so a neighbour's writes
   cannot invalidate a verified handover.
3. Name the concurrent writer in the checkpoint refusal ("the tree changed since verification:
   <paths>") so the operator can see it is not a secret or a broken handover.

## Status

Applied c3f586f (a stale receipt names the changed paths; one harness at a time in run 2).
