---
type: Component
title: "Forum request validation"
description: "Bounded JSON shape validation before authentication and endpoint side effects."
status: draft
sources:
  - resource: ../../demo/forum-app/src/main/java/local/aeion/forum/RequestValidationFilter.java
    title: "Early entity validation"
  - resource: ../../demo/forum-app/src/test/java/local/aeion/forum/RequestValidationTest.java
    title: "HTTP regressions"
---

# Forum request validation

## Intent

Implement feature 017 in the canonical product. Reject invalid request shape before handlers
can create accounts or mutate threads, without exposing supplied values.

## Data sources

The existing register/login and thread resources define the permitted root string fields.
The application already has a global 16 KiB HTTP body cap; the filter also reads at most cap+1
bytes so validation itself cannot consume an unbounded entity stream.
The filter runs after routing at priority AUTHENTICATION-1, before the existing CSRF filter and
resource-owned authentication. Quarkus pre-matching filters run on the event loop, so this bounded
stream reader is a normal request filter ([Quarkus REST guide](https://quarkus.io/guides/rest/)).

## Inputs

Incoming request method, normalized route, media type and entity stream. Register/login require
exactly username/password; thread creation requires exactly title/body. The limit applies to
all methods/routes with a body. Unsupported media types retain the framework's existing refusal.

## Outputs

Oversized bodies return 413. Invalid JSON, non-object roots, duplicate keys and trailing JSON
tokens return 422 with error=invalid_request and fields=[body]. Unknown/missing/non-string
fields return 422 with sorted unique field paths only. Valid bytes are restored for the handler.

## Invariants

1. The same route normalization is used for every rule lookup (RequestValidationTest).
2. No handler side effect occurs on shape refusal (external RequestValidationContractTest).
3. A body is bounded before JSON parsing, authentication and CSRF (bodyLimitIncludesTheBoundary).
4. Valid-shaped business refusals, sessions, CSRF and response headers retain their meaning
   (existing AuthResourceTest/ThreadsResourceTest and validationRefusalsKeepSecurityHeaders).
5. Invalid values and parser exception text never appear in refusal bodies
   (allRoutesRejectUnknownOrWrongTypeFieldsWithoutEchoingValues).

## Determinism

Static route rules, a fixed byte ceiling and sorted unique field paths. No model, clock, network
call, database query, new dependency or provider configuration participates in validation.

## Acceptance checks

Run Gradle test in the existing qualified denied-network Docker boundary, with an ephemeral
test database. Run the supervisor-owned external 017 contract in addition to the product tests.
The new regressions must fail on the pre-feature product and pass with the filter.

## Out of scope

Business password/title rules, session policy changes, schema migrations, new endpoints,
automatic publication and implementing other catalog features. The owner explicitly requested
direct Codex repair on 2026-11-02 after three generated candidates failed; this replaces the
earlier delegation-only preference for this scoped repair.
