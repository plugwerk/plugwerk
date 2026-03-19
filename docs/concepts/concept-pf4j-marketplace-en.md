# PlugWerk – Plugin Marketplace for the Java Ecosystem

**Concept Paper | Product Vision, Features, Implementation Outline & Operating Model**

Date: March 19, 2026 | Version 0.1 – Draft

---

## 1. Executive Summary

Java applications built on PF4J (Plugin Framework for Java) lack a centralized infrastructure for distributing, managing, and updating plugins. Every team that integrates PF4J ends up building its own deployment pipelines, update mechanisms, and plugin registries. This pattern repeats across projects and organizations.

**PlugWerk** closes this gap as a standalone product. It consists of two artifacts:

1. **PlugWerk Server** – a web application that serves as a central plugin marketplace: catalog, upload, versioning, download, and REST API.
2. **PlugWerk Client SDK** – a Java library that can be embedded into any Java application to connect to the marketplace: plugin discovery, download, installation, update checking, and lifecycle integration with PF4J.

The product is intentionally **product-agnostic**. Any Java software product that uses or wants to use PF4J can adopt PlugWerk as its plugin infrastructure – comparable to the relationship between Maven Central and Maven, but specialized for runtime plugins rather than build dependencies.

---

## 2. Problem Analysis

### 2.1 Status Quo in the PF4J Ecosystem

PF4J is a proven, lightweight plugin framework (~100 KB, Apache License), used by Netflix Spinnaker and Appsmith among others. The ecosystem includes:

| Module        | Function                                     | Status             |
|---------------|----------------------------------------------|--------------------|
| pf4j          | Core: plugin loading, classloader isolation   | Active, v3.12+     |
| pf4j-spring   | Spring Framework integration                  | Active             |
| pf4j-update   | Update mechanism (JSON-based)                 | Stagnant (2020)    |
| pf4j-plus     | ServiceRegistry, EventBus, Config             | New (Jan 2026)     |
| pf4j-jpms     | Java Module System PoC                        | Experimental       |

**The gap:** None of these modules provides a complete marketplace solution. `pf4j-update` comes closest – it defines a `plugins.json` format and an `UpdateManager` – but it is:

- a pure client with no server component
- limited to static JSON hosting (no upload, no catalog, no search)
- barely maintained since 2020
- lacking security features (no signing, no review process)

### 2.2 Recurring Problems Across Teams

- **No standard for plugin distribution:** Every product builds its own FTP/S3/Nexus-based solutions.
- **No discovery:** Users don't know what plugins exist without reading documentation.
- **No lifecycle management:** Updates, rollbacks, and compatibility checks are handled manually.
- **No trust model:** There is no mechanism to verify the integrity of downloaded plugins.

---

## 3. Product Vision

> **PlugWerk makes plugin management for Java applications as easy as dependency management with Maven – but at runtime.**

### 3.1 Target State

A Java software product integrates the PlugWerk Client SDK (a Maven dependency). From that moment, its end users or administrators can:

- browse a plugin catalog (either through an embedded UI or programmatically)
- install, update, or uninstall plugins with a single click
- be assured that only compatible and verified plugins are offered

Plugin developers (internal or external) can:

- upload their plugins through a web interface or CI/CD pipeline
- maintain metadata (description, screenshots, changelog, compatibility matrix)
- manage releases and withdraw older versions

Product operators can:

- run their own PlugWerk server (self-hosted) or use a hosted service (SaaS)
- create namespaces/channels for their products
- control approval workflows and permissions

### 3.2 Scope Boundaries

PlugWerk is **not**:

- a build tool or dependency manager (not a Maven/Gradle replacement)
- a plugin framework (not a PF4J replacement, but an extension of it)
- an application server or runtime container (not an OSGi replacement)

PlugWerk is **the missing link** between the plugin framework (PF4J) and a product's plugin ecosystem.

---

## 4. Feature Catalog

### 4.1 PlugWerk Server

#### 4.1.1 Plugin Catalog & Discovery

