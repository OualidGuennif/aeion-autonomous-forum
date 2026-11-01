# FB-001: dsh drops the SessionStart context, so the checkpoint rule never reaches the model

- **Severity:** medium (the 250k/300k rule, the handover path and the checkpoint key are absent from
  the agent's first turn; recovery still works when the first refusal names them)
- **Found in:** round 00
- **Status:** applied c3f586f

## Evidence

The plugin calls `tools/agents/hook.py dsh` with `SessionStart` on the first guarded tool call, then
keeps only `reason`:

```js
// .dsh/plugins/factory/index.js:51-58
function start(session) {
  if (started.has(session)) return;
  const source = compacted.has(session) ? 'compact' : 'startup';
  const { reason } = invoke(root, { session_id: session, hook_event_name: 'SessionStart', source });
  if (reason) throw new Error(reason);
  started.add(session);
  compacted.delete(session);
}
```

`invoke` returns the whole hook output (line 30), but `start()` throws the rest away. The hook does
return the context the other adapters deliver. Running the copy's adapter with a SessionStart event:

```text
$ printf '%s\n' '{"session_id":"demo-dsh-fb-001","hook_event_name":"SessionStart","source":"startup"}' \
    | python3 tools/agents/hook.py dsh
{"hookSpecificOutput": {"hookEventName": "SessionStart", "additionalContext": "Æion Factory dsh checkpoint profile. Handover (created at the first refused compaction): work/handovers/dsh-fb47….md. Checkpoint key: dsh-fb47…. 250k handover/compact, 300k forced handover and compaction; not a strict ceiling.\nBounded intake: …\ngit: inactive (plain folder); …\nwork/current.md: 1 open task(s) …"}}
```

In this session the state file was written (`.aeion/context/dsh-e5596e….json`:
`{"bootstrapped": true, "epoch": 0, "observed_tokens": 1000, "profile": "dsh", "state": "RUNNING"}`),
so the hook ran; no such text ever appeared in the conversation. The test suite codifies the two
handlers and never checks the start context:

```js
// tools/agents/tests/test_dsh.mjs:42
assert.deepEqual(Object.keys(handlers).sort(), ['agent/pre-step', 'tools/post-execute']);
```

`knowledge/operations/agent-compatibility.md:87-90` describes the guard, PostToolUse and PreCompact
for dsh but never says the SessionStart context is dropped, so a reader assumes it arrives.

## Proposed fix

Inject the context the hook already returns, or state that dsh cannot and stop implying it does:

1. In `start()`, keep `output.hookSpecificOutput.additionalContext` and deliver it on the first
   guarded call, e.g. `exec.agent.steer({ content: [{ type: 'text', text: context }], source: { kind:
   'aeion-factory' } })` (the plugin already uses `steer` after compaction, index.js:105), or a prompt
   section if dsh exposes one.
2. Add a `test_dsh.mjs` case that a SessionStart context reaches the agent, not just that the state
   file is written.
3. If dsh has no injection point at start, say so in `knowledge/operations/agent-compatibility.md` and
   in `demo/dsh/README.md`, and keep the refusal text as the only carrier of the checkpoint key.
