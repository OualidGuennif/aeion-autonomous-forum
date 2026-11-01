# The OpenCode run 1, archived

OpenCode built the Idea Board from [`../../../plan/`](../../../plan/00-start.md) on 2026-10-28, in one session,
in a git-less copy of Æion Factory 1.4.0 (`develop` at `578f4f3`), with Deno 2.9.8 and Docker 29.9.2.
The owner pre-authorized every round.

| Folder | What it holds |
| --- | --- |
| [`journal/`](journal/) | one file per round with the record block, and `evidence/` |
| [`feedback_for_aeion/`](feedback_for_aeion/README.md) | 3 frictions with the factory, all fixed in the base |
| [`render/`](render/) | the diagram JSONs and the Graphify answers; the HTML is rebuilt with `tools/architecture/gallery` |

## Results by round

| Round | Result | Tests | Feedback |
| --- | --- | --- | --- |
| 00 setup | done | 736 factory cases; hooks battery 0 failures | none |
| 01 scaffold | done | 3; build, HTTP and dev smoke | FB-002 |
| 02 ideas API | done | 24 | none |
| 03 ideas page | done | 31 | none |
| 04 votes | done | 42; 25 of 25 concurrent votes | FB-002 |
| 05 tags and search | done | 51 | none |
| 06 Docker | done | 51 + 5 container checks (healthy, UID 1993, data kept, frozen lock) | none |
| 07 architecture | done | 14 diagrams validate, render and browser-check; 5 graph answers | FB-001, FB-002, FB-003 |

## What it showed
- Every guard refusal was correct, but two were costly: `cat` of an absolute path after a `cd`
  (FB-002) and an `ask` answer kept stale by Graphify's cache after a frontmatter-only edit (FB-001).
  Both are fixed in `c3f586f`.
- Ten optimistic retries cannot land 25 truly concurrent votes; a per-idea chain in the process
  removes the self-contention while the atomic check still guards other writers.
- Two runs sharing one working tree made each other's checkpoint receipts stale (FB-003): the
  receipt now names the changed paths, and run 2 runs one harness at a time.

## Human help
Two: the authorization for all rounds, and a mid-round compaction request in round 05.
