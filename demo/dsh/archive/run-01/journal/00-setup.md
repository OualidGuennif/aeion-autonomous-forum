# Round 00 — Setup: the git-less copy and the framework's own batteries

**Agent:** dsh (DeepSeek Harness) 0.2.1-rc.3, one session. **Date:** 2026-10-29.
**Factory copy:** `dsh/workspace/`, from `develop` at `6e98daa` (release 1.5.0), exported with
`git archive HEAD -- . ':(exclude=demo)'` and reset with `tools/release/release --new-project`.

## What the copy is

- `VERSION` is `0.1.0`; `CHANGELOG.md` has an empty `[Unreleased]`; `work/current.md` holds only the
  "Adapt Æion Factory to this project" task. That is the fresh state the brief promises.
- No `.git` in the copy (`test -d .git` is false), so this run is plain-folder mode: the tests and the
  docs are the sign-off, and nothing is pushed.
- Toolchain on the machine: Deno 2.9.8, Docker 29.9.2, Python 3.15.2, Node v26.12.1.

## Session

Opened in `demo/` headless with the factory patch:
`dsh headless --patch ../.dsh/factory.patch.yml --json "$(cat start/dsh/START.md)"`; the `--json`
events are in `journal/run.jsonl`. The plugin walks up from `demo/` for `tools/agents/hook.py`, finds
the parent repository and runs the **parent's** hooks. The copy's own hooks therefore do not fire in
this session; they were exercised directly with the battery instead, as the brief says.

## Checks run

### The copy's own test suites

```text
cd demo/dsh/workspace && tools/factory-tests
```

`factory-tests: PASS` in CI order: 152 tests run, 150 passed, 2 skipped, 0 failed. The dsh suite is
the one that matters here: 5 of 5, including "dsh past 250k is held to closure by its own token
meter" and "dsh compacts before the next step once the hook reports a verified handover, then resumes".

### Hooks battery against the copy

```text
cd demo/dsh/workspace && python3 ../../batteries/hooks_battery.py .
```

`hooks battery: 0 failure(s)` over 15 cases (10 PASS, 5 informational, 0 failures).

- **Refused as they must be:** `app/.env` (read and Grep glob), `cat app/.env`,
  `docker compose exec web printenv`, `deno eval 'console.log(Deno.env.toObject())'`.
- **Allowed as they must be:** `app/.env.example`, `app/deno.json`, `app/data/ideas.sqlite3` (a data
  file, not a secret), `cd app && deno test -A`, `head -n 20 app/deno.json`.
- **Informational, exit 0:** `cat app/deno.json`, `git init`, SessionStart, after-edit, sign-off.

### Factory checks

| Command | Exit | Last line |
| --- | --- | --- |
| `tools/knowledge/knowledge-check` | 0 | `RESULT: PASS (0 problem(s))` |
| `tools/knowledge/doc-links` | 0 | `RESULT: PASS (64 links checked)` |
| `tools/repo-health/repo-health` | 0 | `AGENTS.md 119 lines, work/current.md 12, 7 top-level folders, 0 big files, 0 long comment blocks` |

## Which of the copy's hooks this harness runs

The dsh plugin (`.dsh/plugins/factory/index.js`) registers four forwards to
`tools/agents/hook.py dsh`, which calls the backend scripts:

| dsh event | Adapter event | What runs |
| --- | --- | --- |
| first tool call (`ctx.tools.guard`) | `SessionStart` | `session-start.py`: the factory context, the checkpoint profile line, the handover path and key. **The hook returns it, but the plugin drops it** — see FB-001 |
| every tool call (`ctx.tools.guard`) | `PreToolUse` | `guard-bash.py` for shell calls, `guard-path.py` for paths, plus the 250k/300k closure check and `compact: true` once the checkpoint is verified |
| `tools/post-execute` | `PostToolUse` | `after-edit.py` per edited file, `token-feedback.py` advisory, closure measurement |
| `agent/pre-step` | `PreCompact` | refuses without a verified handover, creates the handover file at the first refusal, then compacts |
| — | `Stop` | **not registered**, so `sign-off.py` never fires in a dsh session — see FB-002 |

Empirical check: the first tool call did write the session state
(`.aeion/context/dsh-e5596e….json`: `"bootstrapped": true, "state": "RUNNING"`), and the bounded-intake
refusal below is a real `PreToolUse` decision from the parent's `guard-bash.py`. So the guard and the
edit listeners do run; the two gaps are the SessionStart context and the Stop sign-off.

## Guard refusals this round

1. `cd dsh/workspace && cat VERSION CHANGELOG.md work/current.md` — refused by `guard-bash.py`:
   "bounded intake: cannot size cat after a cd". Right call: after a `cd` the guard cannot size the
   dump, and the rule names the fix. I read the three files with the `read` tool instead, which is
   also the policy's preferred path.

## Commit

The round's records are these explicit paths (never `workspace/` or `run.jsonl`), but the sandbox
refused the index write and headless dsh has no approval channel:

```text
$ git add demo/dsh/README.md demo/dsh/journal/00-setup.md demo/dsh/feedback_for_aeion/*.md
fatal: Unable to create '/Users/wallseven/Desktop/aeion-factory/.git/index.lock': Operation not permitted
[sandbox: file access denied under workspace-write mode]
$ # retry with the wider mode
Error: sandbox escalation to "danger-full-access" requires approval, but no approval channel is available
```

The owner has to run the commit; filed as FB-003. The command is:

```bash
cd /Users/wallseven/Desktop/aeion-factory
git add demo/dsh/README.md demo/dsh/journal/00-setup.md demo/dsh/feedback_for_aeion/*.md
git commit -m "demo(dsh): round 00 setup"
```

## What the framework did well, and where it got in the way

- Helped: `tools/factory-tests` passed first try, the battery is a quick way to prove the guards still
  decide what they claim, and the dsh suite already covers the closure and compaction paths. The
  plugin's guard refuses a call with no agent, so an unattributed tool call fails closed.
- Got in the way: the plugin's `start()` discards the SessionStart `additionalContext` (the handover
  path, checkpoint key and the 250k/300k rule never reach the model), and it has no Stop forward, so
  the sign-off reminder that every other adapter has never runs. Both are filed. The `workspace-write`
  sandbox also blocks the round's commit and headless has no answerer to ask, so the owner commits.
- `journal/run.jsonl` (about 240 KB) is the session's own event log written by the headless form of the
  README's command. It is evidence, not a round record; it is left untracked rather than committed.

## Result

```text
Result:          done
Tests:           152 run, 150 passed, 2 skipped, 0 failed (factory-tests); hooks battery 15 cases, 0 failures
Factory checks:  knowledge-check PASS, doc-links PASS, repo-health PASS
Guard refusals:  1 (bounded-intake on a chained cat; right, fixed with the read tool)
Human help:      1 (the commit: .git writes are outside the sandbox and headless has no approval channel)
Feedback:        FB-001, FB-002, FB-003 new
Elapsed:         ~30 minutes
```
