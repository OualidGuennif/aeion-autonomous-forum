# The Pi run 1, archived

Pi built the Idea Board from [`../../../plan/`](../../../plan/00-start.md) on 2026-10-29, in a git-less copy of
Æion Factory 1.5.0 (`develop` at `6e98daa`), with the one 250k/300k context rule for every harness.

| Folder | What it holds |
| --- | --- |
| [`journal/`](journal/) | one file per round with the record block |
| [`feedback_for_aeion/`](feedback_for_aeion/README.md) | 4 frictions with the factory |
| [`render/`](render/) | the 14 diagram JSONs and the Graphify answers; its gallery builder became `tools/architecture/gallery` |

## Results by round

| Round | Result | Tests | Feedback |
| --- | --- | --- | --- |
| 00 setup | factory-tests and the hooks battery pass | battery 15/15 | none |
| 01 scaffold | Fresh 2 + Vite, Hono under `/api`, health route | 6 | FB-001 |
| 02 ideas API | create, list, read on Deno KV; monotonic ULIDs | 38 | none |
| 03 ideas page | SSR page, a no-JavaScript form and one island | 50 | none |
| 04 votes | atomic vote and vote index; 25 concurrent votes all count | 66 | none |
| 05 tags and search | tag index, `tag=` and `q=` combined | 79 | none |
| 06 Docker | pinned image, `deno` user, named volume, health check | 79 + 5 container checks | none |
| 07 architecture | 14 validated diagrams, an offline gallery, five `ask` questions; the graph found a spec/source gap | 79 + 14/14 diagram gates | FB-002, FB-003, FB-004 |

## What it showed
- **The context rule works end to end.** The 250k alert fired in round 07; the session wrote its
  handover, updated `work/current.md`, verified the checkpoint, compacted and resumed the same round.
  The handover headings were documented nowhere (FB-002), nor the task entry shape (FB-001): both
  are now named by the messages and the checks.
- Pi had no Stop event, so `sign-off.py` never ran (FB-004): the extension now forwards
  `agent_before_settle`. `doc-links` now catches the spec/source gap the graph found (L4).
- The owner asked for a gallery that opens from `file://` with no server (FB-003).

## Human help
One: the owner switched the model in round 00.
