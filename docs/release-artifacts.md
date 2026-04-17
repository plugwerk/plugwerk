# Release artifacts reference

A map of every file consumed or produced by the Plugwerk release pipeline.
Nothing in this document tells the build anything — it is a human-facing
overview that answers "what participates in a release, and where does it live?"

> **Keep this file in sync** with `.github/workflows/prepare-release.yml`,
> `.github/workflows/release.yml`, and `.github/workflows/snapshot-publish.yml`.
> If you change any of those, update the tables below in the same PR.

## Pipeline overview

| Stage | Workflow | Trigger |
|---|---|---|
| Prepare | `.github/workflows/prepare-release.yml` | `workflow_dispatch` — bumps `VERSION`, tags, pushes |
| Release | `.github/workflows/release.yml` | Push of `v*` tag |
| Snapshot | `.github/workflows/snapshot-publish.yml` | Push to `main` when `VERSION` ends with `-SNAPSHOT` |

## Release workflow

Runs on `v*` tag push. Publishes libraries, the server image, and supporting
assets across four external registries plus the website.

### Files consumed

| Path | Role |
|---|---|
| `VERSION` | Release version source of truth (extracted from tag, cross-checked) |
| `plugwerk-api/src/main/resources/openapi/plugwerk-api.yaml` | OpenAPI spec — drives code generation for library JARs, also transported to `plugwerk/website` via `openapi-update` dispatch |
| `buildSrc/src/main/kotlin/io.plugwerk.maven-publish.gradle.kts` | Shared Maven publication convention (POM, signing, GitHub Packages repo) applied to library modules |
| `plugwerk-server/plugwerk-server-backend/build.gradle.kts` | Defines the `serverDist` Maven publication + `serverDistZip` distribution bundle |
| `plugwerk-server/plugwerk-server-backend/src/dist/Dockerfile` | Multi-arch image build context for Docker Hub + GHCR |
| `plugwerk-server/plugwerk-server-backend/src/dist/docker-compose.yml` | Bundled into the distribution ZIP (via `serverDistZip`); also transported to `plugwerk/website` via `docker-compose-update` dispatch |
| `plugwerk-server/plugwerk-server-backend/src/dist/start.sh` | Bundled into the distribution ZIP (via `serverDistZip`) |
| `plugwerk-server/plugwerk-server-backend/src/dist/start.bat` | Bundled into the distribution ZIP (via `serverDistZip`) |
| `docker/dockerhub-readme.md` | Docker Hub repository description, synced via `peter-evans/dockerhub-description` |

### Gradle tasks invoked

- `./gradlew publishAggregationToCentralPortal -Pversion=<v>` — NMCP aggregates all Maven publications and uploads them to Maven Central
- `./gradlew publishAllPublicationsToGitHubPackagesRepository -Pversion=<v>` — mirrors the same publications to GitHub Packages (per ADR-0017)

### Artifacts produced

| Artifact | Destination |
|---|---|
| Library JARs (`plugwerk-spi`, `plugwerk-descriptor`, `plugwerk-api-model`, `plugwerk-client-plugin`) | Maven Central + GitHub Packages |
| `plugwerk-client-plugin-<v>.zip` (PF4J bundle) | Maven Central + GitHub Packages + GitHub Release |
| `plugwerk-server-backend-<v>.jar` (fat JAR, intermediate) | Workflow artifact — consumed by the `docker-publish` job |
| `plugwerk-server-<v>.zip` (distribution bundle) | GitHub Release |
| `plugwerk/plugwerk-server:<v>`, `:<major>.<minor>`, `:latest` | Docker Hub (multi-arch: `linux/amd64`, `linux/arm64`) |
| `ghcr.io/plugwerk/plugwerk-server:<v>`, `:<major>.<minor>`, `:latest` | GitHub Container Registry (same tags as Docker Hub) |
| Docker Hub repo description | From `docker/dockerhub-readme.md` |

### Downstream dispatches

| Event type | Target repo | Triggered workflow |
|---|---|---|
| `openapi-update` | `plugwerk/website` | `sync-openapi.yml` — opens PR syncing `public/api-docs/openapi.yaml` |
| `docker-compose-update` | `plugwerk/website` | `sync-docker-compose.yml` — opens PR syncing `public/deploy/docker-compose.yml` |

## Snapshot workflow

Runs on every push to `main`. Publishes intermediate `*-SNAPSHOT` artifacts
for cross-repo development (per ADR-0017). Skipped automatically when
`VERSION` does not end with `-SNAPSHOT` (e.g. during the tag-creation commit).

### Files consumed

| Path | Role |
|---|---|
| `VERSION` | Gate — step is skipped unless it ends with `-SNAPSHOT` |
| `buildSrc/src/main/kotlin/io.plugwerk.maven-publish.gradle.kts` | Same publication convention as the release |
| `plugwerk-server/plugwerk-server-backend/src/dist/Dockerfile` | Image build context |
| `plugwerk-server/plugwerk-server-frontend/package-lock.json` | Node cache key |

### Gradle tasks invoked

- `./gradlew build -x integrationTest` — produces all publications
- `./gradlew publishAllPublicationsToGitHubPackagesRepository`
- `./gradlew :plugwerk-server:plugwerk-server-backend:bootJar -x test` — fat JAR for the SNAPSHOT image

### Artifacts produced

| Artifact | Destination |
|---|---|
| `*-SNAPSHOT` library JARs (same module set as release) | GitHub Packages only (ADR-0017) |
| `ghcr.io/plugwerk/plugwerk-server:snapshot` | GitHub Container Registry (overwritten on each push) |

## Prepare-release workflow

Runs manually via `workflow_dispatch`. Does not publish anything — its only
job is to bump versions, create a tag, and let the `release` workflow take over.

### Files consumed

| Path | Role |
|---|---|
| `VERSION` | Validated to currently end with `-SNAPSHOT`, then overwritten twice |

### Effects

1. `VERSION` → release version (commit "release: prepare \<v>")
2. Git tag `v<release-version>` created
3. `VERSION` → next development version ending in `-SNAPSHOT` (commit "chore: prepare next development cycle \<v>")
4. Waits for the triggered `release.yml` run to finish
5. `gh release create v<release-version> --generate-notes`

## Dual-use files (not release-pipeline)

These exist at the repo root for Docker/developer ergonomics. They are
**not** referenced by any of the release workflows above.

| Path | Purpose |
|---|---|
| `Dockerfile` (root) | Local `docker compose build` + CI smoke test |
| `docker-compose.yml` (root) | Local dev stack + CI smoke test (`ci.yml`) |

The canonical deployer-facing compose file is
`plugwerk-server/plugwerk-server-backend/src/dist/docker-compose.yml` —
that one ships in the distribution ZIP and is synced to
[plugwerk/website](https://github.com/plugwerk/website).

## Related ADRs

- [ADR-0014](adrs/0014-dual-license-library-modules.md) — Dual license (Apache-2.0 libraries, AGPL-3.0 server) drives the separate license headers applied by Spotless
- [ADR-0017](adrs/0017-dual-registry-publishing-strategy.md) — Dual-registry publishing (Maven Central + GitHub Packages, Docker Hub + GHCR)
