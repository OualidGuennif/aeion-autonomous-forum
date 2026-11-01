# The Claude Code run 2

Claude Code builds the same Idea Board again from [`../plan/`](../plan/00-start.md) as the
[first Claude Code run](archive/run-01/SUMMARY.md), so the runs can be compared round by round.

- **Factory copy:** `workspace/` (not tracked), made from `develop` at `6e98daa` (release 1.5.0). It has the base fixes
  from the Claude Code run (FB-002, 004 to 010), Graphify 0.9.97 and Archify v3.2.0 pins, and
  `tools/architecture/ask` with `ask callers`, and the skill `graph`. The point of this run is to
  check that every fix from run 1 holds.
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

## Starting Claude Code
1. Open Claude Code in `demo/`: the first message's paths start there. Setup and the hook check are in
   [`../start/README.md`](../start/README.md).
2. Paste [`../start/claude_code/START.md`](../start/claude_code/START.md) as the first message.

## Results by round
To fill in as the rounds are done.

| Round | Result | Tests | Guard refusals | Human help | Feedback |
| --- | --- | --- | --- | --- | --- |
