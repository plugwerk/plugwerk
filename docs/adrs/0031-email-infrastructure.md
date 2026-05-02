# ADR-0031: Email Infrastructure (SMTP)

## Status

Accepted (PR for #253)

## Context

Plugwerk needs to send transactional email — password reset, invitations,
review notifications, admin alerts. Up to now there was no mail-sending
infrastructure at all. This ADR records the design decisions made while
adding it; the SPI surface, runtime model, and security posture should
not need to be re-derived from the diff.

The feature is split across three concerns:

1. **Configuration model** — where the SMTP server settings live and how
   they are changed.
2. **Mail-sending infrastructure** — how the application actually puts a
   message on the wire.
3. **Operator verification** — how the operator confirms their config
   without watching the server log.

Only the SMTP/server side is in scope for this ADR. Per-template
management, async queues, and concrete trigger integrations (password
reset, etc.) are deferred to follow-up issues.

## Decision

### 1. Settings live in `application_setting`, not `application.yaml`

The eight SMTP keys (`smtp.enabled`, `host`, `port`, `username`,
`password`, `encryption`, `from_address`, `from_name`) are registered as
`ApplicationSettingKey` entries and persisted in the existing
`application_setting` table (ADR-0016). They are mutable at runtime via
the existing `PUT /api/v1/admin/settings` endpoint.

Spring Boot's `spring.mail.*` auto-configuration is **not** wired —
otherwise the SMTP server would be locked in at JVM start and require a
restart to change. For a self-hosted product where the operator may not
control the SMTP relay until after the server is running, that is the
wrong default.

### 2. Password is encrypted at rest, masked in API responses

`smtp.password` carries a new `SettingValueType.PASSWORD` discriminator.
The marker triggers three different behaviours at three different
layers:

- **Storage** — `ApplicationSettingsService.update` encrypts the value
  with the existing `TextEncryptor` bean (introduced for OIDC
  `client_secret_encrypted` in ADR-0022) before persisting. The cache
  holds ciphertext.
- **Display** — `AdminSettingsController.toDto` replaces ciphertext with
  the sentinel `"***"` so plaintext never leaves the JVM through the
  HTTP layer. Even the ciphertext is hidden — leaking it would let an
  attacker who later learns the encryption key replay the original
  password.
- **Internal use** — A typed accessor
  `ApplicationSettingsService.smtpPasswordPlaintext()` decrypts the
  ciphertext for `MailSenderProvider` only. Marked internal-only in its
  KDoc; never exposed in DTOs or log lines.

The masking sentinel is recognised on the write path: writing back the
literal string `"***"` is treated as "no change" so the form's hidden
field cannot accidentally overwrite the stored ciphertext with random
ASCII.

### 3. `JavaMailSender` is built on demand, cached, invalidated on settings change

A new `MailSenderProvider` Spring component holds an
`AtomicReference<JavaMailSender?>`. On the first `current()` call after
a setting change (or on cold start) it builds a fresh
`JavaMailSenderImpl` from the live settings; subsequent calls return
the cached instance. The cache is invalidated by an
`ApplicationSettingsService` update listener whenever any `smtp.*` key
is written.

This is the same pattern already used by `ApplicationSettingsService`
itself for the settings cache — readers are lock-free, writers do an
atomic swap, and there is no half-built state visible to a reader.

When SMTP is disabled or the configuration is incomplete (blank host,
unknown encryption mode), `current()` returns `null`. `MailService`
treats that as no-op + warning rather than an exception, so a misconfig
does not brick callers that legitimately have nothing to do when SMTP
is off.

### 4. `STARTTLS` is required, not opportunistic

When `smtp.encryption=starttls` we set both
`mail.smtp.starttls.enable=true` and
`mail.smtp.starttls.required=true`. Opportunistic STARTTLS would
silently fall back to plaintext if the server advertised support but
the upgrade failed — exactly the kind of misconfiguration the operator
is least likely to notice. Failing loudly is the correct default.

### 5. Test endpoint surfaces SMTP errors as 502, never as 500

`POST /api/v1/admin/email/test` is the operator's verification path. It
accepts a target address, sends a fixed test body, and returns:

- `200` with a confirmation when the SMTP server accepted the message,
- `400` with a hint pointing at the missing field when SMTP is disabled
  or the configuration is incomplete (`SmtpNotConfiguredException`),
- `502` Bad Gateway when the SMTP server rejected the message
  (`SmtpDeliveryException`) — same semantic as a downstream HTTP
  service returning 5xx.

Internal exceptions are mapped to short kuratierte messages by
`GlobalExceptionHandler`; raw stack traces and `JavaMail` exception
chains are not exposed to the wire.

### 6. Tests run against an in-process SMTP server (GreenMail)

Integration tests live in `SmtpEmailIT` and use the GreenMail JUnit5
extension on a dynamic port. The IT writes settings through the
service, calls `MailService.sendMail`, and asserts on
`greenMail.receivedMessages`. End-to-end coverage of the
`settings -> MailSenderProvider -> JavaMailSenderImpl -> SMTP wire`
boundary that no controller-only test could exercise.

## Consequences

- The `JavaMailSender` Spring auto-config bean is intentionally never
  loaded; if a future caller imports `spring-boot-starter-mail` and
  expects auto-config, they will need to either wire through the
  `MailService` API or override the bean explicitly.
- Encryption key rotation (`PLUGWERK_AUTH_ENCRYPTION_KEY`) loses access
  to the stored `smtp.password` ciphertext just as it does for OIDC
  `client_secret`. Same documented limitation; operator must re-enter
  the password after rotation. Out of scope for this issue.
- The new `SettingValueType.PASSWORD` is now part of the public OpenAPI
  contract. Future password-typed settings (e.g. webhook signing
  secrets, S3 access keys) get the same masking + encryption posture
  for free by reusing the marker.
- `MailService` is intentionally narrow today (`sendMail(to, subject,
  body)` only). Template rendering is a separate decision tracked under
  the follow-up template-engine issue; adding stub methods here would
  widen the API surface without consumers.
- Async / queued delivery and retry are deferred. The current synchronous
  contract makes the test endpoint useful (the operator gets the SMTP
  error in their browser, not 30 seconds later in a worker log) and
  keeps the boundary simple.

## Alternatives considered

- **`spring.mail.*` from `application.yaml`** — rejected per §1; the
  configuration must be runtime-mutable. A hybrid (runtime overrides
  optional yaml defaults) was considered and rejected as adding
  complexity without a use case.
- **Lazy-rebuild on every send** — simpler than the cached-with-
  invalidation pattern but pays SMTP-server-DNS-resolution + connection
  setup costs on every call. The cached pattern is the same one the
  settings service itself uses, so the abstraction cost is amortised.
- **Generic `OperationContext` parameter on `sendMail`** — rejected as
  speculative; the only consumer would be the test endpoint, which can
  build its own subject/body.
- **Always returning the result type even when caller does not care** —
  the alternative would be to throw on failure. Returning a sealed
  `SendResult` lets the test endpoint distinguish 400 from 502 cleanly,
  while ignoring the result at most call sites is one statement.

## References

- ADR-0016: Application-Settings-Precedence
- ADR-0022: Encryption key sizing
- Issue #253: SMTP/email server configuration in global application settings
- Spring Boot `spring-boot-starter-mail` (used as a dependency, not as
  auto-config)
- GreenMail (used as test dependency only)
