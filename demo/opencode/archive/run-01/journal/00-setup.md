# Round 00 — Setup

Plan: qualify the existing plain-folder copy (`workspace/`, no git), use only `opencode/`, run the
copy's own test suites and the shared hook battery, probe that this harness's OpenCode plugin really
dispatches the guards live, then run rounds 01–07 in order. Owner authorized all rounds on
2026-10-28; this overrides the per-round pause. Commits touch only `opencode/`; never push.

Baseline: factory copy 1.4.0 from `578f4f3` (git archive HEAD, excludes `demo/`), reset by
`tools/release/release --new-project`: VERSION 0.1.0, empty Unreleased changelog, only the Adapt
task in `work/current.md`. Parent branch `develop` at `4dbe8ff` (its working tree also holds another
harness's uncommitted round records; not touched). Deno 2.9.8, Docker 29.9.2, Compose v5.6.1.

Hooks actually run by this harness: the session's `.opencode/plugins/factory` finds the parent's
`tools/agents/hook.py` (nearest root above `demo/`) and dispatches natively — observed live: the
SessionStart checkpoint profile at startup, and a `PreToolUse` refusal of `cat app/.env` before
execution (guard-bash, correct refusal of a real risk). The copy's own `.claude/hooks/*` (same
guards) are exercised by the battery below; a session opened inside the copy would dispatch them
through the copy's own plugin.

Hook fixture battery (`python3 ../../batteries/hooks_battery.py .` from `workspace/`):
0 failures — 10 PASS cases, 5 informational probes. Evidence: `evidence/00-hooks-battery.txt`.

Factory tests inside `workspace/` (`tools/factory-tests`): PASS — 736 test cases (602 pytest,
124 unittest, 10 node), 0 failed, 2 skipped (release, architecture refresh). Evidence:
`evidence/00-factory.txt`. Factory checks on the pristine copy: knowledge-check PASS (0 problems),
doc-links PASS (60 links), repo-health PASS. Evidence: `evidence/00-factory-checks.txt`.

Graph tool from `demo/`: `../tools/architecture/ask --root opencode/workspace status` works without
running the copy's setup — 1227 nodes, 2114 edges, 167 files, 0 unresolved Spec: lines.

Result:          done
Tests:           736 factory cases passed, 0 failed (deno not yet used); hook battery 0 failures; checks PASS
Factory checks:  knowledge-check, doc-links, repo-health: PASS (pristine copy)
Guard refusals:  1 native live (`cat app/.env` — guard-bash, real risk, passed by not reading it)
Human help:      1 (authorization to run all rounds without the per-round pause)
Feedback:        none new
Elapsed:         12 minutes
