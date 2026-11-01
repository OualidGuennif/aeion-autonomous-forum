# FB-003: a new project inherits the factory's own task state and history

- **Severity:** low (confusing, not broken)
- **Found in:** round 01
- **Status:** applied (`tools/release/release --new-project` resets a copy's task state and history)

## Evidence
The copied factory, now the Idea Board project, arrived with things that belong to Æion Factory, not to the new project:
- `work/current.md` lists three Æion Factory tasks: "Live Devin, Droid and Antigravity hook qualification", "Native strict context admission" and "Adapt Æion Factory to this project". The session start prints them as the new project's open work.
- `CHANGELOG.md` carries the factory's 1.0–1.3 history, and `VERSION` says `1.3.0`.
- No skill or step says what to reset. Only the "Adapt" entry hints at it, through `AGENTS.md`.

What I did in the copy:
- I kept the template header of `work/current.md` and replaced the entries with the project's own.
- I restarted `CHANGELOG.md` at `Unreleased`.

## Proposed fix
Add a short "Start a new project" checklist, in `README.md` setup or as the first step of `efficiency/00-EFFICIENCY.md`. It would cover:
- empty `work/current.md` except for the "Adapt" entry;
- restart `CHANGELOG.md`;
- set `VERSION` to `0.1.0`;
- write the project paragraph in `AGENTS.md`.

The factory-only entries could also carry a marker such as `(factory)`, so they can be deleted mechanically.
