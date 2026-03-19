# PlugWerk – Plugin Marketplace für das Java-Ökosystem

**Konzept-Paper | Produktvision, Features, Umsetzungsskizze & Betriebsmodell**

Stand: 19. März 2026 | Version 0.1 – Entwurf

---

## 1. Executive Summary

Java-Anwendungen, die auf PF4J (Plugin Framework for Java) setzen, fehlt eine zentrale Infrastruktur für die Distribution, Verwaltung und Aktualisierung von Plugins. Jedes Team, das PF4J integriert, baut sich eigene Deployment-Pipelines, eigene Update-Mechanismen und eigene Plugin-Verzeichnisse. Dieses Muster wiederholt sich projekt- und unternehmensübergreifend.

**PlugWerk** schließt diese Lücke als eigenständiges Produkt. Es besteht aus zwei Artefakten:

1. **PlugWerk Server** – eine Webanwendung, die als zentraler Plugin-Marketplace fungiert: Katalog, Upload, Versionierung, Download und REST-API.
2. **PlugWerk Client SDK** – eine Java-Bibliothek, die in beliebige Java-Anwendungen eingebunden wird und die Verbindung zum Marketplace herstellt: Plugin-Discovery, Download, Installation, Update-Prüfung und Lifecycle-Integration mit PF4J.

Das Produkt ist bewusst **produktunabhängig** konzipiert. Jedes Java-Softwareprodukt, das PF4J nutzt oder nutzen möchte, kann PlugWerk als seine Plugin-Infrastruktur einsetzen – vergleichbar mit dem Verhältnis von Maven Central zu Maven, aber spezialisiert auf Runtime-Plugins statt Build-Dependencies.

---

## 2. Problemanalyse

### 2.1 Status Quo im PF4J-Ökosystem

PF4J ist ein bewährtes, leichtgewichtiges Plugin-Framework (~100 KB, Apache License), das unter anderem von Netflix Spinnaker und Appsmith eingesetzt wird. Das Ökosystem umfasst:

| Modul         | Funktion                                    | Status             |
|---------------|---------------------------------------------|--------------------|
| pf4j          | Core: Plugin-Loading, Classloader-Isolation  | Aktiv, v3.12+      |
| pf4j-spring   | Spring-Framework-Integration                 | Aktiv              |
| pf4j-update   | Update-Mechanismus (JSON-basiert)            | Stagniert (2020)   |
| pf4j-plus     | ServiceRegistry, EventBus, Config            | Neu (Jan 2026)     |
| pf4j-jpms     | Java Module System PoC                       | Experimentell      |

**Die Lücke:** Keines dieser Module bietet eine vollständige Marketplace-Lösung. `pf4j-update` kommt am nächsten – es definiert ein `plugins.json`-Format und einen `UpdateManager` – aber es ist:

- ein reiner Client ohne Server-Komponente
- auf statisches JSON-Hosting beschränkt (kein Upload, kein Katalog, keine Suche)
- seit 2020 kaum weiterentwickelt
- ohne Sicherheitsfeatures (kein Signing, kein Review-Prozess)

### 2.2 Wiederkehrende Probleme bei Teams

- **Kein Standard für Plugin-Distribution:** Jedes Produkt baut eigene FTP/S3/Nexus-basierte Lösungen.
- **Keine Discovery:** Nutzer wissen nicht, welche Plugins es gibt, ohne Dokumentation zu lesen.
- **Kein Lifecycle-Management:** Updates, Rollbacks und Kompatibilitätsprüfungen werden manuell gelöst.
- **Kein Vertrauensmodell:** Es gibt keinen Mechanismus, um die Integrität heruntergeladener Plugins zu verifizieren.

---

## 3. Produktvision

> **PlugWerk macht Plugin-Management für Java-Anwendungen so einfach wie Dependency-Management mit Maven – aber zur Laufzeit.**

### 3.1 Zielbild

Ein Java-Softwareprodukt integriert das PlugWerk Client SDK (eine Maven-Dependency). Ab diesem Moment können seine Endnutzer oder Administratoren:

- einen Plugin-Katalog durchsuchen (entweder über ein eingebettetes UI oder programmatisch)
- Plugins mit einem Klick installieren, updaten oder deinstallieren
- sicher sein, dass nur kompatible und verifizierte Plugins angeboten werden

