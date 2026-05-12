# ADR-0034: S3-compatible storage backend

- Status: Accepted
- Date: 2026-05-12
- Issue: [#191](https://github.com/plugwerk/plugwerk/issues/191)

## Context

Plugwerk shipped with a filesystem-only artifact store. That choice is a hard blocker for horizontal scaling (multi-node deployments cannot share a local FS), most managed-hosting platforms (no persistent volumes), disaster-recovery setups (no geographic replication), and any SaaS-style multi-tenant deployment.

The `ArtifactStorageService` interface, the filesystem implementation, and the `plugwerk.storage.type` selector property already existed from earlier preparation work — only the second implementation was missing.

## Decision

Add an `S3ArtifactStorageService` that targets any S3-compatible endpoint (AWS S3, MinIO, Hetzner Object Storage, Cloudflare R2), gated by `plugwerk.storage.type=s3`. The following load-bearing trade-offs are recorded here so future readers don't need to mine the PR thread.

### SDK choice — AWS SDK for Java v2

Picked `software.amazon.awssdk:s3` over the MinIO Java SDK and over Spring Cloud AWS's `S3Template`. Rationale:

- Universal S3 compatibility via `endpointOverride(URI)` — proven against every endpoint Plugwerk advertises.
- First-party AWS support — long-term security patches, no third-party Spring autoconfig glue.
- We pin the synchronous `apache-client` HTTP module and exclude `netty-nio-client`: Plugwerk's servlet stack is blocking, and Netty would inflate the fat-jar without paying for itself.

Cost: ~6 MB transitive size delta. Accepted.

### Key encoding — keep colons

`PluginReleaseService` builds keys as `<namespace-uuid>:<plugin-id>:<version>:<extension>`. S3 accepts colons in object keys (RFC 3986 sub-delims) and AWS, MinIO, R2, and Hetzner all serve them transparently. Converting to slash-separated paths would:

- force every existing filesystem deployment through a rename migration,
- require a parallel re-keying pass on any production S3 bucket already in use, and
- ask the storage interface to either grow a "what's the native separator" method or to leak backend knowledge into `PluginReleaseService`.

A slash redesign remains possible but lives in its own future issue with its own migration story. Until then, key encoding is backend-agnostic by contract.

### Credentials — hybrid (static or default-chain)

When `accessKey` AND `secretKey` are both set, `StaticCredentialsProvider` is used. When BOTH are blank, the SDK's `DefaultCredentialsProvider` runs (env, instance profile, IRSA, ECS task role). Half-configured states (one set, one blank) are caught at startup by a Bean-Validation `@AssertTrue`.

The hybrid serves two operator profiles cleanly:

- MinIO / dev / fixed-key cloud providers (Hetzner, R2) → static creds via env vars.
- AWS production on EKS / ECS / EC2 with IRSA → leave env vars blank, let the SDK negotiate.

### Bucket startup probe — `HeadBucket`, opt-in fail-fast

An `ApplicationRunner` issues one `HeadBucket` on context start. Two operator outcomes:

- Default (`fail-fast-on-bucket-missing=false`) → probe failure logs `ERROR` with bucket, region, endpoint, and the SDK message; the server keeps running so an operator can fix the bucket without a restart loop.
- Opt-in (`fail-fast-on-bucket-missing=true`) → probe failure throws; the Spring context fails to start; the orchestrator's own restart loop kicks in. Mirrors the [#501](https://github.com/plugwerk/plugwerk/issues/501) fail-fast pattern for the encryption-key mismatch.

`NoSuchBucket` and `PermanentRedirect` (region mismatch) both land in the same log line — the SDK message disambiguates without an extra branch.

### Streaming — return the SDK's response stream directly

`getObject(...)` returns `ResponseInputStream<GetObjectResponse>`, which IS an `InputStream`. We return it through `ArtifactStorageService.retrieve` unchanged so the response body streams to the client without buffering. The controller layer uses Spring's `InputStreamResource`, which closes the stream after the response writes — verified during implementation.

Buffering would force every download into RAM, which is wrong for an artifact store with operator-controlled upload size limits.

### `listKeys` on the interface

Added in this PR even though no current caller needs it. [#190](https://github.com/plugwerk/plugwerk/issues/190) (StorageConsistencyService) will, and pushing the interface change into that PR would force a second cross-implementation update. Filesystem materialises eagerly (artifact directory is bounded); S3 paginates lazily via `ListObjectsV2Paginator` wrapped in a Kotlin `sequence { ... }`.

## Consequences

- Plugwerk now scales horizontally with shared object storage. Multiple server instances behind a load balancer point at the same bucket.
- Operators must choose `fs` or `s3` at deploy time; switching post-hoc requires manual artifact migration (out of scope for this ADR).
- The fat-jar gains ~6 MB. CycloneDX SBOM picks up the new transitive deps automatically.
- A new MinIO Testcontainer participates in the integration-test suite; CI runners must have outbound Docker access.

## Out of scope (recorded for future work)

- Filesystem → S3 artifact migration tool.
- Multi-bucket / multi-region setups.
- Pre-signed URLs for direct client uploads.
- GCS, Azure Blob Storage backends — would follow the same abstraction.
- Slash-based key encoding (see "Key encoding" above).
