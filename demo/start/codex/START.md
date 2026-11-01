You are the Codex harness of the Æion Factory demo, run 2. You were opened at the root of the
`aeion-factory` git repository, on the branch `develop`.

Read first, in this order:
1. `AGENTS.md`: the factory's rules. They apply to everything you do here.
2. `demo/README.md`: what the demo is and how the harnesses compare.
3. `demo/plan/00-start.md`: the rules of the run, the folder layout and the record block that ends each
   journal file.
4. `demo/codex/README.md`: your run, and `demo/codex/archive/run-01/SUMMARY.md`: what run 1 found.

Your folder is `demo/codex/`. The factory copy is `demo/codex/workspace/`: no git; the app goes in
`demo/codex/workspace/app/`, and you run the copy's commands from inside it. Your records go beside it:
`demo/codex/journal/`, `demo/codex/feedback_for_aeion/` and `demo/codex/render/`. Touch nothing else, in
`demo/` or outside it, except what the 250k closure writes (`work/handovers/`, `work/current.md`).

How to work:
- Do round 00 (`demo/plan/00-start.md`) now. Then stop, report, and wait for my message before each
  next round (`demo/plan/01-scaffold.md` to `demo/plan/07-architecture.md`).
- For structure (where a symbol lives, what breaks, which tests to run, which spec covers a file), use
  the skill `graph` with the root's tool: `tools/architecture/ask --root demo/codex/workspace <command>`.
  The copy's own `tools/architecture/ask` has no Graphify venv; do not run its setup. Never run a
  Graphify command that calls a model.
- Each friction with the factory becomes a `demo/codex/feedback_for_aeion/FB-NNN-<slug>.md` file, numbered
  from 001, with evidence and a proposed fix. Do not fix the base factory yourself.
- Commit each round on `develop` as `demo(codex): round NN <what>`, with only `demo/codex/` files (the
  workspace and live logs are git-ignored). If your sandbox refuses the commit, stop with the staged
  paths and the commit command and record one human help; never work around it. Codex runs the project hooks once the project and its hooks are trusted; a refusal names its rule and fix: follow it, never bypass it. Never push.
- Never read or print a secret, and install nothing without my yes.
