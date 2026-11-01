# The Claude Code run 1, archived

Claude Code (Opus) built the Idea Board from [`../plan/`](../plan/) in one long session on 2026-10-28.
It worked in a git-less copy of Æion Factory 1.3.0, taken from `develop` at `fb2af90`. That session had
no subagents, and the owner approved each round visually.

| Folder | What it holds |
| --- | --- |
| [`journal/`](journal/) | one file per round: the plan, the commands, the real output, where it got stuck |
| [`feedback_for_aeion/`](feedback_for_aeion/README.md) | 10 frictions with the factory: 8 fixed in the base, 2 open |
| `render/`, `result/` | removed when the run was archived (git keeps them at `578f4f3`): the 14-diagram gallery and Graphify answers, and a snapshot of the app and its three specs |

## Results by round

| Round | Result | Evidence |
| --- | --- | --- |
| [00 setup](journal/00-setup.md) | done | the copy's hooks battery reports 0 failures; FB-002 found |
| [01 scaffold](journal/01-scaffold.md) | done | 5 tests, HTTP smoke test; FB-003 to FB-006 found |
| [02 ideas API](journal/02-ideas-api.md) | done | 26 tests, HTTP smoke test |
| [03 ideas page](journal/03-ideas-page.md) | done | 38 tests; screenshots at 375 and 1440 px, light and dark |
| [04 votes](journal/04-votes.md) | done | 54 tests; 30 concurrent votes give exactly 30 |
| [05 tags and search](journal/05-tags-search.md) | done | 72 tests |
| [06 Docker](journal/06-docker.md) | done | healthy in 6 s; 5 container checks pass in 16.5 s |
| [07 architecture](journal/07-architecture.md) | done | 14 diagrams pass `validate` and `browser-check`; 5 Graphify questions |

The app's snapshot passed the same 72 tests and `deno task check` before it was removed.

## What the factory did well
- Writing the spec first kept every round small. Each code file names its spec in a `Spec:` line,
  and the factory checks run after every round.
- The guards stopped real risks: reading `.env`, `cat server.key`, and unbounded dumps of logs and
  lockfiles.

## Where it got in the way
- **Closure at the 250k alert** (FB-007). The owner had to run the checkpoint from their own terminal
  three times. This is fixed now, and the session ran it alone at the fourth alert.
- **False positives from the guards** (FB-008, FB-009). They cost a detour each time Deno KV code was
  edited from the shell. Both are fixed, with a few gaps left.
- **Graphify**: a structural graph alone answers "what depends on what" poorly on a Deno app:
  - question 2 misses the API's call to the store;
  - question 3 needed its own reachability script;
  - five files yield no symbols.

  The spec links are fixed (FB-010). Exact Deno import edges are the next step.

## Human help
- A visual approval before each next round.
- Three checkpoint runs at the 250k alert, before the FB-007 fix.
- The yes for each base fix and for this demo layout.
