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
import io.plugwerk.server.repository.UserRepository
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.ExtendWith
import org.junit.jupiter.api.io.TempDir
import org.mockito.ArgumentCaptor
import org.mockito.Mock
import org.mockito.Mockito.verify
import org.mockito.Mockito.verifyNoMoreInteractions
import org.mockito.junit.jupiter.MockitoExtension
import org.mockito.kotlin.any
import org.mockito.kotlin.whenever
import org.springframework.boot.DefaultApplicationArguments
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder
import org.springframework.security.crypto.password.PasswordEncoder
import java.io.ByteArrayOutputStream
import java.io.PrintStream
import java.nio.file.FileSystems
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.attribute.PosixFilePermission

/**
 * Unit tests for [AdminInitializationRunner] focused on audit finding SEC-044:
 * a blank / whitespace-only `PLUGWERK_AUTH_ADMIN_PASSWORD` (the silent failure
 * mode of the old `docker-compose.yml` default) must take the random-password
 * path and force a change on first login — NOT create the admin with an empty
 * credential.
 */
@ExtendWith(MockitoExtension::class)
class AdminInitializationRunnerTest {

    @Mock
    private lateinit var userRepository: UserRepository

    private val passwordEncoder: PasswordEncoder = BCryptPasswordEncoder()

    private fun runnerWith(adminPassword: String?): AdminInitializationRunner {
        val props = PlugwerkProperties(
            auth = PlugwerkProperties.AuthProperties(
                jwtSecret = "a".repeat(32),
                encryptionKey = "b".repeat(16),
                adminPassword = adminPassword,
            ),
        )
        return AdminInitializationRunner(userRepository, passwordEncoder, props)
    }

    private fun captureSavedUser(): UserEntity {
        val captor: ArgumentCaptor<UserEntity> = ArgumentCaptor.forClass(UserEntity::class.java)
        verify(userRepository).save(captor.capture())
        return captor.value
    }

    @Test
    fun `null admin password generates random credential and requires change on first login`() {
        whenever(
            userRepository.existsByUsernameAndSource("admin", io.plugwerk.server.domain.UserSource.INTERNAL),
        ).thenReturn(false)
        whenever(userRepository.save(any<UserEntity>())).thenAnswer { it.arguments[0] }

        runnerWith(null).run(DefaultApplicationArguments())

        val saved = captureSavedUser()
        assertThat(saved.passwordChangeRequired).isTrue()
        assertThat(passwordEncoder.matches("", saved.passwordHash)).isFalse()
    }

    @Test
    fun `blank admin password is treated as unset - random credential, change required`() {
        whenever(
            userRepository.existsByUsernameAndSource("admin", io.plugwerk.server.domain.UserSource.INTERNAL),
        ).thenReturn(false)
        whenever(userRepository.save(any<UserEntity>())).thenAnswer { it.arguments[0] }

        runnerWith("").run(DefaultApplicationArguments())

        val saved = captureSavedUser()
        assertThat(saved.passwordChangeRequired).isTrue()
        assertThat(passwordEncoder.matches("", saved.passwordHash)).isFalse()
    }

    @Test
    fun `whitespace-only admin password is treated as unset - random credential, change required`() {
        whenever(
            userRepository.existsByUsernameAndSource("admin", io.plugwerk.server.domain.UserSource.INTERNAL),
        ).thenReturn(false)
        whenever(userRepository.save(any<UserEntity>())).thenAnswer { it.arguments[0] }

        runnerWith("   ").run(DefaultApplicationArguments())

        val saved = captureSavedUser()
        assertThat(saved.passwordChangeRequired).isTrue()
        assertThat(passwordEncoder.matches("   ", saved.passwordHash)).isFalse()
    }

