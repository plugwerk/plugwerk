# plugwerk-server

Spring Boot 4.x web application with an embedded React frontend. Provides the REST API, plugin catalog, artifact storage, and admin UI.

## Structure

```
plugwerk-server/
├── plugwerk-server-backend/     # Kotlin + Spring Boot (JVM 21)
└── plugwerk-server-frontend/    # React 19 + TypeScript + Vite
```

## Backend (`plugwerk-server-backend`)

The backend implements the REST endpoints defined in `plugwerk-api` and manages all server-side concerns.

| Package | Responsibility |
|---------|---------------|
| `config` | Spring beans: security filter chain, JWT, text encryptor, admin bootstrapping, properties validation |
| `controller` | REST endpoint implementations (delegates to services) |
| `controller/mapper` | Entity-to-DTO mapping |
| `service` | Business logic: plugin CRUD, release lifecycle, namespace management, pf4j-update compatibility |
| `service/storage` | Artifact storage abstraction (`ArtifactStorageService`) with filesystem implementation |
| `repository` | Spring Data JPA repositories |
| `domain` | JPA entities: `Namespace`, `Plugin`, `PluginRelease`, `UserEntity`, `NamespaceMember`, `OidcProvider`, `NamespaceAccessKey` |
| `security` | JWT decoding (multi-issuer OIDC), namespace authorization, API key auth filter, password-change-required filter |

### Key Dependencies

- `plugwerk-api-endpoint` — generated Spring controller interfaces
- `plugwerk-descriptor` — parses `plugwerk.yml` / `MANIFEST.MF` from uploaded artifacts
- Spring Boot Starter (Web, Data JPA, Security, Validation, Actuator)
- Spring OAuth2 Resource Server (OIDC support)
- PostgreSQL + Liquibase (schema migrations)

### Database

PostgreSQL 18 with Liquibase-managed schema. Migrations live in `src/main/resources/db/changelog/`.

## Frontend (`plugwerk-server-frontend`)

Single-page application embedded into the Spring Boot JAR at build time.

| Directory | Contents |
|-----------|----------|
| `src/pages/` | Page components: Catalog, Plugin Detail, Upload, Review Queue, Admin Users, Namespaces |
| `src/components/` | Reusable UI components organized by feature (auth, catalog, layout, admin) |
| `src/stores/` | Zustand state stores: auth, namespace, plugin, UI |
| `src/api/generated/` | TypeScript API client generated from OpenAPI YAML (git-ignored) |
| `src/router/` | React Router v7 route definitions |
| `src/theme/` | MUI 7 theme customization |

### Key Dependencies

- React 19, React Router v7, Material UI 7
- Zustand 5 (state management)
- Axios (HTTP client), react-dropzone (file uploads)
- Vite 8 (bundler), Vitest (tests)

### Build Integration

The Gradle build calls `npm run build` via the `plugwerk-server-frontend` submodule, then copies the output to the backend's static resources. The backend's `SpaController` serves the SPA for all non-API routes.

## Running Locally

```bash
# Start database
docker compose up -d postgres

# Backend (port 8080) — requires PLUGWERK_JWT_SECRET and PLUGWERK_ENCRYPTION_KEY
./gradlew :plugwerk-server:plugwerk-server-backend:bootRun

# Frontend dev server (port 5173, proxies /api to localhost:8080)
cd plugwerk-server/plugwerk-server-frontend
npm install && npm run dev
```

## Testing

```bash
# Backend tests (JUnit 5 + Testcontainers)
./gradlew :plugwerk-server:plugwerk-server-backend:test

# Frontend tests (Vitest)
cd plugwerk-server/plugwerk-server-frontend
npm run test:run
```
