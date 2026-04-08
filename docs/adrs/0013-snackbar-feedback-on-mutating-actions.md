# ADR-0013: Snackbar Feedback on Mutating Actions

**Status:** Accepted
**Date:** 2026-04-08

## Context

Users perform mutating actions throughout the application — saving settings, deleting entities, toggling states, approving releases, etc. Without visual feedback, users are left uncertain whether their action succeeded or failed.

During the design review (#181), several "Save Changes" buttons and toggle actions were found to have no success notification, only error handling.

## Decision

**Every mutating user action must display a Snackbar notification confirming the outcome** — both on success and on failure.

### Rules

1. **Success:** Show a green Snackbar with a specific message describing what happened (e.g. "Namespace settings saved.", "User john-doe disabled.", "v1.2.0 approved and published.").
2. **Error:** Show a red Snackbar with an actionable message (e.g. "Failed to delete namespace.", "Failed to save settings.").
3. **Position:** All Snackbars appear at **top-center** (`anchorOrigin={{ vertical: 'top', horizontal: 'center' }}`).
4. **Auto-dismiss:** Success/info Snackbars dismiss after 4 seconds. Error Snackbars also auto-dismiss but include a manual close button.
5. **No silent mutations:** Actions that change server state must never complete silently. Even optimistic updates should show confirmation once the server responds.

### Mutating actions that require Snackbar feedback

- Save / Update settings (profile, namespace, general)
- Create entity (namespace, user, API key, OIDC provider)
- Delete entity (namespace, user, plugin, release, member, API key)
- Toggle state (enable/disable user, enable/disable OIDC provider)
- Status change (plugin status, release status, approve, reject)
- Upload (plugin release upload)
- Revoke (API key revocation)

### Implementation pattern

```tsx
const [toast, setToast] = useState<{ message: string; severity: 'success' | 'error' } | null>(null)

async function handleAction() {
  try {
    await api.mutate(...)
    setToast({ message: 'Action completed successfully.', severity: 'success' })
  } catch {
    setToast({ message: 'Failed to perform action.', severity: 'error' })
  }
}

<Snackbar
  open={!!toast}
  autoHideDuration={4000}
  onClose={() => setToast(null)}
  anchorOrigin={{ vertical: 'top', horizontal: 'center' }}
>
  <Alert severity={toast?.severity} onClose={() => setToast(null)} sx={{ width: '100%' }}>
    {toast?.message}
  </Alert>
</Snackbar>
```

## Consequences

- Consistent user experience across all mutating operations
- Users always know whether their action succeeded or failed
- Developers must add toast handling to every new mutating action
- The global `ToastRegion` component (used by `useUiStore.addToast()`) follows the same positioning and styling rules
