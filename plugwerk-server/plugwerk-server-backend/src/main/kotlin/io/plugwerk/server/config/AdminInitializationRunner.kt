/*
 * Plugwerk — Plugin Marketplace for the PF4J Ecosystem
 * Copyright (C) 2026 devtank42 GmbH
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU Affero General Public License as
 * published by the Free Software Foundation, either version 3 of the
 * License, or (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the
 * GNU Affero General Public License for more details.
 *
 * You should have received a copy of the GNU Affero General Public License
 * along with this program. If not, see <https://www.gnu.org/licenses/>.
 */
package io.plugwerk.server.config

import io.plugwerk.server.domain.NamespaceMemberEntity
import io.plugwerk.server.domain.NamespaceRole
import io.plugwerk.server.domain.UserEntity
import io.plugwerk.server.repository.NamespaceMemberRepository
import io.plugwerk.server.repository.NamespaceRepository
import io.plugwerk.server.repository.UserRepository
import org.slf4j.LoggerFactory
import org.springframework.boot.ApplicationArguments
import org.springframework.boot.ApplicationRunner
import org.springframework.security.crypto.password.PasswordEncoder
import org.springframework.stereotype.Component
import java.security.SecureRandom

/**
 * Bootstraps the initial admin user on first startup.
 *
 * If no user named `admin` exists in the database, this runner:
 * 1. Generates a cryptographically random initial password.
 * 2. Creates the `admin` user with `passwordChangeRequired = true`.
 * 3. Grants the admin user the [NamespaceRole.ADMIN] role on every existing namespace.
 * 4. Logs the generated password **once** at INFO level so the operator can retrieve it
 *    from the container / service logs during initial setup.
 *
 * On subsequent startups (admin user already present) this runner is a no-op.
 *
 * The generated password is **not** stored in plain text anywhere after this point.
 * The admin must change it on first login (enforced by [UserEntity.passwordChangeRequired]).
 */
@Component
class AdminInitializationRunner(
    private val userRepository: UserRepository,
    private val namespaceRepository: NamespaceRepository,
    private val namespaceMemberRepository: NamespaceMemberRepository,
    private val passwordEncoder: PasswordEncoder,
) : ApplicationRunner {

    private val log = LoggerFactory.getLogger(AdminInitializationRunner::class.java)

    override fun run(args: ApplicationArguments) {
        if (userRepository.existsByUsername(ADMIN_USERNAME)) return

        val initialPassword = generatePassword()
        val admin = userRepository.save(
            UserEntity(
                username = ADMIN_USERNAME,
                passwordHash = passwordEncoder.encode(initialPassword)!!,
                passwordChangeRequired = true,
                enabled = true,
            ),
        )

        namespaceRepository.findAll().forEach { namespace ->
            namespaceMemberRepository.save(
                NamespaceMemberEntity(
                    namespace = namespace,
                    userSubject = ADMIN_USERNAME,
                    role = NamespaceRole.ADMIN,
                ),
            )
        }

        log.info(
            """
            ╔══════════════════════════════════════════════════════════╗
            ║         Plugwerk — Initial Admin Password                ║
            ║                                                          ║
            ║  Username : {}
            ║  Password : {}
            ║                                                          ║
            ║  Change this password immediately after first login.     ║
            ╚══════════════════════════════════════════════════════════╝
            """.trimIndent(),
            admin.username,
            initialPassword,
        )
    }

    private fun generatePassword(): String {
        val chars = "ABCDEFGHJKMNPQRSTUVWXYZabcdefghjkmnpqrstuvwxyz23456789!@#%&"
        val rng = SecureRandom()
        return (1..16).map { chars[rng.nextInt(chars.length)] }.joinToString("")
    }

    companion object {
        const val ADMIN_USERNAME = "admin"
    }
}
