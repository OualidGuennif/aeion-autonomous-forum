# The Pi run

Pi builds the same Idea Board from [`../plan/`](../plan/00-start.md) as the
[first Claude Code run](../claude_code/archive/run-01/SUMMARY.md), so the runs can be compared round by round.

- **Factory copy:** `workspace/` (not tracked), made from `develop` at `6e98daa` (release 1.5.0). It has
  the one 250k/300k context rule, Pi's resume after compaction, the base fixes from the Claude Code run (FB-002, 004 to 010), Graphify 0.9.97 and Archify v3.2.0 pins,
  `tools/architecture/ask` with `ask callers`, and the skill `graph`.
- **Command:** `git archive HEAD -- . ':(exclude)demo'`. The copy has no `.git` and no app yet.
- **Fresh state:** `tools/release/release --new-project` reset the copy (FB-003 of run 1, FB-001 of
  Codex): `VERSION` 0.1.0, an empty changelog, and only the "Adapt" task in `work/current.md`.

| Folder | What goes there |
| --- | --- |
| `journal/` | `00-setup.md`, then one file per round, each ending with the record block of `00-start.md` |
| `feedback_for_aeion/` | `FB-001-<slug>.md` …, numbered from 001 for this run, plus an index `README.md` |
| `render/` | round 07: `index.html`, `diagrams/`, `graphify/` |
| `archive/run-NN/` | after round 07: `SUMMARY.md`, `journal/` and `feedback_for_aeion/`; the rest is reset |
| `workspace/` | the factory copy; the app goes in `workspace/app/` |

## Starting Pi
1. Pi 1.4.0+ with a provider already connected in its own settings. It reads the root `AGENTS.md` and
   `.agents/skills` from any folder below the root, but loads `.pi/extensions/` only from the folder it
   starts in, so from `demo/` the extension (the hooks) is passed explicitly:
   ```bash
   cd demo && pi -e ../.pi/extensions/factory/index.js
   ```
   The extension finds `tools/agents/hook.py` by walking up from `demo/`. Started at the repository
   root, `tools/agents/run pi` loads it on its own once the project is trusted.
2. Paste [`../start/pi/START.md`](../start/pi/START.md) as the first message.

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