Plugin-Entwickler (intern oder extern) können:

- ihre Plugins über eine Web-Oberfläche oder CI/CD-Pipeline hochladen
- Metadaten pflegen (Beschreibung, Screenshots, Changelog, Kompatibilitätsmatrix)
- Releases verwalten und ältere Versionen zurückziehen

Produktbetreiber können:

- ihren eigenen PlugWerk-Server betreiben (Self-Hosted) oder einen gehosteten Service nutzen (SaaS)
- Namespaces/Kanäle für ihre Produkte anlegen
- Freigabeworkflows und Berechtigungen steuern

### 3.2 Abgrenzung

PlugWerk ist **kein**:

- Build-Tool oder Dependency-Manager (kein Maven-/Gradle-Ersatz)
- Plugin-Framework (kein PF4J-Ersatz, sondern eine Erweiterung davon)
- Application Server oder Runtime-Container (kein OSGi-Ersatz)

PlugWerk ist **das fehlende Bindeglied** zwischen dem Plugin-Framework (PF4J) und dem Plugin-Ökosystem eines Produkts.

---

## 4. Feature-Katalog

### 4.1 PlugWerk Server

#### 4.1.1 Plugin-Katalog & Discovery

- Durchsuchbares Verzeichnis aller Plugins mit Volltextsuche
- Filterung nach: Produkt/Namespace, Kategorie/Tags, Kompatibilitätsversion, Bewertung, Aktualität
- Detailseite pro Plugin: Beschreibung, Screenshots, Changelog, Lizenz, Autor, Download-Zahlen, Kompatibilitätsmatrix
- Katalog verfügbar als Web-UI und als REST-API

#### 4.1.2 Plugin-Upload & Release-Management

- Upload über Web-UI oder REST-API (CI/CD-tauglich)
- Automatische Extraktion von Plugin-Metadaten aus dem PF4J-Manifest (Plugin-Id, Plugin-Version, Plugin-Provider, Plugin-Dependencies)
- Erweitertes Manifest-Format (PlugWerk Descriptor): zusätzliche Felder wie Kompatibilitätsbereich, Beschreibung, Kategorie, Lizenz, Icon
- Multi-Version-Support: mehrere Releases pro Plugin, mit Status (Draft, Published, Deprecated, Yanked)
- Changelog-Pflege pro Release

#### 4.1.3 Versionierung & Kompatibilität

- SemVer-basierte Versionierung (konsistent mit pf4j-update)
- Kompatibilitätsmatrix: Plugin deklariert, mit welchen Versionen des Host-Produkts es kompatibel ist (`requires: >=2.0.0 & <3.0.0`)
- Dependency-Resolution: Plugin-zu-Plugin-Abhängigkeiten werden beim Download/Install aufgelöst
- API-Level-Konzept (optional): Host-Produkte können ein API-Level deklarieren, Plugins referenzieren dieses statt konkreter Produktversionen

#### 4.1.4 Sicherheit & Trust

- **Code-Signing:** Plugins können mit einem privaten Schlüssel signiert werden; der Client verifiziert die Signatur vor Installation
- **Checksum-Verifikation:** SHA-256-Hashes für alle Artefakte, automatische Prüfung im Client
- **Review-Workflow:** Optional aktivierbarer Freigabeprozess – Plugins müssen von einem Admin/Reviewer genehmigt werden, bevor sie im Katalog sichtbar sind
- **Vulnerability-Scanning:** Integration mit Tools wie OWASP Dependency-Check oder Trivy für die Analyse der Plugin-Dependencies
- **RBAC (Role-Based Access Control):** Rollen wie Admin, Reviewer, Publisher, Consumer mit granularen Berechtigungen

#### 4.1.5 Multi-Tenancy & Namespaces

- **Namespace-Konzept:** Jedes Host-Produkt erhält einen eigenen Namespace (z.B. `acme-crm`, `logistics-suite`)
- Plugins sind einem oder mehreren Namespaces zugeordnet
- Namespace-spezifische Einstellungen: Kompatibilitätsvorgaben, Freigabeworkflow, Branding
- Mandantenfähigkeit: ein PlugWerk-Server kann mehrere Produkte/Organisationen bedienen
- API-Keys pro Namespace für authentifizierten Zugriff

