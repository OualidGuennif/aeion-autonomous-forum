# FB-005: the plain-folder walk counts build output that `.gitignore` excludes

- **Severity:** medium (a normal `deno task build` turns `repo-health` red)
- **Found in:** round 01
- **Status:** applied 1c9b82c

## Evidence
Without git, `project_files()` walks the disk. That walk skips a fixed list: `node_modules`, `.venv`, and so on, plus `dist`, `build` and others at the root only. It does not read `.gitignore`.

Fresh writes its build to `app/_fresh/`, which `app/.gitignore` excludes. After `deno task build`:

```text
FAIL app/_fresh/server/server-entry.mjs: over 1,500 lines; split it along a real seam
FAIL app/_fresh/server/assets/_fresh-route___api_path_-….mjs: a comment block over 20 lines
```

Under git, the same files would be ignored. So the plain folder and the repository disagree about what the project is.

**Workaround in this run:** `rm -rf app/_fresh` before the checks.

## Proposed fix
In the disk walk, honour each folder's `.gitignore` for simple entries. That means a name, or a name with a trailing `/`, no negation, applied to that folder and below. Unknown pattern forms keep the file listed, which fails closed. The rule needs a test in `.claude/hooks/test_hooks.py`, or wherever `project_files` is tested. Adding `_fresh` to the skip list would only fix this one framework.
