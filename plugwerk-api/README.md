# plugwerk-api

OpenAPI 3.1 specification and generated code for the Plugwerk REST API.

## Purpose

This module follows an **API-first** approach: the OpenAPI YAML specification is the single source of truth for all REST endpoints, request/response schemas, and authentication schemes. Code is generated from this spec — never hand-written.

## Structure

```
plugwerk-api/
├── src/main/resources/openapi/
│   └── plugwerk-api.yaml            # OpenAPI 3.1 specification
├── plugwerk-api-model/              # Generated DTOs (data classes)
└── plugwerk-api-endpoint/           # Generated Spring controller interfaces
```

## Submodules

| Submodule | JVM Target | Contents | Consumers |
|-----------|-----------|----------|-----------|
| `plugwerk-api-model` | 17 | DTOs with Jackson + Jakarta Validation annotations | Server backend, Client SDK |
| `plugwerk-api-endpoint` | 21 | Spring `@RestController` interfaces with `@RequestMapping` | Server backend only |

The split allows the Client SDK to depend on the lightweight model classes without pulling in Spring.

## Code Generation

**Backend (Kotlin):**

```bash
./gradlew :plugwerk-api:plugwerk-api-endpoint:openApiGenerate \
          :plugwerk-api:plugwerk-api-model:openApiGenerate
```

**Frontend (TypeScript):**

```bash
cd plugwerk-server/plugwerk-server-frontend
npm run generate:api
```

## API Overview

All namespace-scoped endpoints live under `/api/v1/namespaces/{ns}/...`.

| Area | Key Endpoints |
|------|--------------|
| Catalog | `GET /plugins`, `GET /plugins/{id}`, `GET /plugins.json` (pf4j-update compatible) |
| Releases | `POST /plugin-releases` (upload), `PATCH /plugins/{id}/releases/{version}` (status change) |
| Updates | `POST /updates/check` (batch update check) |
| Reviews | `GET /reviews/pending`, `POST /reviews/{id}/approve\|reject` |
| Auth | `POST /auth/login`, `POST /auth/change-password` |
| Admin | `GET/POST /admin/users`, `GET/POST /admin/oidc-providers` |

## Authentication Schemes

- **Bearer JWT** (local login or OIDC)
- **API Key** (`X-Api-Key` header, namespace-scoped)
