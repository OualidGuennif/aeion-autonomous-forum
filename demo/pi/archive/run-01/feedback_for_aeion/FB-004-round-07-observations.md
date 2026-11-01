# FB-004: round 07 observations, and one gap from round 00

- **Severity:** low (each item costs one retry or one wasted generator run, none blocks a round)
- **Found in:** round 07, plus one from round 00 kept for this file
- **Status:** applied (Stop and L4 in c3f586f; brief, gallery and ignored HTML in the run 2 brief)

## 1. Pi never runs `sign-off.py` (round 00)
The Pi extension registers `SessionStart`, `PreToolUse`, `PostToolUse` and `PreCompact`; Pi has no
`Stop` event, so the hook's `Stop` branch — the one that runs `sign-off.py` — is unreachable. This
is the only behavioural difference from the Claude Code run: every other guard, the bounded intake,
the 250k closure, the checkpoint and the reset after compaction behaved identically.

Proposed fix: map Pi's end-of-turn (`agent_end` / `turn_end`) to a `Stop` payload in
`.pi/extensions/factory/index.js`, or state in `knowledge/operations/agent-compatibility.md` that Pi
skips sign-off and the agent must run the checklist itself.

## 2. The round-07 brief asks for three Archify commands where one exists
The brief lists `validate`, `render` and `browser-check` as three acceptance commands. `finalize`
does all three (validation, render, browser check with a receipt) and is what a run should call;
calling `validate` separately is only useful for a fast loop on one JSON.

Proposed fix: make `finalize` the primary command in the brief, keep `validate` as the quick gate,
and say that `refresh` refuses a plain folder (already in the brief) so per-diagram `finalize` is
the expected path.

## 3. Archify geometry needs a stated iteration budget
Seven `finalize` runs failed here before the last diagram passed, always with a precise diagnostic
and a `supportedFixes` list — excellent, but the brief reads as if one pass suffices. The failures
clustered in four places worth naming in the brief or the `graph` skill: segment edges that run
along a message (4 px clearance), a note that wraps into the next message, workflow columns
(`col`/`toCol` ≤ lanes − 1) and phases that may not share a boundary column, the
`deployment-ownership` profile (a security-group boundary, a region per component, an `owner` tag
on stateful components, and boundary labels that must stay inside the viewBox), and dataflow
corridors where two `vertical-channel` flows merge.

Proposed fix: one paragraph — "expect two to four `finalize` rounds; apply `supportedFixes` and rerun
once per change; do not rewrite the JSON".

## 4. Rendered HTML should be untracked by default
The first version of this round committed 14 Archify pages (11 MB) alongside their JSON. They are
generated from the JSON plus a pinned Archify version, and a diff of 800 KB of HTML is unreadable.
The run now keeps them out with `render/.gitignore` (`*.html`, `diagrams/*.html`) and keeps
`build_gallery.py` plus the JSON as the source.

Proposed fix: say it in the brief — commit the diagram JSON and the builder, ignore the HTML — and
have the demo README's folder table mention the build command.

## 5. `doc-links` could catch what `ask files` already finds
`ask files knowledge/components/idea-board-api.md` reported
`app/vite.config.ts [Spec: line; missing from the spec's sources (gap)]`: a file names its spec, the
spec's `sources` omits it. Nothing fails when a source is missing, so the gap survived three rounds
until a graph query found it by accident. `tools/knowledge/doc-links` checks links that exist, not
files whose `Spec:` header points at a document that does not list them.

Proposed fix: add that one-way check to `doc-links` (or call `ask files` for every component spec in
the `okf-update`/`sign-off` steps), and let `ask files` keep printing the `(gap)` marker.
