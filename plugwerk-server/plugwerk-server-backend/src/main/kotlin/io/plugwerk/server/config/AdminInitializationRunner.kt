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

import io.plugwerk.server.PlugwerkProperties
import io.plugwerk.server.domain.UserEntity
import io.plugwerk.server.repository.UserRepository
import org.slf4j.LoggerFactory
import org.springframework.boot.ApplicationArguments
import org.springframework.boot.ApplicationRunner
import org.springframework.security.crypto.password.PasswordEncoder
import org.springframework.stereotype.Component
import java.security.SecureRandom

/**
 * Bootstraps the initial superadmin user on first startup.
 *
 * If no user with the configured admin username exists in the database, this runner:
 * 1. Uses [PlugwerkProperties.AuthProperties.adminPassword] if set (CI/smoke-test), or
 *    generates a cryptographically random initial password (production).
 * 2. Creates the superadmin user with [isSuperadmin = true]. If the password was generated,
 *    `passwordChangeRequired = true` is set so the operator must change it on first login.
 * 3. Logs the password **once** at INFO level when it was auto-generated.
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

    override fun run(args: ApplicationArguments) {
        val adminUsername = properties.auth.adminUsername
        if (userRepository.existsByUsername(adminUsername)) return

        val fixedPassword = properties.auth.adminPassword
        val initialPassword = fixedPassword ?: generatePassword()
        val passwordChangeRequired = fixedPassword == null

        val admin = userRepository.save(
            UserEntity(
                username = adminUsername,
                passwordHash = passwordEncoder.encode(initialPassword)!!,
                passwordChangeRequired = passwordChangeRequired,
                isSuperadmin = true,
                enabled = true,
            ),
        )

        if (passwordChangeRequired) {
            log.info(
                """

                ╔══════════════════════════════════════════════════════════╗
                ║         Plugwerk — Initial Superadmin Password           ║
                ║                                                          ║
                ║  Username : {}                                           ║
                ║  Password : {}                                           ║
                ║                                                          ║
                ║  Change this password immediately after first login.     ║
                ╚══════════════════════════════════════════════════════════╝
                """.trimIndent(),
                admin.username,
                initialPassword,
            )
        }
    }

    private fun generatePassword(): String {
        val chars = "ABCDEFGHJKMNPQRSTUVWXYZabcdefghjkmnpqrstuvwxyz23456789!@#%&"
        val rng = SecureRandom()
        return (1..16).map { chars[rng.nextInt(chars.length)] }.joinToString("")
    }
}