- Searchable directory of all plugins with full-text search
- Filtering by: product/namespace, category/tags, compatibility version, rating, recency
- Detail page per plugin: description, screenshots, changelog, license, author, download count, compatibility matrix
- Catalog available as Web UI and REST API

#### 4.1.2 Plugin Upload & Release Management

- Upload via Web UI or REST API (CI/CD-ready)
- Automatic extraction of plugin metadata from the PF4J manifest (Plugin-Id, Plugin-Version, Plugin-Provider, Plugin-Dependencies)
- Extended manifest format (PlugWerk Descriptor): additional fields such as compatibility range, description, category, license, icon
- Multi-version support: multiple releases per plugin, with status (Draft, Published, Deprecated, Yanked)
- Per-release changelog management

#### 4.1.3 Versioning & Compatibility

- SemVer-based versioning (consistent with pf4j-update)
- Compatibility matrix: plugins declare which host product versions they support (`requires: >=2.0.0 & <3.0.0`)
- Dependency resolution: plugin-to-plugin dependencies are resolved during download/install
- API level concept (optional): host products can declare an API level; plugins reference this instead of concrete product versions

#### 4.1.4 Security & Trust

- **Code signing:** Plugins can be signed with a private key; the client verifies the signature before installation
- **Checksum verification:** SHA-256 hashes for all artifacts, automatic verification in the client
- **Review workflow:** Optionally enabled approval process – plugins must be approved by an admin/reviewer before appearing in the catalog
- **Vulnerability scanning:** Integration with tools like OWASP Dependency-Check or Trivy for analyzing plugin dependencies
- **RBAC (Role-Based Access Control):** Roles such as Admin, Reviewer, Publisher, Consumer with granular permissions

#### 4.1.5 Multi-Tenancy & Namespaces

- **Namespace concept:** Each host product receives its own namespace (e.g., `acme-crm`, `logistics-suite`)
- Plugins are assigned to one or more namespaces
- Namespace-specific settings: compatibility requirements, approval workflow, branding
- Multi-tenancy: a single PlugWerk server can serve multiple products/organizations
- API keys per namespace for authenticated access

#### 4.1.6 REST API

- RESTful API as the primary interface (Web UI uses the same API)
- Endpoints for: catalog queries, plugin details, download, upload, release management, namespace management, user/role management
- Authentication: API key and/or OAuth2/OIDC
- Rate limiting and quota management
- OpenAPI/Swagger documentation

#### 4.1.7 Web UI

- Modern, responsive web frontend
- Plugin catalog with card and list views
- Plugin detail page with installation instructions
- Admin area: upload, release management, review queue, namespace management, user management
- Dashboard: download statistics, active plugins, version distribution

### 4.2 PlugWerk Client SDK

#### 4.2.1 Marketplace Connectivity

- REST client for communication with the PlugWerk Server
- Configurable server URL(s) – including multiple repositories simultaneously (analogous to Maven Central + private repos)
- Authentication via API key or token
- Offline-capable: cached catalog, works even when server is temporarily unreachable

#### 4.2.2 Plugin Discovery

- Programmatic catalog search (by name, tags, compatibility)
- Query available updates for installed plugins
- Query new, not yet installed plugins
- Compatibility filtering: only display plugins matching the current host version

#### 4.2.3 Download & Installation

- Download of plugin artifacts (ZIP/JAR) with checksum verification
- Signature verification (if enabled)
- Automatic dependency resolution: required plugins are downloaded together
- Installation into the configured PF4J plugins directory
- Transactional installation: no half-finished state on failure

#### 4.2.4 Update & Rollback

- Automatic or manual update checking
- Update with version switch: deactivate old plugin → install new → start
- Rollback: retain previous version and restore on demand
- Configurable update policy: automatic, notify-only, manual

#### 4.2.5 Lifecycle Integration

- Seamless integration with the PF4J `PluginManager`
- Extension of the `pf4j-update` `UpdateManager` (backward compatible)
- Events/callbacks: `onInstall`, `onUpdate`, `onUninstall`, `onRollback`
- Health check: verify plugin functionality after installation

#### 4.2.6 Embeddable UI (Optional)

