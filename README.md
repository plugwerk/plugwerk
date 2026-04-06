# Plugwerk

**Self-Hosted Plugin Management and Marketplace Software for the Java/PF4J Ecosystem**

Plugwerk is the missing link between [PF4J](https://github.com/pf4j/pf4j) and a product's plugin ecosystem. It gives you a central, self-hosted hub for publishing, versioning, distributing, and updating plugins at runtime — comparable to Maven Central for build dependencies, but designed for runtime plugins.

## Who is Plugwerk for?

| Role | How Plugwerk helps |
|------|--------------------|
| **Application developers** | Integrate the `plugwerk-client-plugin` into your PF4J-based application to let users discover, install, and update plugins at runtime — without shipping a new application release |
| **Plugin developers** | Upload plugin artifacts via the Web UI or REST API, manage versions and metadata, and publish releases with a single API call from your CI/CD pipeline |
| **Operations teams** | Deploy a self-hosted, Docker-based plugin management platform for your organization; one server can serve multiple products via namespaces |

## How it works

```
Your Application                     Plugwerk Server
┌────────────────────────────────┐    ┌─────────────────────────────┐
│ PF4J PluginManager             │    │ REST API  /api/v1/...       │
│  ↕ plugwerk-client-plugin      │◄──►│ Web UI    http://...        │
│    PlugwerkMarketplace         │    │ PostgreSQL + Artifact Store │
└────────────────────────────────┘    └─────────────────────────────┘
         ↑ runtime install/update               ↑ upload via CI/CD
         |                                      |
  end users / admins                    plugin developers
```

A **namespace** scopes all plugins for one product (e.g. `acme-crm`). Plugin metadata is read from the standard `MANIFEST.MF` inside the artifact; the server extracts it automatically on upload.

## Quick Start (Self-Hosted)

### Prerequisites

- Docker + Docker Compose

### Start with Docker Compose

```bash
git clone https://github.com/plugwerk/plugwerk.git
cd plugwerk
docker compose up -d
```

Wait for the server to be ready:

```bash
curl http://localhost:8080/actuator/health
# {"status":"UP"}
```

Open the Web UI at **http://localhost:8080**.

### Initial admin credentials

On first startup, Plugwerk creates a superadmin account with a **randomly generated password** and prints it once to the server log:

```
╔══════════════════════════════════════════════════════════╗
║         Plugwerk — Initial Superadmin Password           ║
║                                                          ║
║  Username : admin                                        ║
║  Password : <generated>                                  ║
║                                                          ║
║  Change this password immediately after first login.     ║
╚══════════════════════════════════════════════════════════╝
```

Retrieve the password from the logs:

```bash
docker compose logs plugwerk-server | grep "Password :"
```

You will be prompted to change the password on first login. The admin username is always `admin`.

> **CI/CD or smoke tests:** Set `PLUGWERK_AUTH_ADMIN_PASSWORD` to a fixed value to skip random generation and the forced password change.

### Obtain a token and create a namespace

```bash
# Login — use the password from the server log (see "Initial admin credentials" above)
TOKEN=$(curl -s -X POST http://localhost:8080/api/v1/auth/login \
  -H "Content-Type: application/json" \
  -d '{"username":"admin","password":"<password-from-log>"}' | jq -r '.accessToken')

# Create a namespace for your product
curl -s -X POST http://localhost:8080/api/v1/namespaces \
  -H "Authorization: Bearer $TOKEN" \
  -H "Content-Type: application/json" \
  -d '{"slug":"myproduct","ownerOrg":"My Company"}'
```

### Upload and publish a plugin

```bash
# Upload a plugin JAR/ZIP — metadata is read automatically from MANIFEST.MF inside the artifact
curl -s -X POST http://localhost:8080/api/v1/namespaces/myproduct/plugin-releases \
  -H "Authorization: Bearer $TOKEN" \
  -F "artifact=@my-plugin-1.0.0.jar"
# Returns: {"id":"...","version":"1.0.0","status":"draft",...}

# Publish the release (draft → published)
RELEASE_ID="<id from above>"
curl -s -X POST http://localhost:8080/api/v1/namespaces/myproduct/reviews/$RELEASE_ID/approve \
  -H "Authorization: Bearer $TOKEN"

# Browse the catalog
curl -s http://localhost:8080/api/v1/namespaces/myproduct/plugins | jq .
```

## Integrating the Client SDK into your Application

The `plugwerk-client-plugin` is a PF4J plugin with an isolated classloader — it has no dependency conflicts with your application. Add it to your PF4J plugins directory and use it to connect to a Plugwerk server at runtime.

### Add to your Gradle project

```kotlin
// build.gradle.kts
dependencies {
    // plugwerk-spi provides the ExtensionPoint interfaces
    implementation("io.plugwerk:plugwerk-spi:<version>")
}
```

The `plugwerk-client-plugin` ZIP is deployed as a PF4J plugin alongside your application's plugins, not as a compile-time dependency.

### Configure and use the client

```kotlin
import io.plugwerk.spi.PlugwerkConfig
import io.plugwerk.spi.PlugwerkMarketplace

// Build the configuration
val config = PlugwerkConfig.builder()
    .serverUrl("https://plugins.mycompany.com")
    .namespace("myproduct")
    .apiKey(System.getenv("PLUGWERK_API_KEY"))
    .build()

// Use as a pf4j-update UpdateRepository drop-in
val marketplace = PlugwerkMarketplace(config)
val updateManager = UpdateManager(pluginManager, listOf(marketplace.asUpdateRepository()))

// Or use the granular extension points directly
val catalog = pluginManager.getExtension(PlugwerkCatalog::class.java)
val installer = pluginManager.getExtension(PlugwerkInstaller::class.java)
val updateChecker = pluginManager.getExtension(PlugwerkUpdateChecker::class.java)
```

### pf4j-update drop-in replacement

Already using `pf4j-update`? Point it at Plugwerk's `plugins.json` endpoint — no code changes required:

```
https://your-plugwerk-server/api/v1/namespaces/{namespace}/plugins.json
```

### Plugin Metadata via `MANIFEST.MF`

Plugwerk reads plugin metadata directly from the standard Java `MANIFEST.MF` inside your plugin JAR. PF4J-standard attributes are supported alongside custom `Plugin-*` headers:

| MANIFEST.MF Attribute | Purpose | Required | PF4J Standard |
|---|---|---|---|
| `Plugin-Id` | Unique plugin identifier | **Yes** | Yes |
| `Plugin-Version` | SemVer version | **Yes** | Yes |
| `Plugin-Class` | Plugin class name | No | Yes |
| `Plugin-Provider` | Provider/organisation | No | Yes |
| `Plugin-Description` | Short description | No | Yes |
| `Plugin-Dependencies` | Comma-separated deps | No | Yes |
| `Plugin-Requires` | SemVer range for host | No | Yes |
| `Plugin-License` | SPDX license | No | Yes |
| `Plugin-Name` | Display name | No | No (custom) |
| `Plugin-Tags` | Comma-separated tags | No | No (custom) |
| `Plugin-Icon` | Icon URL/path | No | No (custom) |
| `Plugin-Screenshots` | Comma-separated URLs | No | No (custom) |
| `Plugin-Homepage` | Project URL | No | No (custom) |
| `Plugin-Repository` | Source code URL | No | No (custom) |

Example `MANIFEST.MF`:

```
Plugin-Id: com.example.my-plugin
Plugin-Version: 1.2.0
Plugin-Class: com.example.MyPlugin
Plugin-Provider: ACME GmbH
Plugin-Description: Exports reports as PDF with configurable templates
Plugin-License: Apache-2.0
Plugin-Requires: >=2.0.0 & <4.0.0
Plugin-Dependencies: com.example.template-engine@>=1.0.0
Plugin-Tags: pdf,report,export
```

If `MANIFEST.MF` is absent, the server falls back to `plugin.properties`.

## API Documentation

Interactive API reference powered by [Scalar](https://scalar.com/) — open in the browser after starting the server:

```
http://localhost:8080/api-docs
```

Raw OpenAPI 3.1 spec:

```
http://localhost:8080/api-docs/openapi.yaml
```

## Environment Variables

| Variable | Required | Default | Description |
|----------|----------|---------|-------------|
| `PLUGWERK_JWT_SECRET` | **yes** | — | HMAC-SHA256 signing secret, minimum 32 characters. Generate with `openssl rand -base64 32`. |
| `PLUGWERK_ENCRYPTION_KEY` | **yes** | — | AES encryption key for OIDC client secrets at rest, exactly 16 characters. Generate with `openssl rand -hex 8`. |
| `PLUGWERK_AUTH_ADMIN_PASSWORD` | no | _(random)_ | Fixed initial admin password. When set, skips random generation and the forced password change. When absent, a random password is generated and logged once. |
| `PLUGWERK_DB_URL` | no | `jdbc:postgresql://localhost:5432/plugwerk` | PostgreSQL JDBC URL |
| `PLUGWERK_DB_USERNAME` | no | `plugwerk` | Database username |
| `PLUGWERK_DB_PASSWORD` | no | `plugwerk` | Database password |
| `PLUGWERK_BASE_URL` | no | `http://localhost:8080` | Public base URL of the server (used for artifact download links) |
| `PLUGWERK_STORAGE_ROOT` | no | `/var/plugwerk/artifacts` | Filesystem path for storing artifact binaries |
| `PLUGWERK_STORAGE_TYPE` | no | `fs` | Storage backend: `fs` (filesystem) |

> **Tip:** Copy `.env.example` to `.env` and fill in the required values. The `.env` file is git-ignored.

## Key Features

- Searchable plugin catalog with full-text search, category and tag filters
- Plugin upload via Web UI or REST API (CI/CD-ready)
- SemVer-based versioning with compatibility ranges (`requires: >=2.0.0 & <4.0.0`)
- SHA-256 checksum verification for artifact integrity
- Drop-in replacement for [`pf4j-update`](https://github.com/pf4j/pf4j-update) — backward-compatible `plugins.json` endpoint
- Review/approval workflow before releases are published
- Multi-namespace support: one server serves multiple products/organizations
- Self-hosted via Docker Compose (open source, AGPL-3.0)

## Development Setup

### Prerequisites

- JDK 21+
- Node.js 20+
- Docker (for PostgreSQL)

### Run locally

```bash
# Start PostgreSQL
docker compose up -d postgres

# Build all modules
./gradlew build

# Start the server
./gradlew :plugwerk-server:plugwerk-server-backend:bootRun
```

The server starts at `http://localhost:8080`. The Vite dev server (frontend with hot reload) starts separately:

```bash
cd plugwerk-server/plugwerk-server-frontend
npm run dev
# Frontend available at http://localhost:5173 (proxies /api → localhost:8080)
```

### E2E Smoke Test

```bash
./scripts/smoke-test.sh
```

Runs the full upload → catalog → download flow against a Docker Compose stack.

### Build Commands

| Command | Description |
|---------|-------------|
| `./gradlew build` | Build all modules + run all tests |
| `./gradlew build -x test` | Build without tests |
| `./gradlew :plugwerk-api:openApiGenerate` | Regenerate backend DTOs/interfaces from OpenAPI spec |
| `./gradlew :plugwerk-server:plugwerk-server-backend:bootRun` | Start the backend server (port 8080) |
| `npm run dev` | Start the Vite frontend dev server (port 5173) |
| `npm run generate:api` | Regenerate TypeScript client from OpenAPI spec |
| `npm run test:run` | Run frontend tests (Vitest) |
| `npm run test:coverage` | Frontend tests with V8 coverage report |

## Module Structure

```
plugwerk/
├── plugwerk-api/                  # OpenAPI 3.1 spec (API-First) + generated DTOs/interfaces
├── plugwerk-spi/                  # Shared ExtensionPoint interfaces, DTOs, constants (JVM 11)
├── plugwerk-descriptor/           # MANIFEST.MF parser/validator + plugin.properties fallback (JVM 11)
├── plugwerk-server/
│   ├── plugwerk-server-backend/   # Spring Boot 4.x + Kotlin REST API (JVM 21)
│   └── plugwerk-server-frontend/  # React + TypeScript + Material UI + Zustand (embedded in server JAR)
├── plugwerk-client-plugin/        # PF4J plugin with isolated classloader: OkHttp + Jackson (JVM 17)
└── examples/
    └── plugwerk-java-cli-example/ # End-to-end example: CLI app with dynamically loaded plugins
```

## Documentation

- [Interactive API Reference](http://localhost:8080/api-docs) (requires running server)
- [Example: Java CLI Application](examples/plugwerk-java-cli-example/README.md)
- [Product Concept (EN)](docs/concepts/concept-pf4j-marketplace-en.md)
- [Produktkonzept (DE)](docs/concepts/concept-pf4j-marketplace-de.md)
- [Architecture Decision Records](docs/adrs/)

## Contributing

See [CONTRIBUTING.md](CONTRIBUTING.md) for guidelines.

- Language: **English** for all code, docs, issues, and reviews
- Branches: `feature/<issue-id>_<short-description>` — never commit directly to `main`
- Commits: [Conventional Commits](https://www.conventionalcommits.org/) format
- Use the issue and PR templates in `.github/`

## License

[AGPL-3.0](LICENSE)
