# FB-003 — Archify examples command writes into its installation

- Severity: low; status: documented c3f586f; round: 07, 2026-10-28.
- Observed: `node <archify>/bin/archify.mjs examples` regenerates example HTML inside the installed
  tool checkout. It is not a read-only listing. The sandbox refused the first temporary output
  there with EPERM. The tool checkout remained clean (`git status --short`, no changes).
- Workaround: inspect the pinned schema/example JSON files directly, then render only under
  demo/codex/render. No escalation or installation change was attempted.
- Proposed fix: document this side effect in the Factory's Archify runbook and direct agents to
  fixture files for read-only exploration. Upstream could expose a separate listing command.
- Verification: confirm the runbook names output locations; keep installation status unchanged
  during demo authoring. No base-factory change made here.
