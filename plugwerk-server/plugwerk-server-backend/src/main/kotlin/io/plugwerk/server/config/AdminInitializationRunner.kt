/*
 * Copyright (c) 2025-present devtank42 GmbH
 *
 * This file is part of Plugwerk.
 *
 * Plugwerk is free software: you can redistribute it and/or modify
 * it under the terms of the GNU Affero General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * Plugwerk is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the
 * GNU Affero General Public License for more details.
 *
 * You should have received a copy of the GNU Affero General Public License
 * along with Plugwerk. If not, see <https://www.gnu.org/licenses/>.
 */
package io.plugwerk.server.config

import io.plugwerk.server.PlugwerkProperties
import io.plugwerk.server.domain.UserEntity
import io.plugwerk.server.domain.UserSource
import io.plugwerk.server.repository.UserRepository
import org.slf4j.LoggerFactory
import org.springframework.boot.ApplicationArguments
import org.springframework.boot.ApplicationRunner
import org.springframework.security.crypto.password.PasswordEncoder
import org.springframework.stereotype.Component
import java.nio.file.FileSystems
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.attribute.PosixFilePermission
import java.security.SecureRandom

/**
 * Bootstraps the initial superadmin user on first startup.
 *
 * If no user named [ADMIN_USERNAME] exists in the database, this runner:
 * 1. Uses [PlugwerkProperties.AuthProperties.adminPassword] if set to a non-blank value
 *    (CI/smoke-test), or generates a cryptographically random initial password
 *    (production). A blank or whitespace-only value is treated the same as unset —
 *    defense-in-depth against a stale compose file or env export that pre-declares
 *    the variable as the empty string (see audit finding SEC-044).
 * 2. Creates the superadmin user with [isSuperadmin = true]. If the password was generated,
 *    `passwordChangeRequired = true` is set so the operator must change it on first login.
 * 3. **Surfaces the auto-generated password without going through SLF4J.** The credential
 *    is written to `System.err` (the JVM stderr stream, bypassing every SLF4J appender) and
 *    to a `0600` file at [PASSWORD_FILE]. Operators retrieve it via
 *    `docker compose logs --no-log-prefix` (which forwards stderr) or by reading the file
 *    inside the container (`docker compose exec ... cat /tmp/plugwerk-admin-password.txt`).
 *    The SLF4J pipeline records that bootstrap occurred but never sees the credential —
 *    closes audit findings #150 / #286 (KT-013).
 *
 * The superadmin implicitly holds ADMIN rights in every namespace without needing an
 * explicit [namespace_member] entry. The superadmin account can never be deleted.
 *
 * On subsequent startups (superadmin user already present) this runner is a no-op.
 */
