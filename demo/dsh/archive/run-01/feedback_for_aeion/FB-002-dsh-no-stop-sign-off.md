# FB-002: dsh never sends Stop, so the sign-off hook never runs

- **Severity:** medium (the sign-off is a reminder, not a guard, and a second stop always passes; but
  every other adapter runs it and `AGENTS.md` promises it)
- **Found in:** round 00
- **Status:** applied c3f586f

## Evidence

The plugin registers a guard, one post-execute listener and one pre-step listener, and nothing else:

```js
// .dsh/plugins/factory/index.js:70,81,94
ctx.tools.guard(...)                       // PreToolUse
ctx.on('tools/post-execute', ...)          // PostToolUse
ctx.on('agent/pre-step', ...)              // PreCompact
```

The test asserts exactly those handlers:

```js
// tools/agents/tests/test_dsh.mjs:42
assert.deepEqual(Object.keys(handlers).sort(), ['agent/pre-step', 'tools/post-execute']);
```

`tools/agents/hook.py:386-388` has the `Stop` branch that calls `sign-off.py`, and the battery shows
`sign-off.py` answering, but nothing in a dsh session ever sends that event. The Pi run saw the same
symptom, but Pi has no stop event to forward; dsh does. Its plugin API documents
`agent/turn-stopping` as a serial event awaited before the turn closes:

```text
name: 'agent/turn-stopping'
mode: 'serial'
signature: 'agent/turn-stopping'(this: Scoped<Agent>, payload: { agent: Agent; turn: number; signal: AbortSignal }): Promise<void> | void
summary: 'The turn is about to close: the model owes no response (no live tool calls, no fresh steering).'
```

`knowledge/operations/agent-compatibility.md:87-96` lists SessionStart, PreToolUse, PostToolUse and
PreCompact for dsh and never mentions Stop, so the omission is invisible to a reader.

## Proposed fix

Forward the turn close to the existing Stop branch:

1. Register `ctx.on('agent/turn-stopping', ...)`, call `invoke(root, { session_id, hook_event_name:
   'Stop' })`, and when it returns a reason, `agent.steer({ content: [{ type: 'text', text: reason }],
   source: { kind: 'aeion-factory' } })` so the turn runs another step. `sign-off.py` shows each
   finding once per session and a second stop passes, so this cannot loop.
2. Add a `test_dsh.mjs` case: a Stop reason steers the agent, and no reason leaves the turn to close.
3. Name the event in `knowledge/operations/agent-compatibility.md`'s dsh paragraph, next to
   `agent/pre-step`.
