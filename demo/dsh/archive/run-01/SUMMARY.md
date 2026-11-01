# The dsh run 1, archived

dsh (DeepSeek Harness) built the Idea Board from [`../../../plan/`](../../../plan/00-start.md) on 2026-10-29,
headless from `demo/`, in a git-less copy of Æion Factory 1.5.0 (`develop` at `6e98daa`). Its sandbox
writes only below its launch folder and headless has no approval channel, so the base session
committed each round and wrote the 250k closure files.

| Folder | What it holds |
| --- | --- |
| [`journal/`](journal/) | one file per round with the record block (the live `run.jsonl` stays local, git-ignored) |
| [`feedback_for_aeion/`](feedback_for_aeion/README.md) | 9 frictions with the factory and the brief |
| [`render/`](render/) | the 14 diagram JSONs and the Graphify answers; the HTML is rebuilt with `tools/architecture/gallery` |

## Results by round

| Round | Result | Tests | Feedback |
| --- | --- | --- | --- |
| 00 setup | done | factory-tests 150 passed, 2 skipped; hooks battery 0 failures | FB-001, FB-002, FB-003 |
| 01 scaffold | done | 6; fmt, lint, check, build; health 200 | FB-004 |
| 02 ideas API | done | 29; KV survived a restart | none |
| 03 ideas page | done | 39; form 303, invalid 400 keeps values | none |
| 04 votes | done | 52; 25 concurrent votes kept | FB-005 |
| 05 tags and search | done | 79; 500-idea search bound | FB-006, FB-007 |
| 06 Docker | done | container acceptance: healthy, data kept, UID 1993, stale lock refused | FB-008 |
| 07 architecture | done | 1,520 nodes; 5 graph questions; 6 stale spec links fixed; 14/14 diagrams | FB-009 |

## What it showed
- The guards ran from the first call, but dsh dropped the start context (FB-001) and sent no Stop
  (FB-002): the plugin now steers the context and forwards `agent/turn-stopping`.
- The sandbox shaped the run: no commit from `demo/` (FB-003), no handover at the root (FB-006),
  no `~/.docker` (FB-008). The limits are documented; run 2 launches at the root.
- A live log in the tree made every receipt stale (FB-007): journal logs are git-ignored and a
  stale receipt names what changed. `ask` kept stale frontmatter (FB-009): fixed.
- The brief itself needed `deno install` first (FB-004) and a vote rule that 10 retries can keep
  (FB-005): both go into the run 2 brief.

## Human help
The base session ran each round's commit, and twice assisted the 250k closure (handover and
`work/current.md` outside the sandbox).
