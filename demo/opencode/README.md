# The OpenCode run

OpenCode builds the same Idea Board from [`../plan/`](../plan/00-start.md) as the
[first Claude Code run](../claude_code/archive/run-01/SUMMARY.md), so the runs can be compared round by round.

- **Factory copy:** `workspace/` (not tracked), made from `develop` at `578f4f3` (release 1.4.0). It has the base fixes
  from the Claude Code run (FB-002, 004 to 010), Graphify 0.9.97 and Archify v3.2.0 pins, and
  `tools/architecture/ask` with `ask callers`, and the skill `graph`.
- **Command:** `git archive HEAD -- . ':(exclude)demo'`. The copy has no `.git`, holds 163 files and
  no app yet.
- **Fresh state:** `tools/release/release --new-project` reset the copy (FB-003 of run 1, FB-001 of
  Codex): `VERSION` 0.1.0, an empty changelog, and only the "Adapt" task in `work/current.md`.

| Folder | What goes there |
| --- | --- |
| `journal/` | `00-setup.md`, then one file per round, each ending with the record block of `00-start.md` |
| `feedback_for_aeion/` | `FB-001-<slug>.md` …, numbered from 001 for this run, plus an index `README.md` |
| `render/` | round 07: `index.html`, `diagrams/`, `graphify/` |
| `archive/run-NN/` | after round 07: `SUMMARY.md`, `journal/` and `feedback_for_aeion/`; the rest is reset |
| `workspace/` | the factory copy; the app goes in `workspace/app/` |

## Starting OpenCode
1. OpenCode 2.1.4+ with a provider already connected in its own settings. It loads the root
   `opencode.json` and `.opencode/plugins/factory/` (the hooks), and the nearest `AGENTS.md` above
   `demo/`, which is the root one: `demo/` has none of its own on purpose.
2. Open OpenCode in `demo/` (or at the repository root; [`../plan/00-start.md`](../plan/00-start.md)
   says why both work).
3. Paste [`../start/opencode/START.md`](../start/opencode/START.md) as the first message.

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
