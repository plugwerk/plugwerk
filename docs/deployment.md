# Plugwerk Server — Deployment Guide

This guide covers all supported ways to run the Plugwerk Server.

## Prerequisites

- **Java 21** or later (for non-container deployment)
- **Docker** and **Docker Compose** (for container deployment)
- **PostgreSQL 16+** database

## Download

Download the distribution ZIP from the latest [GitHub Release](https://github.com/plugwerk/plugwerk/releases):

```
plugwerk-server-<version>.zip
```

Or pull from Maven Central:

```bash
# Using Maven dependency:resolve
mvn dependency:copy -Dartifact=io.plugwerk:plugwerk-server:<version>:zip -DoutputDirectory=.
```

Extract the ZIP:

```bash
unzip plugwerk-server-<version>.zip
cd plugwerk-server-<version>/
```

Contents:

```
plugwerk-server-<version>/
├── plugwerk-server-backend-<version>.jar   # Spring Boot fat JAR
├── start.sh                                 # Linux/macOS start script
├── start.bat                                # Windows start script
├── Dockerfile                               # Container image definition
├── docker-compose.yml                       # Full stack with PostgreSQL
├── LICENSE
└── README.md
```

---

## Option 0: Pull from Docker Hub

The published image `plugwerk/plugwerk-server` is available on Docker Hub for
`linux/amd64` and `linux/arm64`. Every `v*` Git tag publishes a matching image
tag, and stable releases also update `:latest`.

```bash
# Pull a specific version (recommended for production)
docker pull plugwerk/plugwerk-server:1.0.0

# Pull the latest stable release
docker pull plugwerk/plugwerk-server:latest

# Pull a pre-release (no ':latest' redirect)
docker pull plugwerk/plugwerk-server:1.0.0-alpha.1
```

You still need a PostgreSQL instance and the required environment variables
(see [Environment Variables](#environment-variables) below). The simplest way
to run the full stack is Option 1 (Docker Compose), which references this
image automatically.

---

## Option 1: Docker Compose (recommended)

The fastest way to get started. Includes PostgreSQL and the server.

### 1.0 Pin the image version (optional but recommended)

By default, `docker-compose.yml` pulls `plugwerk/plugwerk-server:latest`. For
reproducible deployments, pin a specific version via the `PLUGWERK_VERSION`
environment variable:

```bash
export PLUGWERK_VERSION=1.0.0
```

### 1.1 Generate secrets

```bash
export PLUGWERK_JWT_SECRET="$(openssl rand -base64 32)"
export PLUGWERK_ENCRYPTION_KEY="$(openssl rand -hex 8)"
```

### 1.2 Start the stack

```bash
docker compose up -d
```

The server is available at `http://localhost:8080`.

### 1.3 Check health

```bash
curl http://localhost:8080/actuator/health
```

### 1.4 Stop

```bash
docker compose down          # stop, keep data
docker compose down -v       # stop and delete database volume
```

### 1.5 Custom JVM options

The published Docker image reads the standard `JAVA_OPTS` environment variable
to pass arguments to the JVM. You can override it from the host shell or from
a `docker-compose.override.yml`:

```yaml
# docker-compose.override.yml
services:
  plugwerk-server:
    environment:
      JAVA_OPTS: "-Xms512m -Xmx2g"
```

---

## Option 2: Standalone Docker

Run the server container without Compose. Requires an external PostgreSQL instance.

### 2.1 Build the image

```bash
docker build -t plugwerk-server .
```

### 2.2 Run

```bash
docker run -d \
  --name plugwerk-server \
  -p 8080:8080 \
  -e PLUGWERK_DB_URL=jdbc:postgresql://host.docker.internal:5432/plugwerk \
  -e PLUGWERK_DB_USERNAME=plugwerk \
  -e PLUGWERK_DB_PASSWORD=plugwerk \
  -e PLUGWERK_JWT_SECRET="$(openssl rand -base64 32)" \
  -e PLUGWERK_ENCRYPTION_KEY="$(openssl rand -hex 8)" \
  -e PLUGWERK_STORAGE_ROOT=/data/artifacts \
  -v plugwerk-artifacts:/data/artifacts \
  plugwerk-server
```

### 2.3 Custom JVM options

```bash
docker run -e JAVA_OPTS="-Xms512m -Xmx2g" plugwerk-server
```

---

## Option 3: Start Script (Linux/macOS)

Run directly on the host with the included start script.

### 3.1 Prerequisites

- Java 21+ installed (`java -version`)
- PostgreSQL running and accessible

### 3.2 Configure and start

```bash
export PLUGWERK_DB_URL=jdbc:postgresql://localhost:5432/plugwerk
export PLUGWERK_DB_USERNAME=plugwerk
export PLUGWERK_DB_PASSWORD=plugwerk
export PLUGWERK_JWT_SECRET="$(openssl rand -base64 32)"
export PLUGWERK_ENCRYPTION_KEY="$(openssl rand -hex 8)"

./start.sh
```

### 3.3 Custom JVM options

```bash
JAVA_OPTS="-Xms512m -Xmx2g" ./start.sh
```

### 3.4 Pass Spring Boot arguments

```bash
./start.sh --server.port=9090
```

---

## Option 4: Start Script (Windows)

### 4.1 Prerequisites

- Java 21+ installed and on `PATH`
- PostgreSQL running and accessible

### 4.2 Configure and start

```cmd
set PLUGWERK_DB_URL=jdbc:postgresql://localhost:5432/plugwerk
set PLUGWERK_DB_USERNAME=plugwerk
set PLUGWERK_DB_PASSWORD=plugwerk
set PLUGWERK_JWT_SECRET=<your-secret-min-32-chars>
set PLUGWERK_ENCRYPTION_KEY=<your-key-exactly-16-chars>

start.bat
```

### 4.3 Custom JVM options

```cmd
set JAVA_OPTS=-Xms512m -Xmx2g
start.bat
```

---

## Option 5: Direct JAR execution

For full control, run the fat JAR directly:

```bash
java \
  -Xms256m -Xmx512m \
  -XX:+UseG1GC \
  -XX:+ExitOnOutOfMemoryError \
  -jar plugwerk-server-backend-<version>.jar \
  --server.port=8080
```

---

## Option 6: systemd Service (Linux production)

For production Linux servers, create a systemd unit file:

```ini
# /etc/systemd/system/plugwerk.service
[Unit]
Description=Plugwerk Server
After=network.target postgresql.service

[Service]
Type=exec
User=plugwerk
Group=plugwerk
WorkingDirectory=/opt/plugwerk
ExecStart=/usr/bin/java -Xms512m -Xmx2g -XX:+UseG1GC -XX:+ExitOnOutOfMemoryError -jar plugwerk-server-backend.jar
EnvironmentFile=/etc/plugwerk/plugwerk.env
Restart=on-failure
RestartSec=10

[Install]
WantedBy=multi-user.target
```

```bash
# /etc/plugwerk/plugwerk.env
PLUGWERK_DB_URL=jdbc:postgresql://localhost:5432/plugwerk
PLUGWERK_DB_USERNAME=plugwerk
PLUGWERK_DB_PASSWORD=<secret>
PLUGWERK_JWT_SECRET=<secret>
PLUGWERK_ENCRYPTION_KEY=<secret>
PLUGWERK_STORAGE_ROOT=/var/plugwerk/artifacts
```

```bash
sudo systemctl enable plugwerk
sudo systemctl start plugwerk
sudo journalctl -u plugwerk -f    # view logs
```

---

## Environment Variables

### Required

| Variable | Description | Example |
|----------|-------------|---------|
| `PLUGWERK_JWT_SECRET` | HMAC-SHA256 signing key for JWTs. Min 32 characters. | `openssl rand -base64 32` |
| `PLUGWERK_ENCRYPTION_KEY` | AES key for OIDC client secrets at rest. Exactly 16 characters. | `openssl rand -hex 8` |

### Database

| Variable | Default | Description |
|----------|---------|-------------|
| `PLUGWERK_DB_URL` | `jdbc:postgresql://localhost:5432/plugwerk` | JDBC connection URL |
| `PLUGWERK_DB_USERNAME` | `plugwerk` | Database username |
| `PLUGWERK_DB_PASSWORD` | `plugwerk` | Database password |

### Storage

| Variable | Default | Description |
|----------|---------|-------------|
| `PLUGWERK_STORAGE_TYPE` | `fs` | Storage backend (`fs` for filesystem) |
| `PLUGWERK_STORAGE_ROOT` | `/var/plugwerk/artifacts` | Directory for uploaded plugin artifacts |

### Server

| Variable | Default | Description |
|----------|---------|-------------|
| `PLUGWERK_BASE_URL` | `http://localhost:8080` | Public base URL (used in download links) |

### CORS

See [ADR-0021](adrs/0021-cors-same-origin-default.md). Defaults to same-origin-only; the frontend is bundled with the server JAR so no cross-origin request is needed in the standard deployment. Add entries to `PLUGWERK_SERVER_CORS_ALLOWED_ORIGINS` only if the frontend is deployed on a separate origin (CDN, subdomain, dev server on `localhost:5173`, …).

| Variable | Default | Description |
|----------|---------|-------------|
| `PLUGWERK_SERVER_CORS_ALLOWED_ORIGINS` | *(empty = same-origin-only)* | Comma-separated exact origin values. Case-sensitive, scheme and host must match exactly. Example: `http://localhost:5173,https://plugins.example.com`. Wildcards (`*`) are rejected at startup when combined with `PLUGWERK_SERVER_CORS_ALLOW_CREDENTIALS=true`. |
| `PLUGWERK_SERVER_CORS_ALLOWED_METHODS` | `GET,POST,PUT,PATCH,DELETE,OPTIONS` | Comma-separated HTTP methods browsers may use in cross-origin requests. |
| `PLUGWERK_SERVER_CORS_ALLOWED_HEADERS` | `Authorization,Content-Type,X-Api-Key` | Comma-separated request headers browsers may send. |
| `PLUGWERK_SERVER_CORS_ALLOW_CREDENTIALS` | `true` | Whether browsers may include credentials (`Authorization` header, cookies). Required for JWT Bearer auth to reach the server from a different origin. Must be `false` when `PLUGWERK_SERVER_CORS_ALLOWED_ORIGINS` contains `*`. |
| `PLUGWERK_SERVER_CORS_MAX_AGE` | `3600` | Preflight cache duration in seconds. Bounded at `0..86400`. |

**Local dev with the Vite dev server** (`npm run dev`, port 5173, proxies `/api/**` to the backend) — the proxy forwards the `Origin: http://localhost:5173` header, so the backend rejects it with 403 under the default empty allow-list. Export before starting the backend:

```bash
export PLUGWERK_SERVER_CORS_ALLOWED_ORIGINS=http://localhost:5173
./gradlew :plugwerk-server:plugwerk-server-backend:bootRun
```

> **Breaking change in 1.0.0-alpha.2 (#208, ADR-0016):** `PLUGWERK_UPLOAD_MAX_FILE_SIZE_MB`
> is no longer read. Upload size is now stored in the `application_setting` database table
> and managed via the Admin → Settings UI or `PATCH /api/v1/admin/settings`. On first upgrade
> the value is seeded to `100` MB. Changing it at runtime still requires a server restart for
> the multipart filter to pick up the new limit — the Admin UI displays a restart-pending
> notice when applicable.

### Authentication

| Variable | Default | Description |
|----------|---------|-------------|
| `PLUGWERK_AUTH_ADMIN_PASSWORD` | *(random, logged on first start)* | Fixed superadmin password. If unset, a random password is generated and printed to the log on first startup. |
| `PLUGWERK_AUTH_RATE_LIMIT_MAX_ATTEMPTS` | `10` | Max login attempts per IP per window |
| `PLUGWERK_AUTH_RATE_LIMIT_WINDOW_SECONDS` | `60` | Rate limit window duration |

### Tracking

> **Breaking change in 1.0.0-alpha.2 (#208, ADR-0016):** The `PLUGWERK_TRACKING_*`
> environment variables have been removed. Download-tracking flags are now stored in the
> `application_setting` database table and managed via the Admin → Settings UI or
> `PATCH /api/v1/admin/settings`. Existing deployments that relied on the env variables
> must set the equivalent rows in the Admin UI after the first upgrade. The Liquibase seed
> applies the previous defaults (`enabled=true`, `capture_ip=true`, `anonymize_ip=true`,
> `capture_user_agent=true`), so no action is required unless your deployment had overrides.

### JVM

| Variable | Default | Description |
|----------|---------|-------------|
| `JAVA_OPTS` | `-Xms256m -Xmx512m -XX:+UseG1GC -XX:+ExitOnOutOfMemoryError` | JVM arguments (used by start scripts and Dockerfile) |

---

## Health Check

```bash
curl http://localhost:8080/actuator/health
```

Returns `{"status":"UP"}` when the server and database connection are healthy.

## API Documentation

Once running, the OpenAPI spec is available at:

```
http://localhost:8080/api-docs/openapi.yaml
```