- Swing/JavaFX component or web component (for web applications) for embedding a plugin manager in the host product
- Shows available, installed, and updatable plugins
- Enables installation/update/uninstallation by end users
- Fully configurable and themeable

---

## 5. Plugin Descriptor Format

Beyond the standard PF4J manifest, PlugWerk defines an extended descriptor format:

```yaml
# plugwerk.yml – included in the plugin artifact
plugwerk:
  # Required fields (extending PF4J manifest)
  id: "acme-pdf-export"
  version: "1.2.0"
  name: "PDF Export Plugin"
  description: "Exports reports as PDF with configurable templates."
  author: "ACME GmbH"
  license: "Apache-2.0"

  # Compatibility
  requires:
    system-version: ">=2.0.0 & <4.0.0"    # Host product version
    api-level: 3                            # Optional: API level instead of concrete version
    plugins:                                # Plugin-to-plugin dependencies
      - id: "acme-template-engine"
        version: ">=1.0.0"

  # Catalog metadata
  namespace: "acme-crm"
  categories:
    - "export"
    - "reporting"
  tags:
    - "pdf"
    - "report"
    - "template"
  icon: "icon.png"                          # Relative path within artifact
  screenshots:
    - "screenshot-config.png"
    - "screenshot-output.png"
  homepage: "https://plugins.acme.com/pdf-export"
  repository: "https://github.com/acme/pdf-export-plugin"

  # Security
  min-java-version: "17"
  signed: true
```

The descriptor is backward compatible: if `plugwerk.yml` is missing, the server extracts base information from the PF4J manifest (`MANIFEST.MF` or `plugin.properties`). Extended fields remain empty and can be maintained via the Web UI.

---

## 6. Architecture – Implementation Outline

### 6.1 Technology Stack

| Component             | Technology                                      | Rationale                                                  |
|-----------------------|-------------------------------------------------|------------------------------------------------------------|
| **Server Backend**    | Spring Boot 3.x / Java 21+                      | PF4J ecosystem is Java; Spring Boot is the industry standard|
| **Server API**        | Spring Web (REST) + OpenAPI                      | Industry standard, excellent tooling support                |
| **Server Database**   | PostgreSQL                                       | Robust, JSON support, full-text search                     |
| **Server Storage**    | Filesystem / S3-compatible (MinIO)               | Plugin artifacts as binaries; S3 for scalability           |
| **Server Web UI**     | React / TypeScript or Vaadin                     | React for SaaS variant; Vaadin as Java-only alternative    |
| **Server Auth**       | Spring Security + OIDC/OAuth2                    | Enterprise-ready, Keycloak-compatible                      |
| **Client SDK**        | Pure Java (Java 11+ compatibility)               | Minimal dependencies, maximum portability                  |
| **Client HTTP**       | java.net.http.HttpClient or OkHttp               | No Spring dependency required in the client                |
| **Build**             | Gradle (multi-module project)                    | Modern build tool, well-suited for multi-module            |

### 6.2 System Architecture

```
┌─────────────────────────────────────────────────────────────┐
│                      PlugWerk Server                        │
│                                                             │
│  ┌──────────┐  ┌──────────┐  ┌──────────┐  ┌────────────┐  │
│  │ REST API │  │  Web UI  │  │   Auth   │  │ Admin API  │  │
│  └────┬─────┘  └────┬─────┘  └────┬─────┘  └─────┬──────┘  │
│       │              │             │               │         │
│  ┌────┴──────────────┴─────────────┴───────────────┴──────┐  │
│  │                   Service Layer                        │  │
│  │  ┌────────────┐ ┌──────────┐ ┌───────────┐ ┌────────┐ │  │
│  │  │  Catalog   │ │ Release  │ │ Namespace │ │Security│ │  │
│  │  │  Service   │ │ Service  │ │  Service  │ │Service │ │  │
│  │  └──────┬─────┘ └────┬─────┘ └─────┬─────┘ └───┬────┘ │  │
│  └─────────┼────────────┼─────────────┼────────────┼──────┘  │
│       ┌────┴────┐  ┌────┴────┐   ┌────┴────┐               │
│       │Postgres │  │ Storage │   │  Cache  │               │
│       │ (Meta)  │  │(S3/FS)  │   │(Redis)  │               │
│       └─────────┘  └─────────┘   └─────────┘               │
└─────────────────────────────────────────────────────────────┘
          ▲                    ▲
          │  REST/HTTPS        │  REST/HTTPS
          │                    │
┌─────────┴──────┐    ┌───────┴────────────────────┐
│  Plugin        │    │  Host Application          │
│  Developer     │    │  ┌──────────────────────┐   │
│  (Upload via   │    │  │ PlugWerk Client SDK  │   │
│   API / CI/CD) │    │  │  ┌────────────────┐  │   │
│                │    │  │  │ pf4j            │  │   │
│                │    │  │  │ PluginManager   │  │   │
│                │    │  │  └────────────────┘  │   │
│                │    │  └──────────────────────┘   │
└────────────────┘    └────────────────────────────┘
```

