# Demo: Æion Factory on a real app, one agent harness at a time

An agent builds the same small app, **Idea Board**, inside a copy of Æion Factory that has no git. The
stack is Deno 2, Fresh 2 with Vite, Hono under `/api`, Deno KV, and Docker. The agent follows the
briefs in [`plan/`](plan/00-start.md), one round per file, and journals what really happened.

Each friction with the factory becomes a feedback file. The small ones are fixed in the base factory
on `develop`, each with a test, so the next harness starts from a better factory.

## Replay it with another harness

1. Create the copy from the repository root, replacing `<harness>` with the harness name:

   ```bash
   mkdir -p demo/<harness>/workspace && git archive HEAD -- . ':(exclude)demo' | tar -x -C demo/<harness>/workspace && tools/release/release --new-project --root demo/<harness>/workspace
   ```

2. Create `journal/`, `feedback_for_aeion/` and `render/` beside it. The layout is in
   [`plan/00-start.md`](plan/00-start.md).
3. Open the agent at the repository root ([why](plan/00-start.md)), one harness at a time. Give it the
   harness's `START.md` as its first message from [`start/`](start/README.md), which also says how to set up each harness's skills and hooks, then one round
   at a time.
4. Every round's journal ends with the same record block, so the runs can be compared.

## Results by harness

| Harness | Factory | Rounds done | Final tests | Feedback (fixed) | Human help | Run |
| --- | --- | --- | --- | --- | --- | --- |
| Claude Code, run 1 | 1.3.0 `fb2af90` | 8 of 8 | 72 + 5 container checks | 10 (9) | approvals, 3 checkpoints | [archive/run-01/](claude_code/archive/run-01/SUMMARY.md) |
| Codex, run 1 | 1.4.0 `578f4f3` | 8 of 8 | 50 + 14 diagram checks | 3 (3) | 1 authorization, commit approvals | [archive/run-01/](codex/archive/run-01/SUMMARY.md) |
| OpenCode, run 1 | 1.4.0 `578f4f3` | 8 of 8 | 51 + 5 container checks | 3 (3) | 2: authorization, a compaction request | [archive/run-01/](opencode/archive/run-01/SUMMARY.md) |
| Pi, run 1 | 1.5.0 `6e98daa` | 8 of 8 | 79 + 5 container checks | 4 (4) | 1: a model switch | [archive/run-01/](pi/archive/run-01/SUMMARY.md) |
| dsh, run 1 | 1.5.0 `6e98daa` | 8 of 8 | 79 + container checks | 9 (9) | each round's commit, 2 closure assists | [archive/run-01/](dsh/archive/run-01/SUMMARY.md) |
| Claude Code, run 2 | `develop` after run 1's fixes | not started | | | | [claude_code/](claude_code/README.md) |
| Codex, run 2 | `develop` after run 1's fixes | not started | | | | [codex/](codex/README.md) |
| OpenCode, run 2 | `develop` after run 1's fixes | not started | | | | [opencode/](opencode/README.md) |
| Pi, run 2 | `develop` after run 1's fixes | not started | | | | [pi/](pi/README.md) |
| dsh, run 2 | `develop` after run 1's fixes | not started | | | | [dsh/](dsh/README.md) |

"Fixed" counts feedback applied in the base factory or in the run 2 brief, and documented limits;
the one open item is Claude Code's FB-001, an upstream Archify constraint. Run 1's
lesson for every harness: open at the repository root, one harness at a time, and keep live logs and
rendered HTML out of git ([`plan/00-start.md`](plan/00-start.md)).

Shared tools: [`batteries/hooks_battery.py`](batteries/hooks_battery.py) feeds real payloads to a
copy's Claude hooks.
