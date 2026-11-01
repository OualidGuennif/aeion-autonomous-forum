# FB-003: headless dsh cannot commit from `demo/`, and the brief says the sandbox asks

- **Severity:** medium (it blocks the round protocol's commit every round, and the workaround is not
  in any file the agent reads)
- **Found in:** round 00
- **Status:** applied in the run 2 brief (demo/plan/, demo/start/)

## Evidence

The brief says a sandbox that writes only below its folder "asks before a `git commit`"
(`demo/plan/00-start.md:83`). In this headless dsh session the sandbox is `workspace-write` for
`demo/`, and `.git` is one level above it. Staging the round's records was denied outright:

```text
$ git add demo/dsh/README.md demo/dsh/journal/00-setup.md demo/dsh/feedback_for_aeion/*.md
fatal: Unable to create '/Users/wallseven/Desktop/aeion-factory/.git/index.lock': Operation not permitted
[sandbox: file access denied under workspace-write mode]
[sandbox: escalation available — retry this exact command once with sandbox_permissions ...]
```

The retry with the wider mode failed closed because headless dsh has no answerer:

```text
Error: sandbox escalation to "danger-full-access" requires approval, but no approval channel is available
```

The runtime context states the policy: "Operations that require approval may ask through the
configured answerers; without an available answerer, the request fails closed." So the prompt the
brief promises cannot happen in the mode its own `demo/dsh/README.md` documents for one task per call.
The owner has to run the commit (or approve it in a web session), which is human help the record block
should count.

## Proposed fix

Say where the commit can happen, per dsh mode, and keep the run reproducible:

1. In `demo/plan/00-start.md`, replace "asks before a `git commit`" with the real rule: a sandbox that
   writes only below `demo/` **refuses** `.git` writes, and headless has no approval channel, so the
   owner commits (or the session runs with a wider sandbox / in web with an answerer).
2. In `demo/dsh/README.md`, add the exact commit command and say that `workspace/` and `run.jsonl`
   stay out of it.
3. Keep the fallback in the round protocol: when the sandbox refuses, the agent reports the staged
   paths and the command, and the round records one human help, never a bypass.