### 6.3 Data Model (Core Entities)

```
Namespace
├── id (UUID)
├── slug (unique, e.g. "acme-crm")
├── display_name
├── description
├── owner_organization_id
├── settings (JSON: review_required, auto_approve, etc.)
└── api_keys[]

Plugin
├── id (UUID)
├── plugin_id (PF4J Plugin-Id, unique per namespace)
├── namespace_id (FK)
├── name
├── description (Markdown)
├── author
├── license
├── icon_url
├── homepage_url
├── repository_url
├── categories[]
├── tags[]
├── created_at
├── updated_at
├── download_count (aggregated)
└── status (active, suspended, archived)

PluginRelease
├── id (UUID)
├── plugin_id (FK)
├── version (SemVer)
├── changelog (Markdown)
├── artifact_path (S3/FS reference)
├── artifact_size_bytes
├── artifact_sha256
├── signature (optional, Base64)
├── requires_system_version (SemVer range)
├── requires_api_level (int, optional)
├── requires_java_version
├── plugin_dependencies (JSON array)
├── status (draft, published, deprecated, yanked)
├── published_at
├── download_count
└── reviewed_by (FK, optional)

User / Organization / Role
├── Standard RBAC model
└── Mapping: User → Organization → Namespace → Role
```

### 6.4 API Design (Core Endpoints Excerpt)

```
# Catalog (public or API-key-protected)
GET    /api/v1/namespaces/{ns}/plugins              # List with filters
GET    /api/v1/namespaces/{ns}/plugins/{pluginId}    # Details
GET    /api/v1/namespaces/{ns}/plugins/{pluginId}/releases  # All releases
GET    /api/v1/namespaces/{ns}/plugins/{pluginId}/releases/{version}
GET    /api/v1/namespaces/{ns}/plugins/{pluginId}/releases/{version}/download

# pf4j-update compatible (drop-in for existing clients)
GET    /api/v1/namespaces/{ns}/plugins.json          # pf4j-update format

# Upload & Management (authenticated)
POST   /api/v1/namespaces/{ns}/plugins               # Create new plugin
POST   /api/v1/namespaces/{ns}/plugins/{pluginId}/releases  # Upload new release
PATCH  /api/v1/namespaces/{ns}/plugins/{pluginId}    # Update metadata
PATCH  /api/v1/namespaces/{ns}/plugins/{pluginId}/releases/{version}  # Change release status

# Update check (for Client SDK)
POST   /api/v1/namespaces/{ns}/updates/check         # Body: installed plugins + versions
                                                      # Response: available updates + new plugins

# Admin
GET    /api/v1/namespaces/{ns}/reviews/pending        # Review queue
POST   /api/v1/namespaces/{ns}/reviews/{releaseId}/approve
POST   /api/v1/namespaces/{ns}/reviews/{releaseId}/reject
```

### 6.5 Client SDK – Architecture