#### 4.1.6 REST-API

- RESTful API als primäre Schnittstelle (Web-UI nutzt dieselbe API)
- Endpunkte für: Katalog-Abfrage, Plugin-Details, Download, Upload, Release-Management, Namespace-Verwaltung, User/Rollen-Management
- Authentifizierung: API-Key und/oder OAuth2/OIDC
- Rate-Limiting und Quota-Management
- OpenAPI/Swagger-Dokumentation

#### 4.1.7 Web-UI

- Modernes, responsives Web-Frontend
- Plugin-Katalog mit Karten- und Listenansicht
- Plugin-Detailseite mit Installationshinweisen
- Admin-Bereich: Upload, Release-Management, Review-Queue, Namespace-Verwaltung, User-Management
- Dashboard: Download-Statistiken, aktive Plugins, Versionsverteilung

### 4.2 PlugWerk Client SDK

#### 4.2.1 Marketplace-Connectivity

- REST-Client zur Kommunikation mit dem PlugWerk Server
- Konfigurierbare Server-URL(s) – auch mehrere Repositories gleichzeitig (analog Maven Central + private Repos)
- Authentifizierung per API-Key oder Token
- Offline-fähig: gecachter Katalog, funktioniert auch bei temporärer Server-Unerreichbarkeit

#### 4.2.2 Plugin-Discovery

- Programmatische Suche im Katalog (nach Name, Tags, Kompatibilität)
- Abfrage verfügbarer Updates für installierte Plugins
- Abfrage neuer, noch nicht installierter Plugins
- Kompatibilitätsfilterung: nur Plugins anzeigen, die zur aktuellen Host-Version passen

#### 4.2.3 Download & Installation

- Download von Plugin-Artefakten (ZIP/JAR) mit Checksum-Verifikation
- Signatur-Verifikation (wenn aktiviert)
- Automatische Dependency-Resolution: benötigte Plugins werden mitgeladen
- Installation in das konfigurierte PF4J-Plugins-Verzeichnis
- Transaktionale Installation: bei Fehler kein halbfertiger Zustand

#### 4.2.4 Update & Rollback

- Automatische oder manuelle Update-Prüfung
- Update mit Versionswechsel: altes Plugin deaktivieren → neues installieren → starten
- Rollback: vorherige Version beibehalten und bei Bedarf wiederherstellen
- Update-Policy konfigurierbar: automatisch, nur benachrichtigen, manuell

#### 4.2.5 Lifecycle-Integration

- Nahtlose Integration mit dem PF4J `PluginManager`
- Erweiterung des `pf4j-update` `UpdateManager` (Rückwärtskompatibilität)
- Events/Callbacks: `onInstall`, `onUpdate`, `onUninstall`, `onRollback`
- Health-Check: Plugin nach Installation auf Lauffähigkeit prüfen

#### 4.2.6 Einbettbares UI (Optional)

- Swing/JavaFX-Komponente oder Webkomponente (für Web-Anwendungen) zur Einbettung eines Plugin-Managers in das Host-Produkt
- Zeigt verfügbare, installierte und update-fähige Plugins
- Ermöglicht Installation/Update/Deinstallation durch den Endnutzer
- Vollständig konfigurierbar und thembar

---

## 5. Plugin Descriptor Format

Über das Standard-PF4J-Manifest hinaus definiert PlugWerk ein erweitertes Descriptor-Format:

```yaml
# plugwerk.yml – liegt im Plugin-Artefakt
plugwerk:
  # Pflichtfelder (erweitern PF4J-Manifest)
  id: "acme-pdf-export"
  version: "1.2.0"
  name: "PDF Export Plugin"
  description: "Exportiert Berichte als PDF mit konfigurierbaren Templates."
  author: "ACME GmbH"
  license: "Apache-2.0"

  # Kompatibilität
  requires:
    system-version: ">=2.0.0 & <4.0.0"    # Host-Produkt-Version
    api-level: 3                            # Optional: API-Level statt konkreter Version
    plugins:                                # Plugin-zu-Plugin-Dependencies
      - id: "acme-template-engine"
        version: ">=1.0.0"

  # Katalog-Metadaten
  namespace: "acme-crm"
  categories:
    - "export"
    - "reporting"
  tags:
    - "pdf"
    - "report"
    - "template"
  icon: "icon.png"                          # Relativ im Artefakt
  screenshots:
    - "screenshot-config.png"
    - "screenshot-output.png"
  homepage: "https://plugins.acme.com/pdf-export"
  repository: "https://github.com/acme/pdf-export-plugin"

  # Sicherheit
  min-java-version: "17"
  signed: true
```

