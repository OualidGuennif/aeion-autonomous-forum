# FB-006: the parent's `doc-links` resolves a nested project's `Spec:` pointers against the parent

- **Severity:** high (blocked committing the demo)
- **Found in:** round 01
- **Status:** applied in `2c2fb1d` (base, `develop`)

## Evidence
The copy's app files carry `Spec: knowledge/components/idea-board-api.md`, which is correct inside the copy. The parent repository reads every tracked file's header against its own root:

```text
RULE L1 demo/claude_code/workspace/app/api/app.ts:1 Spec: knowledge/components/idea-board-api.md does not exist
… 5 files …
RESULT: FAIL (5)
```

The copy's own `tools/*` files only passed by coincidence: the same path exists in the parent.

## Fix applied
- `tools/knowledge/doc-links` finds nested projects: folders with their own `AGENTS.md` and `knowledge/index.md`. It skips their files in L1 and in the L3 base coverage.
- A folder with `knowledge/` but no `AGENTS.md` is still checked.
- Three tests were added to `tools/knowledge/tests/test_doc_links.py`. Two of them failed before the fix (`41 passed, 2 failed`); all pass after it (`43 passed`).
- The rule is documented in `knowledge/policies/specs-first.md`.