    @Test
    fun `non-blank admin password is used verbatim without forcing change`() {
        whenever(
            userRepository.existsByUsernameAndSource("admin", io.plugwerk.server.domain.UserSource.INTERNAL),
        ).thenReturn(false)
        whenever(userRepository.save(any<UserEntity>())).thenAnswer { it.arguments[0] }

        runnerWith("CI-smoke-test-password").run(DefaultApplicationArguments())

        val saved = captureSavedUser()
        assertThat(saved.passwordChangeRequired).isFalse()
        assertThat(passwordEncoder.matches("CI-smoke-test-password", saved.passwordHash)).isTrue()
    }

    @Test
    fun `existing admin user short-circuits the runner`() {
        whenever(
            userRepository.existsByUsernameAndSource("admin", io.plugwerk.server.domain.UserSource.INTERNAL),
        ).thenReturn(true)

        runnerWith("anything").run(DefaultApplicationArguments())

        verify(userRepository).existsByUsernameAndSource("admin", io.plugwerk.server.domain.UserSource.INTERNAL)
        verifyNoMoreInteractions(userRepository)
    }

    // ─── #150 / #286 — auto-generated password must not enter SLF4J ────────────

    @Test
    fun `auto-generated password is written to stderr and to a 0600 file (#150 #286)`(@TempDir tempDir: Path) {
        whenever(
            userRepository.existsByUsernameAndSource("admin", io.plugwerk.server.domain.UserSource.INTERNAL),
        ).thenReturn(false)
        whenever(userRepository.save(any<UserEntity>())).thenAnswer { it.arguments[0] }

        val passwordFile = tempDir.resolve("plugwerk-admin-password.txt")
        val capturedStderr = ByteArrayOutputStream()

        val runner = runnerWith(null).apply {
            passwordFilePath = passwordFile
            stderr = PrintStream(capturedStderr, true, Charsets.UTF_8)
        }
        runner.run(DefaultApplicationArguments())

        // 1) The credential is captured by stderr (the byte stream we redirected
        //    `System.err` to via the runner's overridable hook).
        val stderrText = capturedStderr.toString(Charsets.UTF_8)
        assertThat(stderrText)
            .`as`("stderr surface must show the credential box")
            .contains("Plugwerk — Initial Superadmin Password")
            .contains("Username : admin")
            .contains("Password : ")

        // 2) The same credential is in the file.
        assertThat(Files.exists(passwordFile))
            .`as`("0600 password file must be created")
            .isTrue()
        val fileText = Files.readString(passwordFile).trim()
        assertThat(fileText).isNotBlank()

        // 3) On POSIX file systems the permissions are exactly owner-read +
        //    owner-write — group/world have no access. On non-POSIX hosts (rare
        //    in CI) we skip this assertion gracefully.
        if (FileSystems.getDefault().supportedFileAttributeViews().contains("posix")) {
            val perms = Files.getPosixFilePermissions(passwordFile)
            assertThat(perms)
                .containsExactlyInAnyOrder(
                    PosixFilePermission.OWNER_READ,
                    PosixFilePermission.OWNER_WRITE,
                )
        }

        // 4) Sanity: the password the file holds is the same one stderr printed,
        //    confirming the two surfaces emit one consistent value (no race
        //    between two separate generatePassword() calls).
        assertThat(stderrText).contains(fileText)
    }

    @Test
    fun `fixed password (CI mode) writes nothing to stderr and creates no password file`(@TempDir tempDir: Path) {
        whenever(
            userRepository.existsByUsernameAndSource("admin", io.plugwerk.server.domain.UserSource.INTERNAL),
        ).thenReturn(false)
        whenever(userRepository.save(any<UserEntity>())).thenAnswer { it.arguments[0] }

        val passwordFile = tempDir.resolve("plugwerk-admin-password.txt")
        val capturedStderr = ByteArrayOutputStream()

        val runner = runnerWith("CI-smoke-test-password").apply {
            passwordFilePath = passwordFile
            stderr = PrintStream(capturedStderr, true, Charsets.UTF_8)
        }
        runner.run(DefaultApplicationArguments())

        // CI knows the password it set; surfacing it again would be a leak,
        // not a feature.
        assertThat(capturedStderr.toByteArray()).isEmpty()
        assertThat(Files.exists(passwordFile)).isFalse()
    }
}
