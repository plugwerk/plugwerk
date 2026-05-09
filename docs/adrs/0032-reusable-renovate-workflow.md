# ADR-0032: Reusable Renovate workflow at the org level

## Status

Accepted

## Context

After the `plugwerk/.github` org-config bootstrap shipped (issue #398,
ADR follow-up not required), every plugwerk repo extends the
org-wide Renovate `default.json` for its package rules. That covered
the *configuration* side of Renovate. The *triggering* side was still
inconsistent:

- `plugwerk/plugwerk` had a 76-line self-hosted Renovate workflow
  (`.github/workflows/renovate.yml`) introduced in PR #462 to work
  around opaque cadence on Mend-Hosted Renovate (#376 sat with a
  ticked dashboard checkbox for hours with no observable bot run).
- `plugwerk/examples` and `plugwerk/website` had no self-hosted
  trigger at all and relied entirely on Mend-Hosted, with the same
  opacity risk that motivated the original `plugwerk/plugwerk` move.

Two paths were available:

1. Copy the 76-line workflow into `examples` and `website` verbatim.
   Solves the trigger problem but creates three places to update on
   every `renovatebot/github-action` SHA bump (and we already burned
   on this exact pattern with PR #470, the hotfix to PR #462).
2. Move the workflow logic into a reusable workflow in
   `plugwerk/.github` and have each repo call it via a thin stub.

We chose option 2.

## Decision

The org-config repo (`plugwerk/.github`) hosts the canonical Renovate
workflow as a reusable workflow:

```text
plugwerk/.github/.github/workflows/renovate.yml   # on: workflow_call
```

Each consumer repo carries a small stub (~25 lines) that defines its
own schedule, permissions, and inputs, and delegates the run to the
reusable workflow:

```yaml
jobs:
  renovate:
    uses: plugwerk/.github/.github/workflows/renovate.yml@main
    permissions:
      contents: write
      pull-requests: write
      issues: write
    with:
      logLevel: ${{ inputs.logLevel || 'info' }}
```

Token model stays single-repo. `${{ secrets.GITHUB_TOKEN }}` in a
`workflow_call` resolves to the **caller's** repo-scoped token, so
the reusable workflow does not — and structurally cannot — write
across repos. No PAT, no GitHub App.

Concurrency in the reusable workflow keys on `${{ github.repository }}`,
so two runs against the same repo serialize while different repos run
in parallel without contention.

The SHA pins for `actions/checkout` and `renovatebot/github-action`
live in the reusable workflow only. Bumping a pin is a single PR in
`plugwerk/.github`; the change reaches every consumer immediately
without touching their code (because the stub references `@main`).
This trades some surprise-risk (an unreviewed bump can affect three
repos at once) against the maintenance burden of three parallel pin
updates. Mitigation: the `renovate-config-validator` workflow in
`plugwerk/.github` blocks malformed config; for action pin bumps the
PR review in `plugwerk/.github` is the gate.

## Consequences

### Easier

- One place to update the `renovatebot/github-action` SHA pin — no
  more PR #470-style hotfix scenarios where one repo gets a
  hallucinated version while others lag.
- Adding self-hosted Renovate to a new plugwerk org repo is a
  ~25-line stub, not a 76-line copy.
- `examples` and `website` get the same observable Actions-tab cadence
  that `plugwerk/plugwerk` already has, ending Mend-opacity risk
  uniformly.

### Harder

- A bug or breaking change in the reusable workflow can affect three
  repos in one merge. Mitigation: the workflow is small, the change
  surface is narrow, and dispatching a manual `gh workflow run` in one
  repo before merging across-the-board catches obvious regressions.
- Caller stubs reference `@main`, not a pinned commit SHA. This is
  intentional — pinning would defeat the central-update story — but
  means a mistake on `plugwerk/.github`'s `main` ships immediately.
  Revert is a single commit on the reusable side; recovery is fast.

### Unchanged

- Mend-Hosted Renovate continues to run in parallel against all repos
  (it is org-installed). This workflow is an additional, observable
  trigger, not a replacement.
- The `.github/renovate.json` per-repo config is still where each
  repo declares its own `extends` and `packageRules` overrides.
- Schedule (`Mon-Fri 04:00 UTC`) and the in-config schedule filter
  (`before 6am every weekday`) are unchanged.

## References

- ADR-0026: SHA-pin actions and base images (still applies; the
  reusable workflow follows the policy).
- Issue #398: org-config bootstrap (closed in `plugwerk/.github` PR #1).
- PR #462 / #470: original self-hosted Renovate workflow and the
  hotfix that motivated the central-update story.
