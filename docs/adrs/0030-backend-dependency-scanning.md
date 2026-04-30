# ADR-0030: Backend dependency scanning via CycloneDX SBOM and `trivy sbom`

## Status

Accepted

## Context

Issue [#297](https://github.com/plugwerk/plugwerk/issues/297) (M-3 calibration finding from the 1.0.0-beta.1 security audit) surfaced a coverage gap in the pre-merge security gate.

`.github/workflows/security.yml` runs `trivy fs` against the repository root with `skip-dirs: build,.gradle,…`. With those directories excluded — and they must be excluded; CI does not check generated build artefacts into the repo — Trivy sees no backend dependency manifest. It does not understand `build.gradle.kts` or the version catalog (`gradle/libs.versions.toml`), so the Kotlin runtime classpath is invisible to the scanner. Frontend coverage works only because `package-lock.json` is a checked-in lockfile that Trivy can parse natively.

This is a calibration gap, not an active vulnerability. Phase 3 of the audit manually verified the runtime classpath (Tomcat 11.0.20, Jackson 3.1.0, logback 1.5.32) clean against published CVE feeds. The risk is preventive: the scanner currently cannot catch a regression introduced by a future dependency bump, even though Renovate already manages those bumps.

Renovate (configured in `.github/renovate.json`) handles dependency *update* PRs across all ecosystems. The gap is orthogonal — Renovate does not perform CVE matching; it only proposes version bumps.

## Decision

Generate a CycloneDX SBOM for the backend's `runtimeClasspath` during CI and scan it with `trivy sbom` in a dedicated, parallel workflow job.

Concretely:

1. **Plugin**: `org.cyclonedx.bom` v3.2.4 (Gradle Plugin Portal coordinate, declared in `gradle/libs.versions.toml` so Renovate's existing `gradle` manager keeps it current).
2. **Scope**: Only `runtimeClasspath` — test and compile-only dependencies are not shipped in the production artefact and would only add false-positive noise.
3. **Output**: JSON-only SBOM at `plugwerk-server/plugwerk-server-backend/build/reports/bom.json` (XML output disabled via `xmlOutput.unsetConvention()`).
4. **CI job**: New `backend-sbom-scan` job in `.github/workflows/security.yml`, parallel to the existing `trivy` job. Architecture:
   - One Trivy invocation in `format: json` produces the canonical findings file (`trivy-backend-sbom.json`).
   - One Trivy invocation in `format: table` produces the human-readable log/Step-Summary surface.
   - One shell step (`jq`) reads the JSON, counts findings per severity, and applies the gate policy explicitly:
     - **CRITICAL** → emit `::error::` annotation, exit 1, fail the build.
     - **HIGH** → emit `::warning::` annotation, do not fail.
     - **MEDIUM** → counted and reported in the Step Summary, no annotation, no failure.
   - One Trivy invocation in `format: sarif` (CRITICAL+HIGH band) feeds the GitHub Code Scanning tab under category `trivy-backend-sbom`.
5. **Why the gate is jq-based, not trivy-action `exit-code`**: `trivy-action` does not apply its `severity` filter to SARIF output (Code Scanning needs all severity levels for its own filtering UI). As a side effect, an `exit-code: '1'` paired with `severity: CRITICAL` fires on *any* finding in the SARIF, regardless of severity. Routing the gate through JSON + jq makes the policy deterministic and reviewable: severity buckets are explicit shell variables, not a property of the scanner's output mode.
6. **Smoke test**: Before scanning, a bash step asserts that `tomcat-embed-core`, `jackson-databind`, `logback-classic`, and `spring-boot-starter-web` are present in the BOM. If a future change silently degrades the SBOM scope, the build fails loudly instead of producing an empty-but-passing scan.
7. **Findings visibility**: Three surfaces — collapsible log group with the full Trivy table, GitHub Step Summary with the same table, and the Security tab. Operators do not need to download the artefact to triage.
8. **Artefact retention**: 30 days, parity with the existing `trivy-sarif-report` artefact. Bundle includes the BOM, the table, the JSON gate input, and the SARIF.

## Considered Alternatives

### Option B — Dependabot for the Gradle ecosystem

Rejected. The repository already uses Renovate (`.github/renovate.json`) with carefully tuned package rules: grouped Spring Boot, Kotlin, Jackson, OkHttp, JUnit/Mockito/Testcontainers, and GitHub Actions PRs. Enabling Dependabot in parallel would produce competing update PRs against the same dependencies, cause merge conflicts, and force every Renovate group to fight a Dependabot equivalent.

Beyond the Renovate conflict, Dependabot is not a pre-merge gate. Its alerts surface in the Security tab asynchronously — the issue's acceptance criterion "fail the workflow on CRITICAL vulns in the runtime classpath" cannot be satisfied by Dependabot alone.

### Option C — OWASP Dependency-Check Gradle plugin

Rejected. Three reasons:

1. **Performance**: `dependencyCheckAggregate` requires a full NVD database pull on first run and incremental updates afterwards. Without an `NVD_API_KEY` secret, NIST rate-limits the API and CI runs frequently take 5–10 minutes or fail outright. With the key, a new repository secret must be provisioned and rotated.
2. **False-positive rate**: Dependency-Check matches by CPE heuristically; Spring and Jackson regularly produce false matches that require an `dependency-check-suppressions.xml` file to keep the build green. Trivy matches by PURL extracted directly from the CycloneDX BOM, which is exact.
3. **Toolchain divergence**: The frontend scan already uses Trivy. Adopting Trivy for the backend keeps one scanner, one SARIF-upload pattern, one artefact convention, and one set of action SHAs to manage in the CI.

## Consequences

### Easier

- Pre-merge CVE gate covers the backend runtime classpath, closing the audit calibration gap.
- Same scanner family (Trivy) for frontend and backend simplifies CI maintenance and ADR-0026 SHA-pinning hygiene.
- The SBOM artefact is downloadable from every CI run — useful for ad-hoc supply-chain queries, license audits, and customer-facing SBOM disclosure if requested.
- Renovate continues to drive dependency updates, including the CycloneDX plugin itself, with no configuration change.

### More difficult

- The SBOM is regenerated on every CI run rather than checked in. Local reviewers cannot read the BOM offline without either running `./gradlew :plugwerk-server:plugwerk-server-backend:cyclonedxDirectBom` themselves or downloading the latest CI artefact.
- Workflow time grows by an additional parallel job. Expected runtime: ~2 minutes (JDK setup, Gradle dependency resolution, three Trivy scans, three uploads). Acceptable on top of the existing ~3-minute frontend scan.
- HIGH findings without an upstream fix may accumulate in the Security tab. If volume becomes blocking, follow the rollback ladder below.

## Rollback ladder

If the SBOM scan produces unmanageable false-positive volume in practice:

1. **Suppress targeted findings**: Add `.trivyignore` at the repo root with `CVE-XXXX-YYYY` entries, each annotated with reason and review date (max 90 days). Extend the `trivy-action` step with `trivyignores: .trivyignore`.
2. **Severity downshift**: Move the `CRITICAL → fail` step to `exit-code: '0'` (warn-only), making the entire backend scan informational. Document on the relevant PR.
3. **Disable the job**: Set `if: false` on `backend-sbom-scan`, mark this ADR `Superseded by ADR-NNNN`, and re-open issue #297 with a postmortem. The frontend `trivy fs` job continues unaffected.

## References

- Issue: [#297](https://github.com/plugwerk/plugwerk/issues/297)
- Replaces: [#256](https://github.com/plugwerk/plugwerk/issues/256) (closed not-planned, framing was incorrect)
- Audit report: `docs/audits/1.0.0-beta.1-security-audit.md` → "Calibration notes"
- Related ADRs: [ADR-0026](0026-sha-pin-actions-and-base-images.md) for action SHA-pinning rules that this job follows
- Plugin: [CycloneDX/cyclonedx-gradle-plugin](https://github.com/CycloneDX/cyclonedx-gradle-plugin) v3.2.4
- Trivy SBOM scan mode: <https://aquasecurity.github.io/trivy/latest/docs/target/sbom/>
