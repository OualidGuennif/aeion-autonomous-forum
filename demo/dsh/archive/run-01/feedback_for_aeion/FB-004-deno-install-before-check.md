# FB-004: the scaffold's acceptance commands fail until `deno install`

- **Severity:** low (one extra command; the error names it, but the brief's own checklist does not)
- **Found in:** round 01
- **Status:** applied in the run 2 brief (demo/plan/, demo/start/)

## Evidence

Round 01's acceptance block is `deno fmt --check && deno lint && deno check`, then `deno test -A` and
`deno task build`. On a fresh `app/` with Fresh 2 and Vite, `deno check` failed before any code ran:

```text
$ cd app && deno check
error: Could not find a matching package for 'npm:@opentelemetry/api@^1.9.0' in the node_modules directory.
Ensure you have all your JSR and npm dependencies listed in your deno.json or package.json, then run `deno install`.
Alternatively, turn on auto-install by specifying `"nodeModulesDir": "auto"` in your deno.json file.
```

`deno test -A` had already passed (it fetched what the tests import), so the failure is specific to
the Fresh/Vite dependency graph and the `"nodeModulesDir": "manual"` setting that keeps installs
explicit. Running `deno install` once fixed `deno check`, `deno task build` and `deno task start`.
The brief never mentions `deno install`, so a harness following it literally stops on a red check
that is not a code defect.

## Proposed fix

Make the first scaffold step explicit and keep the lock file meaningful:

1. Add `cd app && deno install` as the first line of round 01's acceptance block (and the project's
   `AGENTS.md` build/test block), before `deno check`.
2. Say why: `nodeModulesDir: "manual"` keeps npm resolution reproducible and requires the install;
   `deno.lock` is generated there.
3. Alternatively, use `"nodeModulesDir": "auto"` in the scaffold and drop the extra step, but then
   the lock file is less clearly the source of truth; pick one and state it in the round.
