# 00 — Start: the Idea Board brief

Read this first. It is the brief every round refers to. The feature files `01`–`07` are built in
order, one round each, by an agent working inside a copy of Æion Factory **without git**.

## The product

**Idea Board** is a small team board. People propose ideas, vote for the ones they want, and
filter them by tag or text. It has no accounts: it is a playground, but a serious one, with real
validation, real persistence and tests.

| Actor | Wants to |
| --- | --- |
| Visitor | see the ideas, sorted by votes or by date, filter by tag, search by text |
| Contributor | propose an idea (title, description, tags), vote for an idea |
| Operator | run it locally or in Docker, know it is healthy, keep its data across restarts |

## Stack (use the latest stable version of each, and record the exact versions you used)

| Layer | Choice | Why |
| --- | --- | --- |
| Runtime | Deno 2 | one binary: runtime, test runner, formatter, linter, type checker |
| Web | Fresh 2 with its Vite plugin | server-side rendered pages, islands for the interactive parts |
| API | Hono, mounted under `/api` | a small, typed router; testable with `app.request()` without a server |
| Storage | Deno KV (SQLite file locally, `:memory:` in tests) | built in, atomic operations |
| Packaging | Docker + Compose on the official `denoland/deno` image | one command to run anywhere |

## Layout inside the factory copy

```
<factory copy>/
  app/                     the Deno + Fresh application (its own deno.json)
  knowledge/components/    one spec per feature, written before the code (skill `spec`)
  CHANGELOG.md             one Unreleased line per round
  work/current.md          the open round only
```

## How to run (the app must support all three by the end of round 06)

```bash
cd app && deno task dev                      # development, hot reload
cd app && deno task build && deno task start # production build, local
docker compose -f app/compose.yaml up --build    # container, with a persistent KV volume
```

## The round protocol (follow the factory, plain-folder mode)

1. Read the feature file. Write a short plan (skill `plan`): files touched, what will not be done.
2. Write or update the spec in `knowledge/components/` (skill `spec`, eight sections). Link it from
   `knowledge/components/index.md`. Each code file it covers carries a `Spec:` header line.
3. Write the acceptance tests first and see them fail, then build in thin slices (skills `test`, `build`).
4. Run the batteries:
   - app: `deno fmt --check`, `deno lint`, `deno check`, `deno test -A`, then the smoke commands from the feature file;
   - factory: `tools/knowledge/knowledge-check`, `tools/knowledge/doc-links`, `tools/repo-health/repo-health`.
5. Sign off without git (skill `sign-off`): the tests and docs are the whole sign-off, plus the
   `CHANGELOG.md` line and `work/current.md`.
6. Write down what happened: what you did, where you got stuck, what the framework helped with or got
   in the way of. A friction with the framework is a feedback for Æion Factory.

## Harness setup (one folder per agent harness)

Each harness runs the same briefs in its own folder, so the runs can be compared:

```
demo/<harness>/
  README.md              the run: factory version, results by round, time, findings
  journal/               00-setup.md, then one file per round (img/ for screenshots); run.jsonl, the live log
  feedback_for_aeion/    FB-001-<slug>.md …: severity, evidence, proposed fix, status
  render/                round 07 output: diagrams/*.json, graphify/; the HTML is generated
  workspace/             the factory copy without git (git archive HEAD); the app goes in workspace/app/
  workspace.run-NN/      an earlier run's copy, kept
  archive/run-NN/        after round 07: SUMMARY.md, journal/, feedback_for_aeion/ and render/ of that run
```

The parent repository ignores `workspace/`, `workspace.run-NN/`, live logs (`journal/*.jsonl`) and
rendered HTML (`render/**/*.html`); everything else is committed. A tracked live log changes on every
step, so every checkpoint receipt would be stale. When a run ends, its journal, feedback and render
move to `archive/run-NN/` with a `SUMMARY.md`, and the folder is reset for the next run.

Open the agent at the **repository root** (run 2 does). `demo/` also works for reading, because
`demo/` sits inside the git repository: the agent loads the root `AGENTS.md` (Codex walks from
the git root down; OpenCode takes the nearest one above, so `demo/` has none of its own), the hook
commands resolve the root with `git rev-parse --show-toplevel`, and OpenCode's plugin finds
`tools/agents/hook.py` above it. Opened in `demo/`, the agent's paths start at `demo/`
(`<harness>/workspace/`), and a sandbox that writes only below its folder refuses `.git` and the root
`work/` that the 250k closure writes (dsh run 1).
The agent writes the app under `demo/<harness>/workspace/` and its records beside it, and runs the
copy's commands from inside the copy. Opening the agent inside `workspace/` does not isolate it: the
copy has no `.git`, so the agent's project discovery and the hook commands still resolve to the parent
repository. Its sandbox may also refuse writes to the `journal/` beside the copy.

## Run rules (from run 1)

- **One harness at a time.** Two sessions in one tree make each other's checkpoint receipts stale.
- **One commit per round**, with only `demo/<harness>/` files, after the round's checks pass. When the
  sandbox refuses the commit (a headless run has no approval channel), the agent stops with the staged
  paths and the commit command, the driver runs it, and the round records one human help. Never a
  workaround.
- **The task entry and the handover have fixed shapes.** A `work/current.md` entry is
  `## <title> — <STATUS>`, then `- Owner decision:`, `- State:`, `- Next:` (naming who), `- Blocker:`,
  `- Evidence:`. A handover uses exactly the lines `## Scope`, `## Completed`, `## Remaining`,
  `## Constraints`, `## Evidence`. `knowledge-check` and the 250k message say the same.

Round 00 (setup) checks the factory copy itself before any app code:
- run the copy's own test command (`tools/factory-tests`) inside `workspace/`;
- run `python3 ../../batteries/hooks_battery.py .` from `workspace/`. It feeds real hook payloads to
  the copy's Claude hooks, because a session opened in the parent runs the parent's hooks;
- note which of the copy's hooks your harness actually runs.

## What every round records (so harnesses compare)

End each round's journal with this block, filled with real values:

```text
Result:          done | partial | blocked
Tests:           <n> passed, <n> failed (deno test -A), plus each acceptance command's outcome
Factory checks:  knowledge-check, doc-links, repo-health: PASS | FAIL (rule)
Guard refusals:  <n> (each with the command's intent and the rule that refused it)
Human help:      <n> (each time the owner had to act: approval, terminal command, fix)
Feedback:        FB-<id> new or seen again
Elapsed:         <minutes>
```

A guard refusal is not a failure: it is data. Write down whether it was right (a real risk) or a
false positive, and how you got past it without weakening the guard.

## Shared rules for every round

- Validation errors are stable JSON: `{"error": "invalid", "field": "<name>", "reason": "<code>"}`.
- No secret in the repository. Configuration comes from the process environment (`PORT`, `KV_PATH`), with safe defaults.
- User text is never rendered as HTML.
- A round is done only when every acceptance check of its file passes, with the command output kept.
