# Sensitive change review — round 06

Scope: app Dockerfile, Compose file, build context exclusions, startup listener and KV paths.

- Native `deno audit`: exit 0, "No known vulnerabilities found" (retained output).
- Docker installs with --frozen; an isolated dependency drift build failed specifically because
  the lockfile was out of date. The real dependency files were unchanged.
- Runtime uses USER deno; actual effective UID 1993. A writable named data volume is initialized
  with that ownership. Runtime copies built output and dependency/cache files, not the source tree.
- Build context exclusions cover local data, dependency/build output and secret environment files.
  No secret was opened, logged or copied by this run.
- Health uses Deno, checks HTTP success, and passed 5.47 seconds after restart. Volume readback
  proves persistence. Test container/network/volume were removed only within aeion-codex-demo.
- Static review found no credential literal, shell evaluation of request data, external image
  push, privileged container, host filesystem mount or Docker socket mount.

Limitations: this is local native-tool verification and a separate manual review pass. No
claude-security plugin, remote CI security suite, image-CVE scanner or independent human review
ran. Deno permissions remain broad (-A) for this local playground; no authentication is claimed.
The version tag is exact but not digest-pinned; the tested image ID is in container-acceptance.log.
