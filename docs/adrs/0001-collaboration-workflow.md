# ADR-0001: Collaboration Workflow for Humans and AI Agents

## Status

Accepted

## Context

PlugWerk is a new open-source project where multiple humans and AI coding agents (Claude Code, GitHub Copilot, Cursor,
etc.) collaborate. We need to establish conventions for documentation, branching, commits, and code review from the
start.

## Decision

We adopt the following workflow:

1. **Dual instruction files**: `AGENTS.md` as the universal AI agent instruction file (supported by 60+ tools), and
   `CLAUDE.md` for Claude-specific behavior that imports AGENTS.md.

2. **Documentation split**: GitHub Issues for task tracking and collaboration. `docs/adrs/` for Architecture Decision
   Records (ADRs). `docs/features/` for feature specifications. Issues link to their corresponding feature spec files.

3. **Branch naming**: `feature/<issue-id>_<short-description>` format (e.g., `feature/42_user-auth`,
   `feature/15_fix-null-pointer`). Every branch ties back to a GitHub Issue. AI-generated worktree branch names are
   acceptable.

4. **Conventional Commits**: All commit messages follow the Conventional Commits specification. AI agents include
   `Co-Authored-By` trailers.

5. **PR-based workflow**: No direct pushes to `main`. All changes go through Pull Requests with review. PR template
   includes AI agent disclosure.

6. **Transparency**: AI contributions are clearly labeled through commit trailers and PR template disclosure. This is
   provenance tracking, not gatekeeping.

## Consequences

- **Easier**: Onboarding new contributors (human or AI) — clear conventions from day one. AI agents can read AGENTS.md
  and immediately understand project norms. Architecture decisions are documented and discoverable.
- **Harder**: Slightly more overhead per contribution (branch naming, PR template, commit format). But this overhead is
  minimal and prevents confusion as the team grows.
