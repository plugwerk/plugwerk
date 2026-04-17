# Plugwerk Server

**Plugwerk** is a self-hosted plugin marketplace for the [PF4J](https://pf4j.org/) ecosystem. It lets teams publish, version, and distribute Java/Kotlin plugins to their own applications without relying on a public registry.

- 🏠 Source: https://github.com/plugwerk/plugwerk
- 📘 Docs: https://plugwerk.io
- 🐛 Issues: https://github.com/plugwerk/plugwerk/issues
- 📦 GHCR mirror: `ghcr.io/plugwerk/plugwerk-server`

## Supported tags

| Tag | Description |
|---|---|
| `latest` | Latest stable release (semver, no pre-release) |
| `1.0.0-alpha.2`, `1.0`, `1` | Specific versions and minor/major aliases |
| `snapshot` | Rolling development build from the `main` branch (on [GHCR only](https://github.com/plugwerk/plugwerk/pkgs/container/plugwerk-server)) |

Multi-architecture: `linux/amd64`, `linux/arm64`.

## Quick start

Create a `docker-compose.yml`:

```yaml
services:
  postgres:
    image: postgres:18-alpine
    environment:
      POSTGRES_DB: plugwerk
      POSTGRES_USER: plugwerk
      POSTGRES_PASSWORD: plugwerk
    volumes:
      - postgres-data:/var/lib/postgresql/data
    healthcheck:
      test: ["CMD-SHELL", "pg_isready -U plugwerk"]
      interval: 5s
      retries: 5

  plugwerk-server:
    image: plugwerk/plugwerk-server:latest
    ports:
      - "8080:8080"
    depends_on:
      postgres:
        condition: service_healthy
    environment:
      PLUGWERK_DB_URL: jdbc:postgresql://postgres:5432/plugwerk
      PLUGWERK_DB_USERNAME: plugwerk
      PLUGWERK_DB_PASSWORD: plugwerk
      PLUGWERK_JWT_SECRET: "change-me-min-32-characters-long!"
      PLUGWERK_ENCRYPTION_KEY: "exactly16charss"
      PLUGWERK_AUTH_ADMIN_PASSWORD: admin
    volumes:
      - plugwerk-artifacts:/var/plugwerk/artifacts

volumes:
  postgres-data:
  plugwerk-artifacts:
```

Generate secure secrets and start:

```bash
export PLUGWERK_JWT_SECRET="$(openssl rand -base64 32)"
export PLUGWERK_ENCRYPTION_KEY="$(openssl rand -hex 8)"
docker compose up -d
```

Open http://localhost:8080 and log in with `admin` / your `PLUGWERK_AUTH_ADMIN_PASSWORD` value.

## Configuration

| Variable | Required | Default | Description |
|---|---|---|---|
| `PLUGWERK_JWT_SECRET` | **yes** | — | HMAC signing key for JWTs, min 32 chars |
| `PLUGWERK_ENCRYPTION_KEY` | **yes** | — | AES key for OIDC secrets, exactly 16 chars |
| `PLUGWERK_DB_URL` | no | `jdbc:postgresql://localhost:5432/plugwerk` | JDBC URL |
| `PLUGWERK_DB_USERNAME` | no | `plugwerk` | DB user |
| `PLUGWERK_DB_PASSWORD` | no | `plugwerk` | DB password |
| `PLUGWERK_STORAGE_ROOT` | no | `/var/plugwerk/artifacts` | Artifact storage directory |
| `PLUGWERK_AUTH_ADMIN_PASSWORD` | no | *(random, logged)* | Initial superadmin password |
| `JAVA_OPTS` | no | `-Xms256m -Xmx512m -XX:+UseG1GC` | JVM options |

Full reference: https://plugwerk.io/server/configuration/

## Persistent storage

The container writes uploaded plugin artifacts to `/var/plugwerk/artifacts` (owned by the non-root `plugwerk` user). Always mount a persistent volume here — the example above uses a named Docker volume (`plugwerk-artifacts`).

## Health check

```bash
curl http://localhost:8080/actuator/health
# → {"status":"UP"}
```

## Deployment guides

- [Docker Compose](https://plugwerk.io/server/deployment/#docker-compose-recommended)
- [Standalone Docker](https://plugwerk.io/server/deployment/#standalone-docker-container)
- [JAR execution (systemd, k8s, etc.)](https://plugwerk.io/server/deployment/#jar-execution)

## License

AGPL-3.0 — see [LICENSE](https://github.com/plugwerk/plugwerk/blob/main/LICENSE).
