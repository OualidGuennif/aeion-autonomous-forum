# FB-002: the verified checkpoint requires handover headings that nothing states

- **Severity:** low (one refused checkpoint; the message names the headings only after a full handover write)
- **Found in:** round 07, during the 250k closure
- **Status:** applied c3f586f

## Evidence
The 250k alert said to update `work/handovers/pi-ab8c2bed0c9aecea9e8083610b4ebf1ddba677d4.md` and
verify the checkpoint, but not what that file must contain. I wrote a complete handover — purpose,
completed rounds, remaining work, constraints, evidence with real outputs — and the checkpoint
refused it:

```text
$ cd /Users/wallseven/Desktop/aeion-factory && python3 tools/context/budget.py checkpoint \
    --session pi-ab8c2bed0c9aecea9e8083610b4ebf1ddba677d4 --base origin/develop --verify
Checkpoint refused: complete all handover sections first: missing headings: ## Scope,
## Completed, ## Remaining, ## Constraints, ## Evidence (exact line)
```

The five headings are exact: `## Scope`, `## Completed`, `## Remaining`, `## Constraints`,
`## Evidence`, and the parser compares the whole line. `budget.ensure_handover` writes a template
with exactly those headings, but only when no handover file exists — an agent that creates the file
itself (the alert's instruction) never sees the template. No doc an agent reads lists the headings:
searched the copy's `AGENTS.md`, `command.md` and `knowledge/**/*.md` for `## Scope` and
`## Completed` in a handover context; `knowledge/operations/context-safety.md` does not name them.

The second attempt passed with no other change than renaming five headings, which shows the check
is about the shape and that the rest of the handover was fine.

## Proposed fix
State the shape where the handover is asked for:

1. In the 250k alert text (and the 300k one), name the five headings in one clause, e.g.
   `the handover uses ## Scope, ## Completed, ## Remaining, ## Constraints, ## Evidence`.
2. Add the same list to the handover paragraph of `knowledge/operations/context-safety.md`, next to
   the checkpoint command.
3. Keep `ensure_handover` from overwriting a file; instead, when a handover exists but lacks a
   heading, have `checkpoint` suggest the template path once (`tools/context/tests` already covers
   the refusal message; extend it to assert the heading list is printed).
