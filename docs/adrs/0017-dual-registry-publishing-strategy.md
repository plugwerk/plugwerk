# ADR-0017: Dual-Registry Publishing Strategy — GitHub Packages for SNAPSHOTs, Maven Central + Docker Hub for Releases

## Status

Accepted

## Context

The Plugwerk GitHub organization contains multiple repositories that depend on each other during
development (`plugwerk-spi`, `plugwerk-descriptor`, `plugwerk-api-model` are consumed by
`plugwerk-server`, `plugwerk-client-plugin`, and example projects). Before this ADR,
inter-repository development required running `./gradlew publishToMavenLocal` locally, which
breaks CI in dependent repos and makes cross-repo PRs difficult to test.

Issue [#230](https://github.com/plugwerk/plugwerk/issues/230) explored publishing SNAPSHOT
versions of Maven artifacts and development container images to GitHub's internal registries so
that dependent repositories can resolve development builds without manual local installs.

The existing release workflow ([#195](https://github.com/plugwerk/plugwerk/issues/195),
[#203](https://github.com/plugwerk/plugwerk/issues/203)) publishes to Maven Central and Docker
Hub exclusively. This must not change — those are the canonical distribution channels for
released artifacts.

## Decision

Adopt a **dual-registry strategy** with clear separation between development and release
artifacts:

### Maven Artifacts

| Stage | Registry | Trigger | Versions |
|---|---|---|---|
| **Development** | GitHub Packages (`maven.pkg.github.com/plugwerk/plugwerk`) | Push to `main` | `*-SNAPSHOT` only |
| **Release** | Maven Central **and** GitHub Packages | Git tag `v*` | Release versions only |

**Published modules** (both registries):

1. `plugwerk-spi` (JAR)
2. `plugwerk-descriptor` (JAR)
3. `plugwerk-api-model` (JAR)
4. `plugwerk-client-plugin` (JAR + PF4J ZIP)
5. `plugwerk-server` (distribution ZIP)

**Implementation:**

- The convention plugin `io.plugwerk.maven-publish` and the server backend's custom publication
  both declare a `GitHubPackages` Maven repository alongside the existing NMCP configuration.
- Credentials use `GITHUB_ACTOR` and `GITHUB_TOKEN` environment variables, which are
  automatically available in GitHub Actions. For local development, they fall back to empty
  strings (publishing to GitHub Packages is not intended locally).
- The SNAPSHOT workflow (`snapshot-publish.yml`) runs
  `publishAllPublicationsToGitHubPackagesRepository` — this Gradle task targets only the
  `GitHubPackages` repository and does not touch Maven Central.
- The release workflow (`release.yml`) runs `publishAggregationToCentralPortal` (Maven Central)
  followed by `publishAllPublicationsToGitHubPackagesRepository` (GitHub Packages). SNAPSHOTs
  are rejected by the existing tag guard before either task runs.
- GPG signing is conditional (`onlyIf { project.hasProperty("signingKey") }`). SNAPSHOT
  publishes to GitHub Packages skip signing. Release publishes sign all artifacts (the signed
  artifacts are then published to both registries).

### Container Images

| Stage | Registry | Tags | Trigger |
|---|---|---|---|
| **Development** | GHCR (`ghcr.io/plugwerk/plugwerk-server`) | `:snapshot` | Push to `main` |
| **Release** | Docker Hub **and** GHCR | `:<version>`, `:<major>.<minor>`, `:latest` | Git tag `v*` |

**Implementation:**

- The SNAPSHOT workflow builds the fat JAR and pushes a single `:snapshot` tag to GHCR. This
  tag is overwritten on every push to `main`.
- The release workflow logs into both Docker Hub and GHCR, then uses `docker/metadata-action`
  with both image names to generate tags for both registries in a single `build-push-action`
  step.
- Per-PR images (`pr-<number>`) are deferred to Phase 3 — the complexity of cleanup policies
  and storage costs does not justify the benefit for the current single-team development model.

### Authentication Model

| Context | Mechanism | Scope |
|---|---|---|
| GitHub Actions (same org) | `GITHUB_TOKEN` (automatic) | `read:packages` + `write:packages` |
| GitHub Actions (other org) | `GITHUB_TOKEN` of consuming repo | `read:packages` |
| Local development | Personal Access Token (PAT) with `read:packages` | Read-only |

GitHub Packages requires authentication even for reading public packages within the same
organization. This is a known limitation and the primary trade-off of this approach.

### Consuming SNAPSHOT Artifacts

Dependent repositories add the GitHub Packages Maven repository to their Gradle configuration:

```kotlin
repositories {
    mavenCentral()
    maven {
        name = "GitHubPackages"
        url = uri("https://maven.pkg.github.com/plugwerk/plugwerk")
        credentials {
            username = providers.environmentVariable("GITHUB_ACTOR").getOrElse("")
            password = providers.environmentVariable("GITHUB_TOKEN").getOrElse("")
        }
    }
}
```

For local development, developers configure a PAT in `~/.gradle/gradle.properties`:

```properties
gpr.user=<github-username>
gpr.key=<personal-access-token-with-read:packages>
```

And reference it in the repository block:

```kotlin
credentials {
    username = project.findProperty("gpr.user")?.toString()
        ?: providers.environmentVariable("GITHUB_ACTOR").getOrElse("")
    password = project.findProperty("gpr.key")?.toString()
        ?: providers.environmentVariable("GITHUB_TOKEN").getOrElse("")
}
```

## Consequences

### Positive

- **Cross-repo CI works without local publishes.** Dependent repositories resolve SNAPSHOT
  artifacts from GitHub Packages automatically.
- **Release artifacts are mirrored on GitHub Packages.** Org-internal consumers can use a
  single repository configuration for both SNAPSHOT and release resolution.
- **Container images are available on GHCR for internal testing** without polluting Docker Hub
  with unreleased tags.
- **No changes to the existing release workflow semantics.** Maven Central and Docker Hub remain
  the canonical release channels. GitHub Packages and GHCR are additive.
- **No new secrets required.** `GITHUB_TOKEN` is automatically available in GitHub Actions.
- **Version is already SNAPSHOT on `main`.** The `VERSION` file contains `*-SNAPSHOT` between
  releases, so `./gradlew publish` on `main` naturally produces SNAPSHOT artifacts.

### Negative / Trade-offs

- **GitHub Packages requires authentication for reads.** Even public packages in the same org
  require a valid token. Developers must configure a PAT for local SNAPSHOT resolution. CI
  within the org uses the automatic `GITHUB_TOKEN`.
- **Additional CI time.** The SNAPSHOT workflow adds ~3-5 minutes to every push to `main`. The
  release workflow adds ~1 minute for the GitHub Packages publish step.
- **Storage costs.** GitHub Packages has a free tier (500 MB for free plans, more for paid).
  SNAPSHOT artifacts are overwritten on each publish (Maven SNAPSHOT semantics), so storage
  grows slowly. GHCR `:snapshot` tag is a single overwritten image.
- **Per-PR images deferred.** Cross-repo PR testing requires checking out the PR branch and
  building locally, or waiting for the PR to merge to `main` and the SNAPSHOT to publish.

## Alternatives Considered

- **Gradle composite builds / dependency substitution:** Works for local development but does
  not help CI in other repositories. Also requires all repos to be checked out side-by-side.
- **JitPack:** Third-party service, adds an external dependency, limited control over
  publishing, no container image support.
- **`publishToMavenLocal` in CI:** Possible within a single workflow but does not persist
  across repositories or workflow runs.
- **Only Maven Central for everything:** Maven Central does not accept SNAPSHOT versions (by
  design). The OSSRH staging repository could host SNAPSHOTs, but the Gradle NMCP plugin does
  not support it, and the Sonatype OSSRH is being deprecated in favor of the Central Portal.
