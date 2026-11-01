# FB-001: the `work/current.md` entry shape is enforced but documented nowhere

- **Severity:** low (one retry; the check lists the missing lines, not the shape they belong to)
- **Found in:** round 01
- **Status:** applied c3f586f

## Evidence
I wrote a new task entry in the copy's `work/current.md` in the shape the file itself suggests
(heading, state, next, blocker) and `tools/knowledge/knowledge-check` refused it:

```text
FAIL work/current.md: '## Idea Board rounds — ACTIVE' lacks '- Owner decision:'
FAIL work/current.md: '## Idea Board rounds — ACTIVE' lacks '- Evidence:'
RESULT: FAIL (2 problem(s))
```

`tools/knowledge/knowledge-check` enforces five fields, one per line:

```python
WORK_FIELDS = ("- Owner decision:", "- State:", "- Next:", "- Blocker:", "- Evidence:")
```

No file an agent reads states that list. Searched the copy: `AGENTS.md`, `command.md`,
`knowledge/**/*.md` and `.agents/skills/**` contain no mention of `Owner decision` or `Blocker:`.
The nearest statements are `work/current.md`'s own header ("Task state only … Status: ACTIVE, …")
and `AGENTS.md`'s "How a task flows", which never names the fields.

## Proposed fix
Document the shape where the file's purpose is defined, and have the check say it:

1. Add the five fields and their meaning to `knowledge/operations/` (a short "Task state" section,
   linked from `AGENTS.md`'s task-flow bullet), and point at it from `work/current.md`'s header.
2. Make the failure self-teaching: after the missing-field lines, print
   `an entry has: - Owner decision: / - State: / - Next: / - Blocker: / - Evidence:`.
3. Extend `tools/knowledge/tests/` with a case that checks an entry missing a field reports both the
   field and the shape hint.
