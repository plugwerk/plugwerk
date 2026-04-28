# Plugwerk

**Self-Hosted Plugin Management and Marketplace Software for the Java/PF4J Ecosystem**

Plugwerk is the missing link between [PF4J](https://github.com/pf4j/pf4j) and a product's plugin ecosystem: a central, self-hosted hub for publishing, versioning, distributing, and updating plugins at runtime. Application developers integrate the [`plugwerk-client-plugin`](plugwerk-client-plugin/) into their PF4J host; plugin developers upload artifacts via Web UI or REST API; operators run one server per organisation and serve multiple products via namespaces.

📚 **Full documentation, guides, and API reference — [plugwerk.io](https://www.plugwerk.io)**

## Quick Start (Docker Compose)

Two secrets must be set before the server will start:

```bash
# Generate the required secrets
echo "PLUGWERK_AUTH_JWT_SECRET=$(openssl rand -base64 32)" >  .env
echo "PLUGWERK_AUTH_ENCRYPTION_KEY=$(openssl rand -base64 32)" >> .env

# Pull the published compose file and start the stack
curl -sO https://www.plugwerk.io/deploy/docker-compose.yml
docker compose up -d
```

Wait for `curl http://localhost:8080/actuator/health` to return `{"status":"UP"}`, then open the Web UI at [http://localhost:8080](http://localhost:8080).

See [`.env.example`](.env.example) for the full list of environment variables (CORS, storage, auth tuning).

### Initial admin credentials

On first startup, Plugwerk generates a random superadmin password and prints it once to the log:

```bash
docker compose logs plugwerk-server | grep "Password :"
```

Username is always `admin`. You will be prompted to change the password on first login. For CI/CD or smoke-test environments, set `PLUGWERK_AUTH_ADMIN_PASSWORD` to a fixed value.

## API

Interactive API reference (Scalar) is served by every running instance:

- `http://localhost:8080/api-docs` — interactive
- `http://localhost:8080/api-docs/openapi.yaml` — raw OpenAPI 3.1 spec

Hosted reference: [plugwerk.io/api-docs](https://www.plugwerk.io/api-docs/).

## Repository Layout

```
plugwerk/
├── plugwerk-api/                  # OpenAPI 3.1 spec + generated DTOs/interfaces
├── plugwerk-spi/                  # Shared ExtensionPoint interfaces (JVM 11)
├── plugwerk-descriptor/           # MANIFEST.MF parser + plugin.properties fallback (JVM 11)
├── plugwerk-server/
│   ├── plugwerk-server-backend/   # Spring Boot 4 + Kotlin REST API (JVM 21)
│   └── plugwerk-server-frontend/  # React + TypeScript + MUI (embedded in server JAR)
└── plugwerk-client-plugin/        # PF4J plugin: OkHttp + Jackson (JVM 17)
```

Companion repositories:

- **[plugwerk/website](https://github.com/plugwerk/website)** — documentation site sources for [plugwerk.io](https://www.plugwerk.io)
- **[plugwerk/examples](https://github.com/plugwerk/examples)** — runnable example host applications integrating the client plugin

## Development

Prerequisites: JDK 21+, Node.js 20+, Docker.

```bash
# Postgres only (server runs from Gradle)
docker compose up -d postgres

# Build everything + run all tests
./gradlew build

# Backend at http://localhost:8080
./gradlew :plugwerk-server:plugwerk-server-backend:bootRun

# Frontend with hot reload at http://localhost:5173
cd plugwerk-server/plugwerk-server-frontend && npm run dev
```

For local HTTP dev set `PLUGWERK_AUTH_COOKIE_SECURE=false` — browsers drop `Secure` cookies over plain HTTP otherwise. See [`.env.example`](.env.example) for the full list and [`AGENTS.md`](AGENTS.md) for project conventions.

## Contributing

See [`CONTRIBUTING.md`](CONTRIBUTING.md). Conventional Commits format, branches `feature/<issue-id>_<short-description>`, no direct commits to `main`. PRs use the templates in [`.github/`](.github/).

## License

[AGPL-3.0](LICENSE).
