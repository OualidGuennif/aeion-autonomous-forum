# FB-007: at the 250k alert, closure can deadlock on the checkpoint command

- **Severity:** medium (the session cannot compact without the owner's terminal)
- **Found in:** round 03
- **Status:** applied 87a27b0

## Evidence
At the 250k alert, `tools/context/budget.py` lets only a closed set of tools through. For Bash it
admits only these 8 words: `python3 tools/context/budget.py checkpoint --session <id> --base <ref> --verify`.

1. The Bash working directory had stayed in `demo/claude_code/workspace` after a round's tests. The
   relative command then ran the **copy's** `budget.py`. That one looked for
   `workspace/.aeion/context/<session>.json` and refused with `No such file`.
2. Every way back to the repository root was refused: a `cd <root> && …` prefix, an absolute path to
   `budget.py`, and a bare `cd`. ToolSearch was blocked too, so no other tool could be loaded.
   Writing to `demo/` was refused as well, which is why this file was written only after compaction.
3. `/compact` is held by PreCompact until a verified checkpoint exists. The owner had to run the
   checkpoint from the repository root in their own terminal.
4. That first run printed only `Checkpoint refused: complete all handover sections first`. The real
   cause was one heading, `## Completed (commits on develop)`, because the check expects exactly
   `## Completed`. The message did not name it.

After the fix to the heading, the owner's run printed `Checkpoint: PASS`, and compaction went through.

## Seen again (round 05)
1. The alert came in the middle of a two-part edit of `app/store/ideas.ts`. The first part was
   applied; the second was refused, because only closure tools pass at the alert. The file did not
   compile until after compaction. The handover had to carry the exact missing lines.
2. The Bash working directory was `demo/claude_code/workspace/app`, so the admitted relative
   checkpoint command could not run from the session. The owner ran it again from the root.
3. That run printed only
   `Checkpoint refused: Command ('tools/knowledge/doc-links', '--diff', …, '--worktree') returned non-zero exit status 1`.
   The cause, `RULE L3 AGENTS.md changed, knowledge/components/factory-architecture.md did not`,
   appeared only when the owner ran `doc-links` by hand.

Two more items for the fix:
- On a failed sub-check, print its own output, or at least its `RULE` lines, in the refusal.
- Admit the edit tools on a file the session edited in the last turn, so a half-applied change can
  be completed (or reverted) before the closure.

## Seen again (round 07)
The alert came while the Bash working directory was `demo/claude_code/render`; the owner ran the
checkpoint from the root a third time. After compaction, the first tool calls were refused with
"native usage temporarily unavailable" until a retry.

## After the fix (87a27b0)
At the next alert, the session ran the checkpoint by absolute path from `demo/claude_code` and got
`Checkpoint: PASS` without the owner. The first tool calls after compaction were again refused with
"native usage temporarily unavailable"; the retry passed. That refusal is still open.

## Proposed fix
- At the alert, admit the checkpoint in any form that resolves to **this root's** `budget.py`: an
  absolute path, a `cd <root> &&` prefix, or a run from any working directory. Also admit a bare
  `cd <root>`.
- Name the missing or malformed headings in the refusal, for example
  `missing heading: ## Completed`.
- Tests in `tools/context/tests/test_budget.py`: each admitted form passes, `cd /elsewhere && …`
  stays refused, and the refusal names the heading.
