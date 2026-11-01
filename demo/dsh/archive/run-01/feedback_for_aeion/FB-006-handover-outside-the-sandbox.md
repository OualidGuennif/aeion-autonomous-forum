# FB-006: the 250k closure needs a handover file the dsh sandbox refuses to write

- **Severity:** high (the run cannot pass the closure gate without the owner editing the parent
  repository by hand)
- **Found in:** round 04 → 05
- **Status:** documented c3f586f (launch at the root, or the driver writes closure files)

## Evidence

The dsh plugin resolves the **parent** repository as its root: it walks up from the session folder
`demo/` until it finds `tools/agents/hook.py`. The handover therefore belongs to
`work/handovers/dsh-<session>.md` in the parent, which is outside the session workspace `demo/`:

```text
$ pwd
/Users/wallseven/Desktop/aeion-factory/demo
$ # write work/handovers/dsh-5dfb0e25….md
[sandbox: file access denied under workspace-write mode]
```

The file policy for the session is `workspace-write` with workspace
`/Users/wallseven/Desktop/aeion-factory/demo`, and the closure step is a normal tool call, so it fails
closed. Raising the one-shot `danger-full-access` retry found no approval channel in headless mode and
the request failed instead of asking. The checkpoint then reported:

```text
{"state": "HANDOVER_REQUIRED", "observed_tokens": 288774,
 "checkpoint": {"handover": "f27b0b9d…", "tree": "3e87dc7c…"}}
```

The owner had to write the handover and run `budget.py checkpoint` from the parent. The session then
sat in `HANDOVER_REQUIRED` across several turns: the brief says "stop only when round 07 is done, or
if you are truly blocked", but the closure rule forbids continuing without a verified handover, and
the only way to satisfy it was outside the sandbox.

## Proposed fix

Pick one and state it in the context-safety doc:

1. **Root the plugin at the folder that holds the session's `AGENTS.md`** (or at the `--patch` path),
   not at the first ancestor with `tools/agents/hook.py`. A factory copy under `demo/` would then own
   its `work/`, its handovers and its checkpoint state, all inside the workspace.
2. **Put the handover inside the session workspace** when the factory root is above it, for example
   `<workspace>/work/handovers/<session>.md`, and let `budget.py` read it there.
3. **Give the closure step its own narrow allowance** for the handover path only, so the general
   `workspace-write` policy still holds for everything else.
4. In headless mode, **auto-approve that single handover write** and log it, as the brief already
   assumes the sandbox asks.

Whichever is chosen, the rule "write the handover, verify the checkpoint, then compact" should be
satisfiable without the owner editing a repository the agent was told not to touch.
