# FB-001 — Archify evidence links cannot be used in a plain folder

- **Severity:** low (diagrams still render, without source links)
- **Found in:** planning, confirmed in round 07
- **Status:** open (an upstream Archify constraint; the factory can only document it)

## Evidence
A diagram whose components carry `sources` must declare `meta.repository`. Archify validates it like this:
- `revision` must match `^[a-fA-F0-9]{40}$` (a git commit);
- `url` must be a credential-free HTTP(S) or Git SSH address (`file://…` is refused).

Without `meta.repository`, Archify refuses the diagram with `Repository evidence requires /meta/repository`. If the `sources` are removed, `archify render` works and needs no git.

## Proposed fix
- In `knowledge/operations/architecture-maintenance.md`, add a "Plain folder" note. Diagrams there carry no `sources`. Their evidence is the reviewed JSON next to the HTML. They gain links once the folder becomes a repository.
- Do not fake a revision: a made-up 40-character hash would be fabricated evidence.
