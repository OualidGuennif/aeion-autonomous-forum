# FB-008: the sandbox blocks `~/.docker`, so the brief's own `docker compose up` fails

- **Severity:** medium (round 06's first acceptance command cannot run as written in headless dsh)
- **Found in:** round 06
- **Status:** documented c3f586f

## Evidence

The brief says `cd app && docker compose up -d --build`. The Docker CLI writes its buildx activity
file under the user's home, which the `workspace-write` sandbox refuses:

```text
$ docker compose up -d --build
 Image idea-board:local Building
failed to update builder last activity time: open
/Users/wallseven/.docker/buildx/activity/.tmp-default3323240220: operation not permitted
```

The obvious workaround, a fresh `DOCKER_CONFIG`, loses the CLI plugins, because `docker compose` and
`docker buildx` live in `~/.docker/cli-plugins`:

```text
$ DOCKER_CONFIG=/tmp/dsh-docker docker compose version
docker: unknown command: docker compose
```

What worked was a writable config directory with symlinks to the real plugins:

```bash
mkdir -p /tmp/dsh-docker/cli-plugins
ln -s /Applications/Docker.app/Contents/Resources/cli-plugins/docker-buildx /tmp/dsh-docker/cli-plugins/
ln -s /Applications/Docker.app/Contents/Resources/cli-plugins/docker-compose /tmp/dsh-docker/cli-plugins/
export DOCKER_CONFIG=/tmp/dsh-docker
```

Every later `docker compose` command in the round used that prefix, so the journal's commands are not
the brief's commands verbatim.

## Proposed fix

1. **Document the relocation** in the context-safety or agent-compatibility doc: when the session is
   sandboxed to a workspace, set `DOCKER_CONFIG` to a directory inside it and link the CLI plugins.
2. Or **allow `~/.docker/buildx` for docker commands** in the sandbox profile (a narrow exception:
   the CLI's own scratch state, not credentials).
3. Or, in the demo's round 06 brief, **state the sandboxed form of the command** and keep the plain
   form for an unsandboxed shell.

Whichever is chosen, a round whose acceptance is a container command should be runnable without the
agent inventing a config directory first.