Der Descriptor ist abwärtskompatibel: Fehlt die `plugwerk.yml`, extrahiert der Server die Basisinformationen aus dem PF4J-Manifest (`MANIFEST.MF` oder `plugin.properties`). Die erweiterten Felder sind dann leer und können über die Web-UI nachgepflegt werden.

---

## 6. Architektur – Grobe Umsetzungsskizze

### 6.1 Technologie-Stack

| Komponente            | Technologie                                     | Begründung                                                |
|-----------------------|-------------------------------------------------|-----------------------------------------------------------|
| **Server Backend**    | Spring Boot 3.x / Java 21+                      | PF4J-Ökosystem ist Java; Spring Boot ist Standard          |
| **Server API**        | Spring Web (REST) + OpenAPI                      | Branchenstandard, gute Tooling-Unterstützung               |
| **Server Datenbank**  | PostgreSQL                                       | Robust, JSON-Support, Volltextsuche                        |
| **Server Storage**    | Filesystem / S3-kompatibel (MinIO)               | Plugin-Artefakte als Binaries; S3 für Skalierung           |
| **Server Web-UI**     | React / TypeScript oder Vaadin                   | React für SaaS-Variante; Vaadin als Java-only-Alternative  |
| **Server Auth**       | Spring Security + OIDC/OAuth2                    | Enterprise-tauglich, Keycloak-kompatibel                   |
| **Client SDK**        | Reines Java (Java 11+ Kompatibilität)            | Minimale Dependencies, maximale Einsetzbarkeit             |
| **Client HTTP**       | java.net.http.HttpClient oder OkHttp             | Kein Spring-Zwang im Client                                |
| **Build**             | Gradle (Multi-Module-Projekt)                    | Modernes Build-Tool, gut für Multi-Modul                   |

### 6.2 Systemarchitektur

```
┌─────────────────────────────────────────────────────────────┐
│                      PlugWerk Server                         │
│                                                             │
│  ┌──────────┐  ┌──────────┐  ┌──────────┐  ┌────────────┐  │
│  │ REST-API │  │  Web-UI  │  │  Auth    │  │  Admin-API  │  │
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
│       │  (Meta) │  │(S3/FS)  │   │(Redis)  │               │
│       └─────────┘  └─────────┘   └─────────┘               │
└─────────────────────────────────────────────────────────────┘
          ▲                    ▲
          │  REST/HTTPS        │  REST/HTTPS
          │                    │
┌─────────┴──────┐    ┌───────┴────────────────────┐
│  Plugin-       │    │  Host-Anwendung            │
│  Entwickler    │    │  ┌──────────────────────┐   │
│  (Upload via   │    │  │ PlugWerk Client SDK   │   │
│   API / CI/CD) │    │  │  ┌────────────────┐  │   │
│                │    │  │  │ pf4j            │  │   │
│                │    │  │  │ PluginManager   │  │   │
│                │    │  │  └────────────────┘  │   │
│                │    │  └──────────────────────┘   │
└────────────────┘    └────────────────────────────┘
```

### 6.3 Datenmodell (Kern-Entitäten)

```
Namespace
├── id (UUID)
├── slug (unique, z.B. "acme-crm")
├── display_name
├── description
├── owner_organization_id
├── settings (JSON: review_required, auto_approve, etc.)
└── api_keys[]

Plugin
├── id (UUID)
├── plugin_id (PF4J Plugin-Id, unique pro Namespace)
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
├── download_count (aggregiert)
└── status (active, suspended, archived)

PluginRelease
├── id (UUID)
├── plugin_id (FK)
├── version (SemVer)
├── changelog (Markdown)
├── artifact_path (S3/FS-Referenz)
├── artifact_size_bytes
├── artifact_sha256
├── signature (optional, Base64)
├── requires_system_version (SemVer-Range)
├── requires_api_level (int, optional)
├── requires_java_version
├── plugin_dependencies (JSON Array)
├── status (draft, published, deprecated, yanked)
├── published_at
├── download_count
└── reviewed_by (FK, optional)

User / Organization / Role
├── Standard RBAC-Modell
└── Zuordnung: User → Organization → Namespace → Role
```

