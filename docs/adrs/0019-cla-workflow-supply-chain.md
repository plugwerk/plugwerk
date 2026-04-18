# ADR-0019: Supply-chain hardening for the CLA workflow

## Status

Accepted

## Context

The 1.0.0-beta.1 security audit (tracker issue [#255]) flagged `.github/workflows/cla.yml` as the repository's single highest-risk workflow under finding H-10 / SEC-048. The file combined three dangerous properties in one place:

1. **`on: pull_request_target`** — the trigger runs in the base-repo context with access to repo secrets, even for PRs originating from forks. This is the only trigger available to the CLA Assistant action, because fork PRs need the base-repo token to comment on the PR and commit signatures to the `cla-signatures` branch.
2. **Broad write permissions** — `actions: write`, `contents: write`, `pull-requests: write`, and `statuses: write` were granted at the workflow level, so every job in the file (including any future one) inherited them.
3. **Floating-tag action reference** — `contributor-assistant/github-action@v2.6.1` could be retagged by the action's maintainers (or by an attacker who compromised the maintainers) to point at a malicious commit. That commit would execute in our CI with the `GITHUB_TOKEN` and the `CLA_TOKEN` personal access token.

A retag-based supply-chain attack is the realistic worst case here. The classic `pull_request_target` exploit vector — checking out fork-controlled code and executing it with secrets in scope — does not apply to the current workflow because it does not run `actions/checkout` at all. But "does not apply today" is a brittle guarantee that one future edit could remove.

The repository-wide SHA-pinning effort for all seven workflows is tracked separately under finding H-09 / [#295]. This ADR covers the CLA workflow in isolation because it has the highest risk profile and warrants a dedicated record of the decision.

## Decision

Apply three layered hardenings to `.github/workflows/cla.yml`:

### 1. Pin the action by full commit SHA

Replace:

```yaml
uses: contributor-assistant/github-action@v2.6.1
```

with:

```yaml
uses: contributor-assistant/github-action@ca4a40a7d1004f18d9960b404b97e5f30a505a08 # v2.6.1
```

The SHA was resolved on 2026-04-18 via `gh api repos/contributor-assistant/github-action/git/ref/tags/v2.6.1` and verified as a commit (not a tag) object via `gh api repos/.../commits/<sha>`. Updates to the action release new tags that move independently; the pinned SHA is immutable.

### 2. Move permissions to job scope; declare workflow-level `permissions: {}`

Workflow-level permissions are dropped to the empty set. The CLA job keeps the permissions that the action's README (read at the pinned SHA) documents as required:

- `actions: write`
- `contents: write` (required because the signature file lives on the in-repo `cla-signatures` branch)
- `pull-requests: write` (comment posting)
- `statuses: write`

Any future job added to this workflow file starts from `{}` and must opt in to what it actually needs. A future maintainer cannot accidentally inherit the CLA job's write access.

### 3. Do not narrow below the action's documented requirements

All four permissions listed above are required by the action at the pinned SHA. The audit's initial recommendation to drop `actions: write` and `statuses: write` was rejected after the action's README was verified at the pinned SHA: the action's CLI path requires both. If a future action version removes one or more of these requirements, the permissions block can be narrowed — but only after confirming the new version's docs and running the full CLA flow end-to-end.

### 4. Document the threat model inline and commit to not checking out PR code

A prominent comment block at the top of `cla.yml` records the threat model, forbids adding `actions/checkout` to this workflow, and points at this ADR. Code reviewers are the enforcement mechanism: any PR that adds checkout to this file must be blocked pending re-evaluation of the threat model.

### 5. Maintenance via Renovate, not Dependabot

`.github/renovate.json` (already configured) includes a `matchManagers: ["github-actions"]` rule with the group `"GitHub Actions"`. Renovate detects new SHA pins on every scheduled run and opens grouped update PRs weekly. This replaces the more common Dependabot story for action pinning. Do not add `.github/dependabot.yml`.

## Consequences

### Positive

- A malicious retag of `contributor-assistant/github-action` no longer auto-executes in our CI.
- If the CLA job is ever compromised, its blast radius is limited to the four documented permissions — not inherited by future jobs that may be added.
- Renovate surfaces action updates as reviewable PRs on a weekly cadence, so the SHA pin does not become stale.
- The inline `actions/checkout` prohibition makes the primary `pull_request_target` exploit class (fork-controlled code execution with secrets) impossible by convention.

### Negative

- Each action release now requires a manual review of the Renovate PR before merging. This is the intended trade-off — updates to a `pull_request_target` workflow's third-party action warrant deliberate review.
- Narrowing permissions below the documented set is not possible today. If the action's future versions require even more permissions, we will re-evaluate whether to stay on this action or switch to an alternative.

### Scope

This ADR is limited to `.github/workflows/cla.yml`. The six other workflows in `.github/workflows/` still reference actions by floating tag — that repository-wide pinning is tracked under [#295] / H-09 and will reference this ADR for its pattern. Secret rotation (`CLA_TOKEN`, `GITHUB_TOKEN`) is out of scope unless evidence of exposure emerges.

[#255]: https://github.com/plugwerk/plugwerk/issues/255
[#295]: https://github.com/plugwerk/plugwerk/issues/295
