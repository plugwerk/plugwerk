# ADR-0007: Artifact Storage — Filesystem (Phase 1), S3-Compatible (Phase 2)

## Status

Accepted

## Context

Uploaded plugin JARs must be stored and retrieved reliably. Options considered:

1. **Filesystem** — simplest, no additional services, works in Docker Compose
2. **S3-compatible object storage** (AWS S3, MinIO, Garage) — scalable, cloud-native, HA-ready
3. **Database BLOB** — easy to back up with the DB, but poor performance for large files and no streaming support

The MVP targets single-node self-hosted deployments. Scalability and high availability are Phase 2 concerns.

## Decision

**Phase 1 (MVP):** Filesystem storage via `PlugwerkStorageService` with a configurable root directory (`plugwerk.storage.fs.root`).

- Artifacts stored as `{namespace}/{pluginId}/{version}.jar`
- SHA-256 checksum computed on upload and stored in `plugin_release.artifact_sha256`
- Verified on download

**Phase 2+:** Add a `S3StorageService` implementation of the same `StorageService` interface, switchable via `plugwerk.storage.type=s3`. No server code changes required.

## Consequences

- **Easier:** No additional infrastructure for Phase 1 self-hosted deployments
- **Easier:** `StorageService` interface isolates storage concerns — swapping is a single implementation class
- **Harder:** Filesystem storage does not survive container restarts without a persistent volume (documented in Docker Compose setup)
- **Harder:** No built-in replication or redundancy in Phase 1
