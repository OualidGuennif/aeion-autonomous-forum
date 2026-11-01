# FB-002 — `cat` is refused in any command that also contains a `cd`, even with an absolute path

- Severity: low (usability; a workaround exists)
- Date: 2026-10-28
- Harness: OpenCode, demo run rounds 01, 04, 07

## What happened

Three refusals in this run, all from `guard-bash.py` (bounded intake):

1. `cd app && cat > deno.json <<'EOF' …` — a heredoc dump. Correct refusal, wrong tool anyway
   (the write tool was the right answer).
2. `cd app && (deno task start … & echo $! > /tmp/ib.pid); … kill $(cat /tmp/ib.pid)` — refused
   with "cannot size cat after a cd (the path depends on the new directory)". The path is
   **absolute** (`/tmp/ib.pid`), so it does not depend on the new directory, and the `cat` output is
   captured into `$(…)` — nothing is printed.
3. `cd demo && … | cat opencode/render/graphify/hubs.txt` — same rule, a 14-line file already
   written by the same command.

A minimal probe (kept): from any directory,
`cd /tmp && cat /Users/wallseven/Desktop/aeion-factory/demo/opencode/workspace/VERSION` is refused
today, while `cd /tmp && head -2 <the same absolute path>` passes and prints the version.

## Evidence

- The refusals are quoted in `demo/opencode/journal/01-scaffold.md`,
  `04-votes.md` and `07-architecture.md` (record blocks).
- The probe above was run during this feedback round; the message returned is
  "bounded intake: cannot size cat after a cd (the path depends on the new directory)".
- Workarounds used: absolute path with `head`, a shell variable instead of the pid file, and the
  `write` tool instead of heredocs.

## Proposed fix

1. Only report "the path depends on the new directory" when the `cat` argument is relative; an
   absolute path is sizeable in any directory, so evaluate it normally.
2. Allow `cat` whose stdout is captured (`$(cat …)`, `x=$(cat …)`) — nothing is dumped to the
   transcript — or name that case in the refusal with the working form.
3. Keep the current message's advice ("use head -n 80 <path>") only when it actually applies.

## Status

Applied c3f586f.
