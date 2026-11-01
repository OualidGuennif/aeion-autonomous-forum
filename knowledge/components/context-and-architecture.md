---
type: Reference
title: "Æion Factory integration choices"
description: "The narrow upstream practices retained for context safety, skills and mandatory architecture maintenance."
status: stable
generated:
  by: aeion-factory/1.1
  at: 2026-10-25
sources:
  - id: context
    resource: ../operations/context-safety.md
    title: "Actual context contract"
  - id: architecture
    resource: factory-architecture.md
    title: "Complete architecture specification"
  - id: maintenance
    resource: ../operations/architecture-maintenance.md
    title: "Actual analysis contract"
---

# Integration choices

The approved plan preserves Doc / OKF / Knowledge, frontmatter/tags, Æion Factory skill entry points,
existing hooks and the reviewed task flow. Current behavior belongs to the linked runbooks and
architecture specification; this file records the integration choices rather than a second policy.

## Context-mode

[Inspected source](https://github.com/mksglu/context-mode/tree/8b74cbba68a79e45170dd5866dee949fd6ddd8e1).
Retain bounded output, processing large results in code, file artifacts and handover restoration.
No upstream code/MCP/memory store is installed. Its best-effort snapshot is not a deterministic
250k/300k token guard. Æion Factory uses native compaction at 250k, explicit closure and a verified
checkpoint; the 300k forced compaction is not qualified pre-request admission. Strict execution
refuses instead of claiming an unsupported guarantee.

## Superpowers

[Inspected source](https://github.com/obra/superpowers/tree/2ce8aa15d05bd57213e6f746f953aafb7ded7a8b).
Add focused debugging; strengthen the existing plan, test, sign-off and review procedures.
Reproduction, one falsifiable hypothesis, meaningful regression evidence and fresh verification
add value. Existing build/spec/review/ship behavior already covers much of the rest. Do not add
the global bootstrap, mandatory implementer/reviewer rounds or a second task ledger. Worktrees
and additional agents remain justified by actual independent work, not enabled by default.
Adapted material retains its MIT attribution in CREDITS and the accompanying notice.

## Graphify and Archify

[Graphify 0.9.97](https://github.com/Graphify-Labs/graphify/tree/0a8cd4e1e342235fe53e881fb5d591bf0460071f) and
[Archify v3.2.0](https://github.com/tt-a1i/archify/tree/208acba8d412335185b2056fedf54b3405df43fb).
CI analyses every main update; locally they are strongly recommended and installed only on
the user's yes. Use native structural code/Markdown
extraction, including frontmatter, headings and links, instead of making required CI depend on
LLM credentials or quotas. Full small-corpus rebuilds avoid stale deletion/rename state and
shared-cache races; optimize only after measured cost warrants it. This replaces the proposed
semantic-model/incremental baseline with a deterministic structural baseline, explicitly labelled.

Keep a complete reviewed architecture spec in Knowledge. Source-backed Archify views explain
responsibilities, delivery and context recovery; automatic validation does not prove their
meaning. Outputs are immutable per SHA, hashed and kept outside tracked source. No graph
inference writes canonical policy and no generated artifact automatically commits to main.

No new database, service, scheduler, provider router or agent orchestration layer is introduced.
