# Development Guide

This guide covers development workflows for contributors and consumers of Plugwerk libraries.

## Resolving SNAPSHOT Artifacts

Every push to `main` publishes SNAPSHOT versions of all Plugwerk library modules to
[GitHub Packages](https://github.com/orgs/plugwerk/packages). This allows dependent
repositories to resolve development builds without local `publishToMavenLocal` workflows.

### Published SNAPSHOT Modules

| Module | Artifact ID | Description |
|---|---|---|
| `plugwerk-spi` | `io.plugwerk:plugwerk-spi` | Extension point interfaces and shared model types |
| `plugwerk-descriptor` | `io.plugwerk:plugwerk-descriptor` | MANIFEST.MF parser and validator |
| `plugwerk-api-model` | `io.plugwerk:plugwerk-api-model` | Generated REST API DTOs |
| `plugwerk-client-plugin` | `io.plugwerk:plugwerk-client-plugin` | Client SDK (JAR + PF4J ZIP) |
| `plugwerk-server` | `io.plugwerk:plugwerk-server` | Server distribution ZIP |

### Gradle Configuration (CI)

In GitHub Actions, the automatic `GITHUB_TOKEN` provides read access to GitHub Packages within
the same organization. Add the repository to your `build.gradle.kts`:

```kotlin
repositories {
    mavenCentral()
    maven {
        name = "GitHubPackages"
        url = uri("https://maven.pkg.github.com/plugwerk/plugwerk")
        credentials {
            username = providers.environmentVariable("GITHUB_ACTOR").getOrElse("")
            password = providers.environmentVariable("GITHUB_TOKEN").getOrElse("")
        }
    }
}

dependencies {
    implementation("io.plugwerk:plugwerk-spi:1.0.0-SNAPSHOT")
}
```

No additional secrets are needed — `GITHUB_TOKEN` and `GITHUB_ACTOR` are automatically
available in GitHub Actions workflows.

### Gradle Configuration (Local Development)

GitHub Packages requires authentication even for reading public packages. Create a Personal
Access Token (PAT) with `read:packages` scope and configure it in `~/.gradle/gradle.properties`:

```properties
gpr.user=your-github-username
gpr.key=ghp_your-personal-access-token
```

Then reference these properties in your `build.gradle.kts`:

```kotlin
repositories {
    mavenCentral()
    maven {
        name = "GitHubPackages"
        url = uri("https://maven.pkg.github.com/plugwerk/plugwerk")
        credentials {
            username = project.findProperty("gpr.user")?.toString()
                ?: providers.environmentVariable("GITHUB_ACTOR").getOrElse("")
            password = project.findProperty("gpr.key")?.toString()
                ?: providers.environmentVariable("GITHUB_TOKEN").getOrElse("")
        }
    }
}
```

### Maven Configuration

For Maven-based consumers, add the repository to `~/.m2/settings.xml`:

```xml
<settings>
  <servers>
    <server>
      <id>github-plugwerk</id>
      <username>your-github-username</username>
      <password>ghp_your-personal-access-token</password>
    </server>
  </servers>
</settings>
```

And in your `pom.xml`:

```xml
<repositories>
  <repository>
    <id>github-plugwerk</id>
    <url>https://maven.pkg.github.com/plugwerk/plugwerk</url>
  </repository>
</repositories>
```

## SNAPSHOT Container Images

A development container image is published to GHCR on every push to `main`:

```bash
docker pull ghcr.io/plugwerk/plugwerk-server:snapshot
```

This image reflects the latest state of `main` and is intended for integration testing across
repositories. It is **not** suitable for production use.

Authentication for pulling from GHCR (if the package is org-internal):

```bash
echo $GITHUB_TOKEN | docker login ghcr.io -u $GITHUB_ACTOR --password-stdin
```

## Release Artifacts

Released versions are published to both external and internal registries:

| Artifact | External Registry | Internal Registry |
|---|---|---|
| Maven JARs/ZIPs | Maven Central | GitHub Packages |
| Container Images | Docker Hub (`plugwerk/plugwerk-server`) | GHCR (`ghcr.io/plugwerk/plugwerk-server`) |

For production use, prefer Maven Central and Docker Hub. GitHub Packages and GHCR mirror the
same artifacts for org-internal convenience.

See [ADR-0017](adrs/0017-dual-registry-publishing-strategy.md) for the full rationale.

## Local SMTP for Email Development (#253)

Plugwerk's transactional email pipeline (`MailService` → `MailSenderProvider`
→ `JavaMailSenderImpl`) needs a real SMTP server to talk to. Two options
for local dev:

### Option A — MailHog in Docker Compose (recommended)

Add to your local `docker-compose.override.yml`:

```yaml
services:
  mailhog:
    image: mailhog/mailhog:latest
    ports:
      - "1025:1025"   # SMTP
      - "8025:8025"   # Web UI
```

Then in the Plugwerk admin UI under **Settings → Email → Server**:

| Field | Value |
|---|---|
| `smtp.enabled` | `true` |
| `smtp.host` | `localhost` (or `mailhog` if your Plugwerk container is on the same compose network) |
| `smtp.port` | `1025` |
| `smtp.encryption` | `none` |
| `smtp.username` | (leave blank) |
| `smtp.password` | (leave blank) |
| `smtp.from_address` | `noreply@plugwerk.local` |

Open <http://localhost:8025> to inspect every message Plugwerk sent.

### Option B — GreenMail in unit tests

`SmtpEmailIT` already wires GreenMail on a dynamic port. Use it as a
template when adding test coverage for new mail-triggering flows. No
Docker setup needed; runs as part of `./gradlew :plugwerk-server:plugwerk-server-backend:integrationTest`.

See [ADR-0031](adrs/0031-email-infrastructure.md) for the full design.
