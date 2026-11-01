# 00 — Setup

Round 00 checks the factory copy before any app code. Date: 2026-10-29.

## What the copy is

- Made with `git archive HEAD -- . ':(exclude=demo)'` from `develop` at `6e98daa` (release 1.5.0),
  then `tools/release/release --new-project`: `VERSION` is `0.1.0`, `CHANGELOG.md` has an empty
  `[Unreleased]`, and `work/current.md` holds only the "Adapt Æion Factory to this project" task.
- No `.git` in the copy (`test -d .git` → absent), so this run is the plain-folder mode: tests and
  docs are the sign-off, and nothing is pushed.
- Toolchain on the machine: Python 3.15.2, Deno 2.9.8, Node v26.12.1.

## Session

Opened in `demo/` with the parent's extension:
`pi -e ../.pi/extensions/factory/index.js`. The extension walks up from `demo/` for
`tools/agents/hook.py`, finds the parent repository, and runs the parent's hooks — the same
resolution the brief describes. The copy's own hooks therefore do not fire in this session; they
were exercised directly with the battery instead.

## Checks run

### The copy's own test suites

```
cd demo/pi/workspace && tools/factory-tests
```

`factory-tests: PASS`, in CI order and with no failure: doc-links, the Claude hook tests, token
feedback, repo-health, pre-push, release, budget, architecture refresh and `ask`, the shared hook
adapter, the OpenCode, Pi and dsh suites, and the loops tests. The Pi suite is the one that matters
here: 8 of 8 passed, including "Pi past 250k is held to closure by its own context measure" and
"Pi compacts itself once the hook reports a verified handover, then resumes from it".

### Hooks battery against the copy

```
cd demo/pi/workspace && python3 ../../batteries/hooks_battery.py .
```

`hooks battery: 0 failure(s)` over 15 cases. The refusals that must hold do hold: `.env` read and
grep, `cat app/.env`, `docker compose exec web printenv`, `deno eval 'Deno.env.toObject()'`. The
allowed ones stay allowed: `.env.example`, `deno.json`, `app/data/ideas.sqlite3` (a data file, not a
secret), `cd app && deno test -A`, `head -n 20 app/deno.json`. The informational cases
(`cat app/deno.json`, `git init`, SessionStart, after-edit, sign-off) all exited 0; in a real
session `cat` is refused by the bounded-intake guard, as it was for me below.

### Which of the copy's hooks this harness runs

The extension registers five events and forwards each to `tools/agents/hook.py pi`, which calls the
backend scripts:

| Pi event | Adapter handler | What runs |
| --- | --- | --- |
| `before_agent_start` | `SessionStart` | `session-start.py`: the factory context once per session, the checkpoint profile line, the handover path and key |
| `tool_call` | `PreToolUse` | `guard-bash.py` for shell calls, `guard-path.py` for paths, plus the 250k/300k closure check and `compact: true` once the checkpoint is verified |
| `tool_result` | `PostToolUse` | `after-edit.py` per edited file, `token-feedback.py` advisory, closure measurement |
| `session_before_compact` | `PreCompact` | refuses without a verified handover, creates the handover file at the first refusal |
| `session_compact` | `SessionStart` with `source: compact` | resets the checkpoint, bumps the epoch, owes closure if one was pending |
| — | `Stop` | not registered by the extension, so `sign-off.py` never fires in a Pi session |

The last line is the one difference from a Claude Code session: Pi has no stop hook here, so the
end-of-task sign-off reminder depends on the skill `sign-off`, not on the guard.

The adapter also backs `tools/architecture/ask --root pi/workspace` from the parent's Graphify venv
(`../tools/architecture/ask` answered `--help`); the copy's own `tools/architecture/ask` has no venv,
as the brief says.

## Guard refusals this round

1. `cd … && git status; …; cat demo/pi/workspace/VERSION` — refused by `guard-bash.py`: "bounded
   intake: cannot size cat after a cd". Right call: the chain ended in an unbounded dump. Fixed by
   splitting the command and putting `head -n 3` last in its own pipeline.

## What the framework did well, and where it got in the way

- Helped: the copy's suites ran first try and the battery is a genuinely quick way to prove the
  guards still decide what they claim; the session-start context arrived with the checkpoint key and
  handover path, so a compaction later needs no search.
- Got in the way: the bounded-intake rule is stated as a size limit but enforced partly on shape
  (`cat` after `cd`, one pipeline), which reads stricter than the policy's own sentence.

## Result

```text
Result:          done
Tests:           factory-tests PASS (all suites, including Pi 8/8); hooks battery 15 cases, 0 failures
Factory checks:  knowledge-check, doc-links, repo-health: run in round 01 (not part of round 00)
Guard refusals:  1 (bounded-intake on a chained cat; right, fixed by splitting the command)
Human help:      1 (owner paused the run and switched the model, then resumed)
Feedback:        none yet
Elapsed:         ~15 minutes
```
