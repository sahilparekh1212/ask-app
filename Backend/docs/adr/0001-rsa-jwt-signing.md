# ADR-0001: RSA (asymmetric) JWT signing over HMAC

**Status:** Accepted

## Context

Auth issues JWTs (`TokenService`/`JwtConfig`); Audit — and any future resource server — must
verify them without being able to *mint* them. Spring Security's OAuth2 resource-server support
covers both signing families equally easily, so the choice is really about key distribution.

## Decision

Sign with RSA (`JwtConfig` generates/loads a 2048-bit RSA keypair) and publish the public key at
`/.well-known/jwks.json`. Audit — and every other resource server — points
`spring.security.oauth2.resourceserver.jwt.jwk-set-uri` at that endpoint
(`application-DEV/SIT/UAT/PROD.properties`) and verifies signatures with the public key alone.

## Alternatives considered

- **HMAC (symmetric, e.g. HS256).** Simpler — one shared secret, no key-pair machinery. Rejected
  because *every* verifying service would need the same secret Auth uses to *sign*: an Audit
  instance holding it could forge tokens as any user, not just verify them. That's an unnecessary
  trust expansion for a multi-service system where verifiers should never gain minting power.
- **RSA with a static/committed keypair.** Simplest to run, but a key baked into the repo is a
  standing secret-scanning and rotation liability. Rejected in favor of `AUTH_RSA_PRIVATE_KEY`
  (env-injected PEM in DEV+) with an ephemeral fallback for zero-config local dev.

## Consequences

- Verifying services need no secret at all — just an HTTP call to a public endpoint, which is
  also what makes horizontal scaling of resource servers trivial (no secret to distribute).
- Auth itself is *not* yet horizontally safe: with no `AUTH_RSA_PRIVATE_KEY` set, each pod
  generates its own ephemeral key, so tokens signed by one replica fail JWKS verification against
  another. This is a known, tracked gap (see `TODO.md`, "Auth refresh-token store is in-memory
  only" / "Make services stateless") — RSA doesn't solve multi-replica signing consistency by
  itself, it only removes the *distribution* problem once a stable key is supplied.