@Component
class AdminInitializationRunner(
    private val userRepository: UserRepository,
    private val passwordEncoder: PasswordEncoder,
    private val properties: PlugwerkProperties,
) : ApplicationRunner {

    private val log = LoggerFactory.getLogger(AdminInitializationRunner::class.java)

    /**
     * Where the auto-generated password is persisted on first start. Package-private `var`
     * so tests can redirect it to a `@TempDir`-backed path without touching the real
     * filesystem. Production code never reassigns it.
     */
    internal var passwordFilePath: Path = Path.of(PASSWORD_FILE)

    /**
     * Where the auto-generated password is written. Stderr is also used in parallel.
     */
    internal var stderr: java.io.PrintStream = System.err

    companion object {
        /** Fixed admin username — not configurable. */
        const val ADMIN_USERNAME = "admin"

        /**
         * Default email assigned to the bootstrap superadmin row. Users can update
         * it after first login via the profile UI; the value just needs to satisfy
         * the post-#351 NOT NULL constraint and the LOCAL-scoped email-uniqueness
         * index (no other admin can land on the same address).
         */
        const val ADMIN_DEFAULT_EMAIL = "admin@plugwerk.local"

        /**
         * Path of the 0600 file the auto-generated password is written to on first start.
         * Lives in the container's tmpfs and is wiped on restart; operators who claimed
         * the credential are expected to delete it manually.
         */
        const val PASSWORD_FILE = "/tmp/plugwerk-admin-password.txt"
    }

    override fun run(args: ApplicationArguments) {
        if (userRepository.existsByUsernameAndSource(ADMIN_USERNAME, UserSource.LOCAL)) return

        // takeUnless treats blank / whitespace-only values the same as unset.
        // SEC-044: the bundled docker-compose.yml previously pre-declared the env var as
        // empty string, which bound to a non-null "" and silently took the fixed-password
        // branch with an empty credential. That default is gone, but this guard stays.
        val fixedPassword = properties.auth.adminPassword?.takeUnless { it.isBlank() }
        val initialPassword = fixedPassword ?: generatePassword()
        val passwordChangeRequired = fixedPassword == null

        userRepository.save(
            UserEntity(
                username = ADMIN_USERNAME,
                displayName = "Administrator",
                email = ADMIN_DEFAULT_EMAIL,
                source = UserSource.LOCAL,
                passwordHash = passwordEncoder.encode(initialPassword)!!,
                passwordChangeRequired = passwordChangeRequired,
                isSuperadmin = true,
                enabled = true,
            ),
        )

        if (passwordChangeRequired) {
            surfaceGeneratedPassword(initialPassword)
        }
    }

    /**
     * Writes the credential to stderr and to a 0600 file, then logs a no-credential
     * hint into SLF4J pointing at both surfaces. Closes #150 / #286: the password
     * never enters the SLF4J pipeline, so log-aggregation forwarders (Datadog, ELK,
     * CloudWatch) cannot capture it.
     */
    private fun surfaceGeneratedPassword(password: String) {
        val box = """
            ╔══════════════════════════════════════════════════════════╗
            ║         Plugwerk — Initial Superadmin Password           ║
            ║                                                          ║
            ║  Username : $ADMIN_USERNAME
            ║  Password : $password
            ║                                                          ║
            ║  Change this password immediately after first login.     ║
            ╚══════════════════════════════════════════════════════════╝
        """.trimIndent()

        // Direct write to the JVM stderr stream — no SLF4J appenders attached, no
        // log-aggregation forwarder picks this up by default.
        stderr.println(box)

        val fileWritten = writePasswordFile(password)

        // SLF4J still records the event, just without the credential. The hint
        // points at both surfaces so the operator knows where to look regardless
        // of which one is observable in their setup.
        if (fileWritten) {
            log.info(
                "Initial superadmin password generated. Retrieve it from container stderr or from {} (POSIX 0600).",
                passwordFilePath,
            )
        } else {
            log.info(
                "Initial superadmin password generated. Retrieve it from container stderr (file write to {} failed — see WARN above).",
                passwordFilePath,
            )
        }
    }

    /**
     * Persists [password] to [passwordFilePath] with `0600` permissions. Returns
     * `true` on success, `false` on any failure (read-only filesystem, missing
     * POSIX support, permission setter not honoured). Failure is non-fatal — the
     * stderr surface remains the canonical channel.
     */
    private fun writePasswordFile(password: String): Boolean = runCatching {
        Files.writeString(passwordFilePath, password + System.lineSeparator())
        if (FileSystems.getDefault().supportedFileAttributeViews().contains("posix")) {
            Files.setPosixFilePermissions(
                passwordFilePath,
                setOf(PosixFilePermission.OWNER_READ, PosixFilePermission.OWNER_WRITE),
            )
        }
        true
    }.getOrElse { ex ->
        log.warn(
            "Could not persist initial admin password to {}: {} — operator must read it from stderr instead.",
            passwordFilePath,
            ex.message,
        )
        // If the partial write left a world-readable file behind, try to delete it
        // so the credential does not linger with bad permissions.
        runCatching { Files.deleteIfExists(passwordFilePath) }
        false
    }

    private fun generatePassword(): String {
        val chars = "ABCDEFGHJKMNPQRSTUVWXYZabcdefghjkmnpqrstuvwxyz23456789!@#%&"
        val rng = SecureRandom()
        return (1..16).map { chars[rng.nextInt(chars.length)] }.joinToString("")
    }
}
