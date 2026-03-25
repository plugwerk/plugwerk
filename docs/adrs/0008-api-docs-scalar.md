# ADR-0008: API Documentation — Scalar instead of SpringDoc/Swagger UI

## Status

Accepted

## Context

Plugwerk needs interactive API documentation accessible at a well-known URL. Common options:

1. **SpringDoc OpenAPI** (`springdoc-openapi-starter-webmvc-ui`) — the standard Spring Boot Swagger UI integration
2. **Redoc** — clean, three-panel documentation UI, embeddable via React component
3. **Scalar** (`@scalar/api-reference-react`) — modern, visually polished API reference UI, embeddable as React component

Two additional constraints shaped the decision:

- **Spring Boot 4 compatibility:** SpringDoc 2.x supports Spring Boot 3.x only. As of the time of this decision, no stable SpringDoc release supports Spring Boot 4 / Spring Framework 7. A snapshot dependency would be required.
- **Existing canonical spec:** The project is API-First. A complete, authoritative `plugwerk-api.yaml` already exists and is the single source of truth. SpringDoc's code-scanning approach would generate a second spec that could drift from the canonical one.

## Decision

- The backend serves the canonical `plugwerk-api.yaml` at `/api-docs/openapi.yaml` via a Gradle `processResources` copy task (no duplication in source, no extra Spring component)
- The frontend embeds **Scalar** (`@scalar/api-reference-react`) in an `ApiDocsPage` that points to `/api-docs/openapi.yaml`
- The "API Docs" link in the application footer navigates to `/api-docs`
- Spring Security permits unauthenticated access to `/api-docs/**`

## Consequences

- **Easier:** No Spring Boot 4 compatibility issues — zero SpringDoc dependency
- **Easier:** One canonical spec — no risk of generated vs. hand-written spec divergence
- **Easier:** Scalar respects the app's dark/light mode preference
- **Harder:** API docs are only available when the frontend is running (not as a standalone static page)
- **Note:** If SpringDoc releases stable Spring Boot 4 support, this decision can be revisited (ADR-0008 superseded)