### 6.4 API-Design (Auszug Kern-Endpunkte)

```
# Katalog (öffentlich oder API-Key-geschützt)
GET    /api/v1/namespaces/{ns}/plugins              # Liste mit Filtern
GET    /api/v1/namespaces/{ns}/plugins/{pluginId}    # Details
GET    /api/v1/namespaces/{ns}/plugins/{pluginId}/releases  # Alle Releases
GET    /api/v1/namespaces/{ns}/plugins/{pluginId}/releases/{version}
GET    /api/v1/namespaces/{ns}/plugins/{pluginId}/releases/{version}/download

# pf4j-update-kompatibel (Drop-in für bestehende Clients)
GET    /api/v1/namespaces/{ns}/plugins.json          # pf4j-update-Format

# Upload & Management (authentifiziert)
POST   /api/v1/namespaces/{ns}/plugins               # Neues Plugin anlegen
POST   /api/v1/namespaces/{ns}/plugins/{pluginId}/releases  # Neuen Release hochladen
PATCH  /api/v1/namespaces/{ns}/plugins/{pluginId}    # Metadaten aktualisieren
PATCH  /api/v1/namespaces/{ns}/plugins/{pluginId}/releases/{version}  # Release-Status ändern

# Update-Check (für Client SDK)
POST   /api/v1/namespaces/{ns}/updates/check         # Body: installierte Plugins + Versionen
                                                      # Response: verfügbare Updates + neue Plugins

# Admin
GET    /api/v1/namespaces/{ns}/reviews/pending        # Review-Queue
POST   /api/v1/namespaces/{ns}/reviews/{releaseId}/approve
POST   /api/v1/namespaces/{ns}/reviews/{releaseId}/reject
```

### 6.5 Client SDK – Architektur

```java
// Kernklassen
PlugWerkClient                  // HTTP-Client, konfigurierbar
PlugWerkCatalog                 // Katalog-Abfragen
PlugWerkInstaller               // Download + Installation + Verifikation
PlugWerkUpdateChecker           // Update-Prüfung

// PF4J-Integration
PlugWerkUpdateRepository        // Implementiert pf4j-update UpdateRepository
                               // → Drop-in-Ersatz für DefaultUpdateRepository
PlugWerkPluginManager           // Optionaler erweiterter PluginManager
                               // → Wraps DefaultPluginManager + PlugWerk-Features

// Konfiguration
PlugWerkConfig                  // Server-URL, API-Key, Namespace, Cache-Dir, etc.
```

**Designprinzipien des Client SDK:**
- Keine Spring-Abhängigkeit (reines Java 11+)
- Minimale externe Dependencies (nur HTTP-Client, JSON-Parser, SLF4J)
- Rückwärtskompatibel mit `pf4j-update` – bestehende Integrationen können schrittweise migrieren
- Thread-safe für parallele Downloads
- Konfiguration via Builder-Pattern oder Properties-File

---

## 7. Umsetzungsplan (Phasen)

### Phase 1 – MVP (geschätzt: 8–12 Wochen)

**Ziel:** Ein funktionsfähiger Marketplace, der den pf4j-update-Workflow ersetzt und erweitert.

**Server:**
- REST-API für Plugin-Upload, Katalog-Abfrage, Download
- PostgreSQL-Datenbank mit Kern-Entitäten
- Filesystem-basierter Artefakt-Storage
- API-Key-Authentifizierung (einfach)
- Minimales Web-UI: Plugin-Liste, Plugin-Detail, Upload-Formular
- `plugins.json`-Endpunkt (pf4j-update-kompatibel)
- Docker-basiertes Deployment

**Client SDK:**
- `PlugWerkUpdateRepository` als Drop-in für `pf4j-update`
- Katalog-Abfrage, Download mit SHA-256-Verifikation
- Update-Check
- Properties-basierte Konfiguration

**Descriptor:**
- `plugwerk.yml` mit Pflichtfeldern
- Fallback auf PF4J-Manifest

### Phase 2 – Enterprise Features (geschätzt: 6–10 Wochen)

