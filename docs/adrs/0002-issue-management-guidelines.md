# ADR-0002: Issue Management Guidelines

## Status

Accepted

## Context

As the project grows, GitHub Issues become the primary tool for planning, tracking, and coordinating work across
contributors (human and AI). Without consistent standards for issue creation, issues end up incomplete — missing types,
milestones, labels, or relationships — making it difficult to filter, prioritize, and understand the project's state at
a glance.

## Decision

Every GitHub Issue must have the following fields set at creation time:

### 1. Issue Type (mandatory)

Use one of the repository's defined issue types:

| Type | When to use |
|------|-------------|
| **Feature** | New functionality, epics, user-facing capabilities |
| **Bug** | Something is broken or behaves incorrectly |
| **Task** | Technical work, refactoring, chores, documentation, infrastructure |

### 2. Milestone (mandatory)

Every issue must be assigned to a milestone. Milestones represent delivery phases:

| Milestone | Scope |
|-----------|-------|
| Phase 1 — MVP | Core marketplace: REST API, minimal Web UI, Client SDK |
| *(future)* | Additional milestones are created as phases are planned |

If no suitable milestone exists, create one before creating the issue.

### 3. Labels (mandatory)

Apply all relevant labels. At minimum, one label indicating the nature of the work:

| Label | Purpose |
|-------|---------|
| `enhancement` | New feature or improvement |
| `bug` | Defect or regression |
| `documentation` | Documentation-only changes |
| `good first issue` | Suitable for newcomers |
| `help wanted` | Community contributions welcome |

Additional labels may be introduced as needed (e.g., module-specific labels like `server`, `sdk`, `frontend`).

### 4. Relationships (mandatory, if applicable)

Use GitHub's sub-issue / parent-issue relationships to maintain hierarchy:

- **Epics** (large Feature issues) should have sub-issues for each milestone or work package.
- **Sub-issues** must reference their parent issue.
- **Blocking/blocked-by** relationships should be documented in the issue body when GitHub's native relationship
  features do not cover the dependency.

### Issue Templates

All issues must use the appropriate template from `.github/ISSUE_TEMPLATE/`:

- Bugs: `bug_report.md`
- Features: `feature_request.md`

### Checklist for Issue Creation

Before submitting an issue, verify:

- [ ] Issue type is set (Feature / Bug / Task)
- [ ] Milestone is assigned
- [ ] All relevant labels are applied
- [ ] Relationships to parent/child/related issues are set (if applicable)
- [ ] Appropriate issue template is used

## Consequences

- **Easier**: Filtering and prioritizing work by milestone, type, and label. Understanding project progress at a glance.
  AI agents can create well-structured issues autonomously. Dependency tracking through relationships prevents missed
  prerequisites.
- **Harder**: Slightly more effort per issue creation. Requires discipline to maintain milestones and labels. But the
  overhead is minimal compared to the cost of disorganized issue tracking.
