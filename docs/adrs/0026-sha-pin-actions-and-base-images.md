# ADR-0026: SHA-pin GitHub Actions and Docker base images

## Status

Accepted

## Context

The 1.0.0-beta.1 security audit (tracker [#255]) flagged four findings around supply-chain pinning in the build/release pipeline:

- **SEC-046** — every `uses: owner/action@vX` in CI workflows references a floating mutable tag. A malicious retag on any referenced action executes in our CI with access to whatever secrets the job holds.
- **SEC-047** — the release pipeline (`release.yml`) calls third-party actions from `peter-evans/*` and `docker/*` by floating tag. The release job holds Maven Central, Docker Hub, GHCR, and GitHub release tokens; a compromised action there has the highest blast radius in the repo.
- **SEC-049** — `aquasecurity/trivy-action` in `security.yml` was pinned only to the minor release (`v0.35.0`), which is still a mutable tag — anyone with push access to that repo can retarget it.
- **SEC-042** — `Dockerfile` and `plugwerk-server/plugwerk-server-backend/src/dist/Dockerfile` use `eclipse-temurin:21-j{re,dk}-alpine` as a floating Docker tag. A compromised image registry can ship a malicious build to users through the release pipeline.

ADR-0019 / SEC-048 already established the SHA-pinning pattern on the CLA workflow: `uses: contributor-assistant/github-action@<full-40-char-sha> # v2.6.1`. The action reference and the human-readable tag are kept together — the SHA is what Git resolves, the comment is what humans review — and Renovate updates both atomically.

This ADR extends that pattern to every workflow, both Dockerfiles, and formalises the Renovate rules that keep the pins current.

## Decision

### Syntax

Every GitHub Actions reference in `.github/workflows/*.yml` uses:

```yaml
uses: owner/action@<40-lowercase-hex-sha> # <release-tag>
```

- The SHA is the commit SHA the release tag points to at pin time.
- The trailing comment is the **exact** release tag (`# v4.8.0`, not `# v4`).
- When a repo uses a subpath action (`github/codeql-action/upload-sarif`), the SHA is still the parent repo's commit SHA — the subpath is an import path, not a separate ref.
- Trivy stays at `v0.35.0` in this PR; bumping the version is a follow-up decision for Renovate, not bundled with the pinning work.

Every `FROM` line in a Dockerfile uses:

```dockerfile
FROM <image>:<tag>@sha256:<64-hex-manifest-list-digest>
```

- The tag stays visible for human readers; the `@sha256:` digest is the immutable pin.
- The digest must be a **manifest-list digest** (returned by `docker buildx imagetools inspect <image>:<tag> --format '{{ .Manifest.Digest }}'`). Single-arch digests break the multi-arch build (`linux/amd64,linux/arm64`) which is exercised by both `release.yml` and `snapshot-publish.yml`.

### Scope

All six unpinned workflows get pinned in this PR: `ci.yml`, `e2e.yml`, `prepare-release.yml`, `release.yml`, `security.yml`, `snapshot-publish.yml`. `cla.yml` was already pinned under ADR-0019 and is untouched. Both Dockerfiles get pinned — the shipped one under `plugwerk-server/plugwerk-server-backend/src/dist/Dockerfile` (SEC-042 scope) **and** the repo-root `Dockerfile` used for developer builds (out-of-scope of SEC-042 but cheap symmetric fix; a floating tag in a dev Dockerfile is still a Trivy finding waiting to happen).

### Renovate, not Dependabot

The audit recommendation named **Dependabot** with a grouped `github-actions` ecosystem. The project uses **Renovate** exclusively (`.github/renovate.json`) — adding Dependabot would create two tools fighting over the same dependency updates. Renovate already covers GitHub Actions and Dockerfile managers out of the box; we extend its config to:

- Extend with `helpers:pinGitHubActionDigests` so every action reference is automatically enforced to carry a SHA.
- Enable `pinDigests: true` on the existing "GitHub Actions" package rule and a new "Docker base images" rule (`matchManagers: ["dockerfile"]`).
- Split the GitHub Actions group into a grouped minor/patch/digest PR and a **separate** major-update PR (mirroring the existing `npm-major` split at `renovate.json`). A major version of an action can carry a breaking change and deserves individual review; bundling it into a weekly grouped PR masks the blast radius.

This fulfils audit criterion #5 ("add Dependabot github-actions ecosystem with grouped updates") via the equivalent Renovate mechanism, with the same properties: grouped, scheduled, reviewable PRs; SHA-level updates; major bumps separated.

## Consequences

### Good

- Supply-chain surface shrinks to "what Renovate ingests on Monday morning" instead of "every retag in the upstream action repo".
- The `# vX.Y.Z` trailing comment makes every workflow diff human-readable without needing to resolve the SHA.
- Renovate updates the SHA and the trailing comment atomically; comment/SHA drift is not possible unless a human edits one by hand.
- The default-deny wiring in ADR-0025's actuator chain plus this ADR's action pinning cover the two audit-critical pathways in the build pipeline.

### Breaking

None. SHAs resolve to the same commits the floating tags currently point to; semantics are identical. First Renovate PR after merge may be large (catches up every action that has had a minor release), which is expected.

### Watch

- **Transitive actions.** An action that we pin may itself call a nested action by floating tag; those calls are outside our control. Accepted residual risk — the third-party actions we actually call (`peter-evans/*`, `docker/*`, `aquasecurity/trivy-action`) are widely used enough that a breach would be loud.
- **Stale pins.** Renovate runs weekly. If it stalls (credential expiry, rate limit, schedule override), digests age and security fixes do not land automatically. Mitigation: Dependency Dashboard is enabled (`dependencyDashboard: true`); maintainers review it during the Phase 2 release cadence.
- **Major-version drift.** Because we pin per-major, the current pinned majors (v3/v4/v5/v6 depending on action) will age relative to upstream. Renovate's separate major-update PRs surface each breaking bump for explicit review; do not bypass them.
- **`aquasecurity/trivy-action` upstream is several minors ahead of v0.35.0.** Bumping it is out of scope here — the ticket only asked for the SHA at v0.35.0. Renovate's next major/minor PR offers the bump on its own merits.

## References

- Audit tracker [#255]; rows SEC-042, SEC-046, SEC-047, SEC-049 in `docs/audits/1.0.0-beta.1-artifacts/triage-SEC.csv`.
- Precedent: ADR-0019 (CLA workflow SHA pinning, SEC-048).
- [OpenSSF guidance on pinning GitHub Actions by SHA](https://github.com/ossf/scorecard/blob/main/docs/checks.md#pinned-dependencies).
- Touched files:
  - `.github/workflows/ci.yml`
  - `.github/workflows/e2e.yml`
  - `.github/workflows/prepare-release.yml`
  - `.github/workflows/release.yml`
  - `.github/workflows/security.yml`
  - `.github/workflows/snapshot-publish.yml`
  - `.github/renovate.json`
  - `Dockerfile`
  - `plugwerk-server/plugwerk-server-backend/src/dist/Dockerfile`

[#255]: https://github.com/plugwerk/plugwerk/issues/255