- Multi-Namespace-Support
- RBAC mit OIDC/OAuth2 (Keycloak-Integration)
- Review-/Approval-Workflow
- Code-Signing und Signatur-Verifikation
- Erweitertes Web-UI: Dashboard, Statistiken, Admin-Bereich
- S3-kompatibler Storage (MinIO)
- Dependency-Resolution im Client

### Phase 3 – Ökosystem & Skalierung (fortlaufend)

- Einbettbare UI-Komponente (Web Component / Vaadin)
- Plugin-Bewertungen und Kommentare
- Webhook-Integration (Benachrichtigungen bei neuen Releases)
- Vulnerability-Scanning der Artefakte
- Gradle/Maven-Plugin für CI/CD-Upload
- SaaS-Hosting-Option (Multi-Tenant mit Billing)
- CLI-Tool für Plugin-Entwickler
- Plugin-Sandbox/Testumgebung

---

## 8. Betriebsmodell

### 8.1 Deployment-Varianten

#### Variante A: Self-Hosted (Open Core)

Der Kunde betreibt den PlugWerk-Server in seiner eigenen Infrastruktur.

- **Deployment:** Docker Compose (Einzelserver) oder Kubernetes (Helm Chart)
- **Minimale Infrastruktur:** 1 Server mit Docker, PostgreSQL, ~50 GB Storage
- **Skalierung:** Horizontal über Kubernetes (stateless App-Server, shared DB + S3)
- **Zielgruppe:** Unternehmen mit eigenen Rechenzentren, Datenschutzanforderungen, On-Premise-Pflicht

```
Docker Compose (Minimal):
├── plugwerk-server  (Spring Boot App)
├── postgres        (Datenbank)
├── minio           (optional, S3-Storage)
└── nginx           (Reverse Proxy, TLS)
```

#### Variante B: SaaS / Managed Service

devtank42 betreibt den PlugWerk-Server als Managed Service.

- **Deployment:** Multi-Tenant auf Kubernetes
- **Isolation:** Namespace-basiert (logische Trennung) oder dedizierte Instanzen (Enterprise-Tier)
- **Zielgruppe:** Startups, SaaS-Produkte, Teams ohne eigene Infrastruktur

### 8.2 Lizenzmodell

| Tier | Umfang | Preis |
|------|--------|-------|
| **Community** | Open Source (Apache 2.0), Self-Hosted, 1 Namespace, unbegrenzte Plugins, Community-Support | Kostenlos |
| **Professional** | Self-Hosted, Multi-Namespace, RBAC, Review-Workflow, Code-Signing, E-Mail-Support | Subscription (pro Server) |
| **Enterprise** | Self-Hosted oder Managed, Vulnerability-Scanning, SSO/OIDC, SLA, Dedicated Support | Subscription (nach Vereinbarung) |
| **SaaS** | Managed Service, Pay-per-Namespace oder Flatrate, alles inklusive | Monatlich (pro Namespace + Download-Volumen) |

**Open-Core-Strategie:** Der Kern (API, Katalog, Client SDK, Basis-UI) ist Open Source. Enterprise-Features (RBAC, Signing, Scanning, Multi-Tenancy) sind proprietäre Add-ons.

### 8.3 Support & Maintenance

- **Community:** GitHub Issues, Discussions, Community-Forum
- **Professional:** E-Mail-Support, garantierte Reaktionszeit (48h)
- **Enterprise:** Dedicated Support, SLA (4h Response), Onboarding, Custom Development
- **Updates:** Regelmäßige Releases (monatlich Patch, quartalsweise Minor), LTS-Versionen (jährlich)

### 8.4 Monitoring & Betrieb

- Health-Endpoints (`/actuator/health`) für Kubernetes Probes
- Prometheus-Metriken: API-Latenz, Download-Throughput, Storage-Auslastung, Active Namespaces
- Structured Logging (JSON) für ELK/Grafana Loki
- Backup-Strategie: PostgreSQL-Dumps + S3-Bucket-Replikation
- Disaster Recovery: RTO < 4h, RPO < 1h (Enterprise-Tier)

---

## 9. Wettbewerbsanalyse

