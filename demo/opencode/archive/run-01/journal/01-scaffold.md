# Round 01 — Scaffold

Plan (skill `plan`): one spec first (`knowledge/components/idea-board-api.md`), then a thin Fresh 2
scaffold in `app/` — deno.json, vite.config, main/utils/client, `_app` and index routes, one API
catch-all — plus the plain Hono module and `version.ts`. Acceptance tests written before the code
and seen red. Not done: storage, idea routes, pages with data, Docker.

Versions, verified against JSR/npm metadata and resolved by `deno install` into `deno.lock`:
Fresh `@fresh/core` 2.3.3 (JSR latest), `@fresh/plugin-vite` 1.1.2 (JSR latest, declares
`vite ^7.1.4`), Vite 7.3.7 (latest 7.x; 8.3.5 is out of Fresh's range), Preact 10.29.8 (latest in
Fresh's `^10.29.1`; 11.1.0 exceeds it), Hono 4.13.14 (JSR latest stable), `@std/assert` 1.0.19.
Generated `app/.gitignore` and `app/deno.lock` from the official `@fresh/init` 2.3.3 template.

What happened:
- Spec written and linked from the components index; every covered file carries its `Spec:` line.
- Red first: `deno test -A` failed with "Cannot find module .../api/app.ts" (evidence/01-red.txt).
- Built in slices; three surprises worth recording:
  1. `define.handlers({ ALL() {} })` does not type-check in Fresh 2: a by-method handler only
     registers its declared methods. Read `@fresh/core`'s `app.ts` from the Deno cache: a plain
     function handler registers every method — that is the catch-all shape.
  2. Empty `interface` fails `deno lint` (`no-empty-interface`); `ApiDeps`/`State` are
     `Record<string, never>` until a round gives them members.
  3. The `deno serve`/types resolution rejects `npm:vite@=7.3.7` subpaths (`vite/client`); ranges
     plus `"lock": true` pin the exact versions instead — same idea as the official template.
- Vite's dev server defaults to 5173; set `server: { port: Number(Deno.env.get("PORT") ?? 8000) }`
  in `vite.config.ts` so dev and start listen on the same port.
- Quality: `deno fmt --check`, `deno lint`, `deno check`, `deno test -A` (3 passed) — green
  (evidence/01-quality.txt). `deno task build` (evidence/01-build.txt), `deno task start` smoke:
  health body, JSON 404, JSON 405, home 200 with title Idea Board (evidence/01-smoke.txt);
  `deno task dev` smoke on 8000 (evidence/01-dev.txt).
- Factory checks inside the copy: PASS (evidence/01-factory.txt). CHANGELOG Unreleased line added;
  work/current.md entry added.

Result:          done
Tests:           3 passed, 0 failed (deno test -A); build PASS; start and dev smokes PASS
Factory checks:  knowledge-check, doc-links, repo-health: PASS
Guard refusals:  3, all correct and passed without weakening: 1 secret probe (round 00); 1 heredoc
                 `cat > file` after cd — bounded intake wants the write tool, not a shell dump;
                 1 `cat`-of-a-pidfile after cd — kept the pid in a shell variable instead
Human help:      0 (all rounds pre-authorized)
Feedback:        none new for the factory itself; Fresh 2 API-shape findings recorded above
Elapsed:         25 minutes