```java
// Core classes
PlugWerkClient                  // HTTP client, configurable
PlugWerkCatalog                 // Catalog queries
PlugWerkInstaller               // Download + installation + verification
PlugWerkUpdateChecker           // Update checking

// PF4J integration
PlugWerkUpdateRepository        // Implements pf4j-update UpdateRepository
                                // → Drop-in replacement for DefaultUpdateRepository
PlugWerkPluginManager           // Optional extended PluginManager
                                // → Wraps DefaultPluginManager + PlugWerk features

// Configuration
PlugWerkConfig                  // Server URL, API key, namespace, cache dir, etc.
```

**Client SDK design principles:**
- No Spring dependency (pure Java 11+)
- Minimal external dependencies (only HTTP client, JSON parser, SLF4J)
- Backward compatible with `pf4j-update` – existing integrations can migrate incrementally
- Thread-safe for parallel downloads
- Configuration via builder pattern or properties file

---

## 7. Implementation Plan (Phases)

### Phase 1 – MVP (estimated: 8–12 weeks)

**Goal:** A functional marketplace that replaces and extends the pf4j-update workflow.

**Server:**
- REST API for plugin upload, catalog queries, download
- PostgreSQL database with core entities
- Filesystem-based artifact storage
- API key authentication (simple)
- Minimal Web UI: plugin list, plugin detail, upload form
- `plugins.json` endpoint (pf4j-update compatible)
- Docker-based deployment

**Client SDK:**
- `PlugWerkUpdateRepository` as drop-in for `pf4j-update`
- Catalog queries, download with SHA-256 verification
- Update checking
- Properties-based configuration

**Descriptor:**
- `plugwerk.yml` with required fields
- Fallback to PF4J manifest

### Phase 2 – Enterprise Features (estimated: 6–10 weeks)

- Multi-namespace support
- RBAC with OIDC/OAuth2 (Keycloak integration)
- Review/approval workflow
- Code signing and signature verification
- Extended Web UI: dashboard, statistics, admin area
- S3-compatible storage (MinIO)
- Dependency resolution in the client

### Phase 3 – Ecosystem & Scaling (ongoing)

- Embeddable UI component (Web Component / Vaadin)
- Plugin ratings and comments
- Webhook integration (notifications on new releases)
- Vulnerability scanning of artifacts
- Gradle/Maven plugin for CI/CD uploads
- SaaS hosting option (multi-tenant with billing)
- CLI tool for plugin developers
- Plugin sandbox/test environment

---

## 8. Operating Model

### 8.1 Deployment Variants

#### Variant A: Self-Hosted (Open Core)

The customer operates the PlugWerk server in their own infrastructure.

- **Deployment:** Docker Compose (single server) or Kubernetes (Helm chart)
- **Minimum infrastructure:** 1 server with Docker, PostgreSQL, ~50 GB storage
- **Scaling:** Horizontal via Kubernetes (stateless app servers, shared DB + S3)
- **Target audience:** Companies with their own data centers, data privacy requirements, on-premise mandates

```
Docker Compose (Minimal):
├── plugwerk-server  (Spring Boot app)
├── postgres         (database)
├── minio            (optional, S3 storage)
└── nginx            (reverse proxy, TLS)
```

#### Variant B: SaaS / Managed Service

devtank42 operates the PlugWerk server as a managed service.

- **Deployment:** Multi-tenant on Kubernetes
- **Isolation:** Namespace-based (logical separation) or dedicated instances (enterprise tier)
- **Target audience:** Startups, SaaS products, teams without their own infrastructure

### 8.2 Licensing Model

| Tier | Scope | Price |
|------|-------|-------|
| **Community** | Open Source (Apache 2.0), self-hosted, 1 namespace, unlimited plugins, community support | Free |
| **Professional** | Self-hosted, multi-namespace, RBAC, review workflow, code signing, email support | Subscription (per server) |
| **Enterprise** | Self-hosted or managed, vulnerability scanning, SSO/OIDC, SLA, dedicated support | Subscription (by arrangement) |
| **SaaS** | Managed service, pay-per-namespace or flat rate, all features included | Monthly (per namespace + download volume) |

