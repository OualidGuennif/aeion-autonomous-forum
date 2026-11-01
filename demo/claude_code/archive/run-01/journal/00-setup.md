# Round 00 — Setup: a git-less copy and the framework's own batteries

**Agent:** Claude Code (Opus 5.5), one session, no subagent. **Started:** 2026-10-28T14:10Z.
**Factory version copied:** 1.3.0, commit `fb2af90` of this repository.

## What I did, step by step

1. **Made the copy without git.** It is not a clone: it is the committed tree, exported.
   ```bash
   mkdir -p demo/claude_code/workspace
   git archive HEAD | tar -x -C demo/claude_code/workspace
   ```
   Result: the export has 153 regular files and 16 symlinks. The symlinks are the `.agents/skills/*` links and `CLAUDE.md → AGENTS.md`. There is no `.git` folder.

2. **Ran every command of the copy's "Build and test" section from inside the copy.**

   | Command | Exit | Last line |
   | --- | --- | --- |
   | `tools/knowledge/knowledge-check` | 0 | `RESULT: PASS (0 problem(s))` |
   | `tools/knowledge/doc-links` | 0 | `RESULT: PASS (54 links checked)` |
   | `tools/repo-health/repo-health` | 0 | `7 top-level folders, 0 big files` |
   | `test_doc_links.py` | 0 | `40 passed, 0 failed` |
   | `.claude/hooks/test_hooks.py` | 0 | `hook tests: 522 passed, 0 failed` |
   | `test_token_feedback.py`, `test_repo_health.py`, `test_pre_push.py` | 0 | `OK` |
   | `test_budget.py`, `test_refresh.py`, `tools/agents/tests/test_hooks.py` | 0 | `OK` |
   | `node --test test_opencode.mjs` | 0 | 3 pass |
   | `test_loops.py`, `test_release.py` | 0 | `OK` |
   | `loops/orchestrator.sh outer --dry-run` | 0 | `dry run: nothing executed` |
   | `tools/architecture/refresh --target HEAD` | **1** | `FAILED (run tools/architecture/setup first)` |

3. **Ran the copy's hooks with real payloads.** A session opened in this repository runs the parent's hooks, not the copy's. To see what the copy itself would decide, I wrote [`../../batteries/hooks_battery.py`](../../batteries/hooks_battery.py), which pipes the JSON a Claude hook receives into each hook.
   ```bash
   cd demo/claude_code/workspace && python3 ../../batteries/hooks_battery.py .
   ```
   Result: `hooks battery: 0 failure(s)`.
   - **Refused:**
     - reading `app/.env`;
     - a Grep glob `**/.env*`;
     - `cat app/.env`;
     - `docker compose exec web printenv`;
     - `deno eval 'console.log(Deno.env.toObject())'`.
   - **Allowed:**
     - `app/.env.example`;
     - `app/deno.json`;
     - the KV data file;
     - `deno test -A`.
   - **`session-start`** prints `git: inactive (plain folder); the branch, review, CI and release flow is skipped.` That is right.
   - **`sign-off`** stays silent. That is also right: without git the tests and docs are the whole sign-off.

## Where I got stuck

- **The copy is not really git-less.** It sits inside this repository, so `git` run from the copy walks up and finds the parent's `.git`.
  - `git rev-parse --show-toplevel` in the copy prints the parent's path.
  - The hooks, `doc-links`, `repo-health` and `budget.py` handle this correctly: they use `project_files.git_active()`, which treats a nested folder as a plain folder.
  - `tools/architecture/refresh` does not use it. It resolved `HEAD` to the **parent's** commit and only failed later, with a misleading "run setup first".
  - If the copy had an `.aeion/` toolchain, `refresh` would have analysed the wrong tree. Filed as [FB-002](../feedback_for_aeion/FB-002-refresh-nested-plain-folder.md).
- **Writing the battery from the parent session.** The parent's `guard-bash` (correctly) refused my first attempt, a shell command whose text contained `.env`. I wrote the payloads into a Python file with the Write tool instead. That is the documented way, so it is not a defect.

## Result

The factory works in a plain folder: 14 of 15 checks pass with no change. The architecture refresh is the one tool that needs git, and it does not say so. The Graphify and Archify path without git is tested in round 07.
