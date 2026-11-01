# FB-002 — `tools/architecture/refresh` analyses the parent repository from a nested plain folder

- **Severity:** medium (wrong evidence possible, misleading message)
- **Found in:** round 00
- **Status:** applied aeda5e0

## Evidence
Run from `demo/claude_code/workspace` (no `.git`, nested in this repository):

```text
$ tools/architecture/refresh --target HEAD
Architecture refresh: FAILED (run tools/architecture/setup first)
```

- `refresh()` calls `revision(root, "HEAD")` first. Git walks up to the parent's `.git` and returns the **parent's** commit.
- The run only fails later, because the copy has no `.aeion/integrations`.
- With a toolchain present, it would `git clone --shared` the parent and analyse a tree that is not the copy.

Every other git-aware tool (`doc-links`, `repo-health`, `budget.py`, the hooks) calls `project_files.git_active()`, which treats a nested folder as a plain folder. `refresh` is the only one that does not.

## Proposed fix
- At the top of `refresh()`, call `git_active(root)` first.
- When it is false, fail closed with: `needs its own git repository; in a plain folder run tools/architecture/graph.py and archify render directly (knowledge/operations/architecture-maintenance.md)`.
- Add a test in `tools/architecture/tests/test_refresh.py`: a plain folder nested in a repository is refused before any git call.
- Add a "Plain folder" paragraph to the architecture maintenance doc with the two direct commands that round 07 proves.
