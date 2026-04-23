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

> **Warning — placeholders in this snippet are intentionally invalid.** `PLUGWERK_AUTH_JWT_SECRET` and `PLUGWERK_AUTH_ENCRYPTION_KEY` below fail the server's minimum-length validation on purpose, so a verbatim `docker compose up` aborts at startup with a clear property-validation error. Replace the database password and export real secrets as shown in the next step before running the stack.

Create a `docker-compose.yml`:

```yaml
services:
  postgres:
    image: postgres:18-alpine
    environment:
      POSTGRES_DB: plugwerk
      POSTGRES_USER: plugwerk
      POSTGRES_PASSWORD: REPLACE_ME_STRONG_PASSWORD
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
      PLUGWERK_DB_PASSWORD: REPLACE_ME_STRONG_PASSWORD
      # Short placeholder — fails @Size(min=32) and aborts startup until replaced.
      PLUGWERK_AUTH_JWT_SECRET: "REPLACE_ME_SEE_README"
      # Short placeholder — fails @Size(min=16) and aborts startup until replaced.
      PLUGWERK_AUTH_ENCRYPTION_KEY: "REPLACE_ME"
    volumes:
      - plugwerk-artifacts:/var/plugwerk/artifacts

volumes:
  postgres-data:
  plugwerk-artifacts:
```

Generate secure secrets and start:

```bash
# 1) Edit docker-compose.yml and replace each REPLACE_ME_* literal with the
#    value you want to pin (the database password is a plain string; the two
#    server secrets are easier to export and substitute, see below).
# 2) Export real secrets into your shell:
export PLUGWERK_AUTH_JWT_SECRET="$(openssl rand -base64 32)"
export PLUGWERK_AUTH_ENCRYPTION_KEY="$(openssl rand -base64 32)"
# 3) Substitute them in-place (or paste the values into the compose file).
# 4) Launch:
docker compose up -d
```

On first start the server generates a random superadmin password and prints it once to the container log. Retrieve it and log in:

```bash
docker compose logs plugwerk-server | grep -A 6 "Initial Superadmin Password"
```

Open http://localhost:8080 and log in with `admin` / that value. You will be required to change the password on first login.

## Configuration

| Variable | Required | Default | Description |
|---|---|---|---|
| `PLUGWERK_AUTH_JWT_SECRET` | **yes** | — | HMAC signing key for JWTs, min 32 chars |
| `PLUGWERK_AUTH_ENCRYPTION_KEY` | **yes** | — | Password for the AES-256-CBC text encryptor that protects OIDC client secrets at rest. PBKDF2-derived key, so length controls input entropy (not AES key size). Min 16 chars; 32+ recommended. |
| `PLUGWERK_DB_URL` | no | `jdbc:postgresql://localhost:5432/plugwerk` | JDBC URL |
| `PLUGWERK_DB_USERNAME` | no | `plugwerk` | DB user |
| `PLUGWERK_DB_PASSWORD` | no | `plugwerk` | DB password |
| `PLUGWERK_STORAGE_ROOT` | no | `/var/plugwerk/artifacts` | Artifact storage directory |
| `PLUGWERK_AUTH_ADMIN_PASSWORD` | no | *(random, logged, `passwordChangeRequired`)* | Pin the initial superadmin password (CI/smoke-test only — **do not set in production**). Blank or whitespace-only values are treated the same as unset. |
| `PLUGWERK_SERVER_CORS_ALLOWED_ORIGINS` | no | *(empty = same-origin-only)* | Comma-separated origins allowed to make cross-origin requests (e.g. `https://frontend.example.com`). Default empty preserves the bundled-frontend same-origin deployment. Wildcards not supported with default credentials. |
| `PLUGWERK_SERVER_CORS_ALLOWED_METHODS` | no | `GET,POST,PUT,PATCH,DELETE,OPTIONS` | Comma-separated HTTP methods for cross-origin requests |
| `PLUGWERK_SERVER_CORS_ALLOWED_HEADERS` | no | `Authorization,Content-Type,X-Api-Key` | Comma-separated request headers for cross-origin requests |
| `PLUGWERK_SERVER_CORS_ALLOW_CREDENTIALS` | no | `true` | Whether browsers may include credentials on cross-origin requests. Must be `false` when `PLUGWERK_SERVER_CORS_ALLOWED_ORIGINS=*`. |
| `PLUGWERK_SERVER_CORS_MAX_AGE` | no | `3600` | Preflight cache duration in seconds (0..86400) |
| `JAVA_OPTS` | no | `-Xms256m -Xmx512m -XX:+UseG1GC` | JVM options |

Full reference: https://plugwerk.io/server/configuration/

## Persistent storage

The container writes uploaded plugin artifacts to `/var/plugwerk/artifacts` (owned by the non-root `plugwerk` user). Always mount a persistent volume here — the example above uses a named Docker volume (`plugwerk-artifacts`).

## Health check

```bash
curl http://localhost:8080/actuator/health
# → {"status":"UP"}
```

`/actuator/health` is public by design — safe for Docker / Kubernetes healthchecks with no credentials.

## Monitoring

Three actuator endpoints are exposed:

| Endpoint | Auth | Intended consumer |
|---|---|---|
| `/actuator/health` | public | healthchecks, uptime probes |
| `/actuator/info` | superadmin JWT **or** HTTP Basic scrape account | ad-hoc inspection, metric collectors |
| `/actuator/prometheus` | superadmin JWT **or** HTTP Basic scrape account | Prometheus scraper |

### Unattended Prometheus scraping

Configure a dedicated scrape account by setting **both** env vars on the container:

```bash
docker run -d \
  -e PLUGWERK_AUTH_ACTUATOR_SCRAPE_USERNAME=prometheus \
  -e PLUGWERK_AUTH_ACTUATOR_SCRAPE_PASSWORD="$(openssl rand -base64 32)" \
  # ... other PLUGWERK_* env vars ...
  plugwerk/plugwerk-server:latest
```

The password must be at least 16 characters (32+ recommended). If only one of the two is set, the container refuses to start — that asymmetry would silently be misconfigured at scrape time.

Example `scrape_configs` for Prometheus:

```yaml
scrape_configs:
  - job_name: plugwerk
    metrics_path: /actuator/prometheus
    scheme: https
    basic_auth:
      username: prometheus
      password_file: /etc/prometheus/plugwerk-scrape-password
    static_configs:
      - targets: ['plugwerk.example.com']
```

### No scrape account configured?

When the two env vars are unset (the default), `/actuator/info` and `/actuator/prometheus` fall back to superadmin-JWT-only. That is deliberate — namespace members cannot read metrics even if they are logged in. `/actuator/health` stays public regardless.

See [ADR-0025](https://github.com/plugwerk/plugwerk/blob/main/docs/adrs/0025-actuator-endpoint-hardening.md) for the full decision record and threat model.

## Deployment guides

- [Docker Compose](https://plugwerk.io/server/deployment/#docker-compose-recommended)
- [Standalone Docker](https://plugwerk.io/server/deployment/#standalone-docker-container)
- [JAR execution (systemd, k8s, etc.)](https://plugwerk.io/server/deployment/#jar-execution)

## License

AGPL-3.0 — see [LICENSE](https://github.com/plugwerk/plugwerk/blob/main/LICENSE).
