# The dsh run

dsh (DeepSeek Harness) builds the same Idea Board from [`../plan/`](../plan/00-start.md) as the
[first Claude Code run](../claude_code/archive/run-01/SUMMARY.md), so the runs can be compared round by round.

- **Factory copy:** `workspace/` (not tracked), made from `develop` at `6e98daa` (release 1.5.0). It has
  the dsh plugin, the one 250k/300k context rule for every harness, and everything the Pi copy has.
- **Command:** `git archive HEAD -- . ':(exclude)demo'`. The copy has no `.git` and no app yet.
- **Fresh state:** `tools/release/release --new-project` reset the copy: `VERSION` 0.1.0, an empty
  changelog, and only the "Adapt" task in `work/current.md`.

| Folder | What goes there |
| --- | --- |
| `journal/` | `00-setup.md`, then one file per round, each ending with the record block of `00-start.md` |
| `feedback_for_aeion/` | `FB-001-<slug>.md` …, numbered from 001 for this run, plus an index `README.md` |
| `render/` | round 07: `index.html`, `diagrams/`, `graphify/` |
| `archive/run-NN/` | after round 07: `SUMMARY.md`, `journal/` and `feedback_for_aeion/`; the rest is reset |
| `workspace/` | the factory copy; the app goes in `workspace/app/` |

## Starting dsh
1. dsh 0.2.1+ with a provider already connected in its own settings. It reads the root `AGENTS.md` and
   `.agents/skills` from any folder below the root, but loads the factory plugin only through a patch
   overlay, which every start below passes. dsh ships no `tui` profile.
   - **Web** (chat in the browser; `--no-open` prints the URL instead):
     ```bash
     cd demo && dsh web --patch ../.dsh/factory.patch.yml
     ```
     Web listens on 3080; if another `dsh web` already holds it (`EADDRINUSE`), add `--port 0`.
   - **Headless** (one task per call, no browser or port; the first `--json` event gives the `sessionId`):
     ```bash
     cd demo && dsh headless --patch ../.dsh/factory.patch.yml --json "$(cat start/dsh/START.md)" | tee dsh/journal/run.jsonl
     dsh headless --patch ../.dsh/factory.patch.yml --session-id <id> "continue"
     ```
   The plugin finds `tools/agents/hook.py` by walking up from the session's folder. Started at the
   repository root, `tools/agents/run dsh [web|headless]` adds the same patch. In web, dsh reports
   `aeion-factory-compaction` as pending at startup: web keeps compaction inside each agent preset, so past
   250k the agent asks you to type `/compact` once its handover is verified. Headless compacts by itself.
2. In web, paste [`../start/dsh/START.md`](../start/dsh/START.md) as the first message.

## Results by round
Run 1 is archived in [`archive/run-01/`](archive/run-01/SUMMARY.md). Run 2 fills this table.

| Round | Result | Tests | Guard refusals | Human help | Feedback |
| --- | --- | --- | --- | --- | --- |
| 00 setup | not started | | | | |
| 01 scaffold | not started | | | | |
| 02 ideas API | not started | | | | |
| 03 ideas page | not started | | | | |
| 04 votes | not started | | | | |
| 05 tags and search | not started | | | | |
| 06 Docker | not started | | | | |
| 07 architecture | not started | | | | |