| Lösung | Vergleich zu PlugWerk | Stärke | Schwäche für unseren Use Case |
|--------|---------------------|--------|------------------------------|
| **pf4j-update** | Direkter Vorläufer; PlugWerk erweitert diesen Ansatz | Leichtgewichtig, pf4j-nativ | Kein Server, kein Katalog, stagniert |
| **Eclipse p2** | Ähnliches Konzept (Update-Sites + Client) | Bewährt, mächtig | An OSGi/Eclipse gebunden, extrem komplex |
| **JetBrains Marketplace** | Referenzmodell für UX | Exzellente UX, Kompatibilitätsmatrix | Proprietär, nur für JetBrains-IDEs |
| **Atlassian Marketplace** | Referenz für kommerzielles Modell | Monetarisierung, Reviews, Vendor-Mgmt | Proprietär, nur für Atlassian-Produkte |
| **Gradle Plugin Portal** | Ähnlich für Build-Plugins | Discovery, Versionierung | Nur Gradle-Plugins, nicht Runtime |
| **Maven Central / Nexus** | Artefakt-Hosting | Universell, bewährt | Kein Plugin-Lifecycle, keine Kompatibilitätsmatrix |
| **OSGi (Felix, Karaf)** | Technisch verwandt | Hot-Deploy, Isolation, Dependency-Mgmt | Komplexer Stack, kein Marketplace-Aspekt |

**Positionierung:** PlugWerk ist der **einzige generische, produktunabhängige Plugin-Marketplace für das PF4J-Ökosystem**. Es kombiniert die Einfachheit von pf4j-update mit der Funktionalität eines vollwertigen Marketplaces.

---

## 10. Risiken & Mitigationsstrategien

| Risiko | Eintrittswahrscheinlichkeit | Auswirkung | Mitigation |
|--------|-----------------------------|------------|------------|
| PF4J-Entwicklung stagniert oder wird eingestellt | Mittel | Hoch | PlugWerk abstrahiert die PF4J-Abhängigkeit im Client SDK; bei Bedarf kann ein eigener PluginManager implementiert werden. Zudem: PF4J ist Apache-lizenziert und kann geforkt werden. |
| Geringe Adoption mangels Bekanntheit | Hoch | Hoch | Open-Source-Core für Reichweite, Integration in pf4j-Ökosystem anstreben (offizielles pf4j-Projekt?), Talks/Blog-Posts in der Java-Community |
| Sicherheitslücken in gehosteten Plugins | Mittel | Hoch | Vulnerability-Scanning, Review-Prozess, Code-Signing; Haftungsausschluss im Nutzungsvertrag |
| Komplexität des Kompatibilitätsmodells | Mittel | Mittel | MVP startet mit einfacher SemVer-Range; API-Level-Konzept erst in Phase 2 |
| Konkurrenz durch PF4J-eigene Lösung | Niedrig | Hoch | Frühzeitig Kontakt zum PF4J-Maintainer (Decebal Suiu) suchen; idealerweise PlugWerk als offizielles Ökosystem-Projekt positionieren |

---

## 11. Projektname & Branding

**Name: PlugWerk**

Der Name verbindet „Plug" (Plugin) mit dem deutschen Wort „Werk" (Fabrik/Opus) und spiegelt sowohl den deutschen Ursprung des Produkts als auch das Konzept eines Fertigungszentrums für Plugins wider. Er ist international aussprechbar und transportiert Assoziationen von Handwerkskunst und Zuverlässigkeit.

Aufgaben vor dem Launch:
- Domainverfügbarkeit prüfen und registrieren
- Markenrechtliche Prüfung
- Logo- und Visual-Identity-Design
- Landingpage

---

## 12. Nächste Schritte

1. **Feedback zu diesem Konzept** – Abstimmung zu Scope, Priorisierung, Namensgebung
2. **Technischer PoC** – MVP-Server mit REST-API + Client SDK als Drop-in für pf4j-update (2–3 Wochen)
3. **Repository-Setup** – Gradle Multi-Module-Projekt, CI/CD-Pipeline, Contribution-Guidelines
4. **Community-Validierung** – Konzept im PF4J GitHub Discussions vorstellen, Feedback der Community einholen
5. **Domain & Branding** – Name finalisieren, Domain sichern, minimale Landingpage

---

*Dieses Dokument ist ein lebendiger Entwurf. Änderungen und Ergänzungen werden versioniert nachgeführt.*
