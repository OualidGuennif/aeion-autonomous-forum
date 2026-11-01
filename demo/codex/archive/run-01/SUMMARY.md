# The Codex run 1, archived

Codex built the Idea Board from [`../../../plan/`](../../../plan/00-start.md) on 2026-10-28 and 29, in a git-less
copy of Æion Factory 1.4.0 (`develop` at `578f4f3`). The owner authorized rounds 01 to 07 together;
the sandbox wrote only below `demo/`, so it asked before each commit.

| Folder | What it holds |
| --- | --- |
| [`journal/`](journal/) | one file per round with the record block, and `evidence/` (command output, browser captures) |
| [`feedback_for_aeion/`](feedback_for_aeion/README.md) | 3 frictions with the factory: 2 fixed in the base, 1 documented |
| [`render/`](render/) | the 14 diagram JSONs, `catalog.json` and the five Graphify answers; the HTML is rebuilt with `tools/architecture/gallery` |

## Results by round

| Round | Result | Tests | Feedback |
| --- | --- | --- | --- |
| 00 setup | workspace and hooks qualified | 743 factory checks; 10 hook cases | none |
| 01 scaffold | Fresh 2 + Hono | 3; build and HTTP gates | FB-001 |
| 02 ideas API | validated API on Deno KV | 28 | none |
| 03 ideas page | SSR page, enhanced creation | 33; Chrome checks | none |
| 04 votes | atomic votes, sorting | 40 | none |
| 05 tags and search | indexed tags, bounded search | 49 | none |
| 06 Docker | locked non-root image, durable volume | 49; health, persistence, audit | FB-002 |
| 07 architecture | 14 diagrams, a source-checked graph report; an API startup regression fixed | 50; 14 validate/render/browser checks | FB-003 |

The final Docker readback kept the idea across `down`/`up`, ran as UID 1993 and was healthy in 5.5 s.

## What it showed
- The guards held: 5 expected denials in the round 00 battery, none against real work except the
  three frictions below.
- **False positives** cost a detour each: a copy of the environment handed to a child process
  (FB-001) and a heredoc of prose naming `.env` (FB-002). Both are fixed in `c3f586f`.
- `archify examples` writes into the Archify checkout (FB-003): the runbook now says so.

## Human help
One shared authorization for all rounds, and an approval for each commit.
