# ADR-0014: Dual-License Library Modules under Apache-2.0

## Status

Accepted

## Context

Plugwerk was originally licensed entirely under AGPL-3.0. While AGPL is appropriate for the
server component (protecting against proprietary forks of the self-hosted server), it creates
a fundamental adoption barrier for the reusable library modules.

AGPL's copyleft clause requires any software that links against an AGPL library to also be
released under AGPL — effectively forcing host applications to open-source their entire
codebase. No enterprise will embed a plugin SDK (plugwerk-spi, plugwerk-client-plugin) under
these terms, as it would require them to publish their proprietary application source code.

This is the standard problem with copyleft licenses on libraries intended for third-party
embedding, and it has a well-established industry solution.

## Decision

Adopt a **split-licensing model**: the server remains AGPL-3.0, all client-facing library
modules are relicensed to Apache-2.0.

| Module | License | Rationale |
|--------|---------|-----------|
| `plugwerk-server` (backend + frontend) | AGPL-3.0 | Protects the server; self-hosters must contribute back |
| `plugwerk-spi` | Apache-2.0 | ExtensionPoint interfaces used by every host application |
| `plugwerk-descriptor` | Apache-2.0 | MANIFEST.MF parser used by build tools and CI/CD |
| `plugwerk-client-plugin` | Apache-2.0 | Client SDK embedded in host applications as PF4J plugin |
| `plugwerk-api-model` | Apache-2.0 | Generated DTOs for REST API consumers |

**Precedent:** GitLab (server AGPL, client libs MIT), Grafana (server AGPL, SDKs Apache-2.0),
MongoDB (server SSPL, drivers Apache-2.0).

**Why Apache-2.0 over MIT?** Apache-2.0 includes an explicit patent grant, which provides
stronger legal protection for both the project and its users. It is the standard choice for
Java/JVM ecosystem libraries.

## Consequences

### Positive

- Host applications can embed `plugwerk-spi` and `plugwerk-client-plugin` in proprietary
  software without any copyleft obligation.
- Enterprise adoption is unblocked — the licensing model matches industry expectations.
- Maven Central publication (issue #195) can proceed with correct license metadata.
- The server remains protected by AGPL-3.0 — self-hosters must still contribute modifications
  back.

### Negative

- Two license headers must be maintained (AGPL for server, Apache for libraries). Spotless
  is configured per-module to enforce the correct header automatically.
- Contributors must understand which license applies to which module. This is documented in
  `AGENTS.md` and in per-module `LICENSE` files.

### Implementation

- `LICENSE` (AGPL-3.0) remains at the project root for the server.
- `LICENSE-APACHE-2.0` added at the project root with the full Apache-2.0 text.
- Each library module has its own `LICENSE` file pointing to Apache-2.0.
- `license-header-apache.txt` contains the short Apache-2.0 header for source files.
- Spotless is configured to apply the correct header per module automatically.