**Open core strategy:** The core (API, catalog, Client SDK, base UI) is open source. Enterprise features (RBAC, signing, scanning, multi-tenancy) are proprietary add-ons.

### 8.3 Support & Maintenance

- **Community:** GitHub Issues, Discussions, community forum
- **Professional:** Email support, guaranteed response time (48h)
- **Enterprise:** Dedicated support, SLA (4h response), onboarding, custom development
- **Updates:** Regular releases (monthly patch, quarterly minor), LTS versions (annually)

### 8.4 Monitoring & Operations

- Health endpoints (`/actuator/health`) for Kubernetes probes
- Prometheus metrics: API latency, download throughput, storage utilization, active namespaces
- Structured logging (JSON) for ELK/Grafana Loki
- Backup strategy: PostgreSQL dumps + S3 bucket replication
- Disaster recovery: RTO < 4h, RPO < 1h (enterprise tier)

---

## 9. Competitive Analysis

| Solution | Comparison to PlugWerk | Strength | Weakness for Our Use Case |
|----------|----------------------|----------|--------------------------|
| **pf4j-update** | Direct predecessor; PlugWerk extends this approach | Lightweight, pf4j-native | No server, no catalog, stagnant |
| **Eclipse p2** | Similar concept (update sites + client) | Proven, powerful | Tied to OSGi/Eclipse, extremely complex |
| **JetBrains Marketplace** | Reference model for UX | Excellent UX, compatibility matrix | Proprietary, JetBrains IDEs only |
| **Atlassian Marketplace** | Reference for commercial model | Monetization, reviews, vendor mgmt | Proprietary, Atlassian products only |
| **Gradle Plugin Portal** | Similar for build plugins | Discovery, versioning | Gradle plugins only, not runtime |
| **Maven Central / Nexus** | Artifact hosting | Universal, proven | No plugin lifecycle, no compatibility matrix |
| **OSGi (Felix, Karaf)** | Technically related | Hot-deploy, isolation, dependency mgmt | Complex stack, no marketplace aspect |

**Positioning:** PlugWerk is the **only generic, product-agnostic plugin marketplace for the PF4J ecosystem**. It combines the simplicity of pf4j-update with the functionality of a full-featured marketplace.

---

## 10. Risks & Mitigation Strategies

| Risk | Probability | Impact | Mitigation |
|------|-------------|--------|------------|
| PF4J development stagnates or is discontinued | Medium | High | PlugWerk abstracts the PF4J dependency in the Client SDK; a custom PluginManager can be implemented if needed. Additionally, PF4J is Apache-licensed and can be forked. |
| Low adoption due to lack of visibility | High | High | Open source core for reach, seek integration into the pf4j ecosystem (official pf4j project?), talks/blog posts in the Java community |
| Security vulnerabilities in hosted plugins | Medium | High | Vulnerability scanning, review process, code signing; liability disclaimer in terms of service |
| Complexity of the compatibility model | Medium | Medium | MVP starts with simple SemVer range; API level concept deferred to Phase 2 |
| Competition from a PF4J-native solution | Low | High | Establish early contact with PF4J maintainer (Decebal Suiu); ideally position PlugWerk as an official ecosystem project |

---

## 11. Project Name & Branding

**Name: PlugWerk**

The name combines "Plug" (plugin) with the German word "Werk" (works/factory/opus), reflecting both the product's German origin and the concept of a manufacturing hub for plugins. It is internationally pronounceable and carries connotations of craftsmanship and reliability.

Tasks before launch:
- Domain availability check and registration
- Trademark clearance
- Logo and visual identity design
- Landing page

---

## 12. Next Steps

1. **Feedback on this concept** – alignment on scope, prioritization, naming
2. **Technical PoC** – MVP server with REST API + Client SDK as drop-in for pf4j-update (2–3 weeks)
3. **Repository setup** – Gradle multi-module project, CI/CD pipeline, contribution guidelines
4. **Community validation** – present concept in PF4J GitHub Discussions, gather community feedback
5. **Domain & branding** – finalize domain, secure it, create minimal landing page

---

*This document is a living draft. Changes and additions will be tracked by version.*
