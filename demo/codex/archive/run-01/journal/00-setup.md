# Round 00 — Workspace qualification

Date: 2026-10-28. Mode: parent repository `develop`; workspace is a plain folder.

## Scope and plan

The owner requested `start/codex/START.md` and the beginning of this run. Its round 00 instruction
authorizes the setup checks now, then requires approval before round 01. Use the `plan` and
`sign-off` skills: inspect the copy, run its factory suites and hook battery, retain command output,
and update this run's records. No app code, dependency installs, base-factory fixes or pushes.

## What ran

From `codex/workspace/`:

| Command | Result | Evidence |
| --- | --- | --- |
| `tools/factory-tests` | PASS: 743 passed, 0 failed, 2 skipped | [factory-tests.log](evidence/00/factory-tests.log) |
| `python3 ../../batteries/hooks_battery.py .` | PASS: 10 asserted cases, 5 informational cases, 0 failures | [hooks-battery.log](evidence/00/hooks-battery.log) |
| `tools/knowledge/knowledge-check` | PASS: 0 problems | [knowledge-check.log](evidence/00/knowledge-check.log) |
| `tools/knowledge/doc-links` | PASS: 61 links | [doc-links.log](evidence/00/doc-links.log) |
| `tools/repo-health/repo-health` | PASS | [repo-health.log](evidence/00/repo-health.log) |

The 743 total combines the suites' assertions and test cases, rather than counting only unittest
methods. The skipped release case requires the factory's own checkout with `demo/`; the skipped
extraction case requires a Graphify venv in this copy. No installation was attempted. The graph
brief instead directs later queries through the parent's `tools/architecture/ask --root`.

Runtime: Python 3.15.2, Node v26.12.1, Deno 2.9.8 stable (TypeScript 6.0.3).
[Runtime output](evidence/00/runtime.txt). Fresh and Hono are not installed or selected in setup.

## Hooks and limits

This session starts in `demo/`, so native hook commands resolve the parent git root. The session
received its Codex checkpoint context and a token-feedback narration notice, providing evidence
of parent hook dispatch. The copy's `.codex/config.toml`, `.codex/hooks.json`, adapter and context
budget script matched the parent at setup. No hook configuration edit was needed.

The battery invokes the copy's Claude hook scripts directly; its denials are expected test
results. It does not prove that Codex independently discovers the nested copy's hooks. Its five
correct refusals cover secret reads, secret-targeting searches and environment dumps; none of
those payload commands is executed. No live secret probe was used. The adapter regression suite
also passed; a live 250k/300k compaction cycle remains untested in this round.

## Outcome

The reset copy has no `.git` and no `app/`. Factory checks and the round evidence are ready.
No new factory friction was found. The README's reset feedback reference predates this run and
was not counted as a newly observed issue. Round 01 awaits the owner's yes under
[`START.md`](../../../../start/codex/START.md). Only this run's records are prepared for the round commit;
the workspace changelog and task state are updated locally and remain git-ignored.

```text
Result:          done
Tests:           743 passed, 0 failed, 2 skipped (factory); app tests not applicable; hook battery 10 passed, 0 failed, 5 informational
Factory checks:  knowledge-check, doc-links, repo-health: PASS
Guard refusals:  0 during work; 5 expected, correct synthetic denials in the hook battery (secret reads/search and environment dumps)
Human help:      0 during setup; round commit uses the required sandbox approval request
Feedback:        none new
Elapsed:         approximately 3 minutes for setup and checks, excluding commit approval
```
