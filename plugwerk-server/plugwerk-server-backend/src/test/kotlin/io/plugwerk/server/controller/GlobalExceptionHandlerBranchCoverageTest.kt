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
package io.plugwerk.server.controller

import io.plugwerk.descriptor.DescriptorNotFoundException
import io.plugwerk.descriptor.DescriptorValidationException
import io.plugwerk.server.service.ArtifactStorageException
import io.plugwerk.server.service.ConflictException
import io.plugwerk.server.service.EntityNotFoundException
import io.plugwerk.server.service.FileTooLargeException
import io.plugwerk.server.service.ForbiddenException
import io.plugwerk.server.service.InvalidArtifactException
import io.plugwerk.server.service.NamespaceNotFoundException
import io.plugwerk.server.service.UnauthorizedException
import io.plugwerk.server.service.auth.ExternalUserResetNotAllowedException
import io.plugwerk.server.service.auth.InvalidPasswordResetTokenException
import io.plugwerk.server.service.auth.InvalidVerificationTokenException
import io.plugwerk.server.service.auth.SelfResetNotAllowedException
import jakarta.validation.ConstraintViolation
import jakarta.validation.ConstraintViolationException
import jakarta.validation.Path
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.mockito.kotlin.mock
import org.mockito.kotlin.whenever
import org.springframework.http.HttpStatus
import org.springframework.http.converter.HttpMessageNotReadableException
import org.springframework.mock.http.MockHttpInputMessage
import org.springframework.security.access.AccessDeniedException
import org.springframework.security.authorization.AuthorizationDeniedException
import org.springframework.validation.BindingResult
import org.springframework.validation.FieldError
import org.springframework.web.bind.MethodArgumentNotValidException
import org.springframework.web.multipart.MaxUploadSizeExceededException
import org.springframework.web.server.ResponseStatusException

class GlobalExceptionHandlerBranchCoverageTest {

    private val handler = GlobalExceptionHandler()

    // -- handleNotFound ------------------------------------------------------

    @Test
    fun `handleNotFound uses the exception message`() {
        val response = handler.handleNotFound(NamespaceNotFoundException("acme"))

        assertThat(response.statusCode).isEqualTo(HttpStatus.NOT_FOUND)
        assertThat(response.body?.message).contains("acme")
        assertThat(response.body?.status).isEqualTo(404)
        assertThat(response.body?.error).isEqualTo(HttpStatus.NOT_FOUND.reasonPhrase)
    }

    @Test
    fun `handleNotFound falls back to the default when the message is null`() {
        val ex = mock<EntityNotFoundException>() // message defaults to null
        val response = handler.handleNotFound(ex)

        assertThat(response.statusCode).isEqualTo(HttpStatus.NOT_FOUND)
        assertThat(response.body?.message).isEqualTo("Resource not found")
    }

    // -- handleConflict ------------------------------------------------------

    @Test
    fun `handleConflict uses the exception message`() {
        val response = handler.handleConflict(ConflictException("slot taken"))

        assertThat(response.statusCode).isEqualTo(HttpStatus.CONFLICT)
        assertThat(response.body?.message).isEqualTo("slot taken")
    }

    @Test
    fun `handleConflict falls back to the default when the message is null`() {
        val response = handler.handleConflict(mock<ConflictException>())

        assertThat(response.body?.message).isEqualTo("Resource already exists")
    }

    // -- handleUnauthorized --------------------------------------------------

    @Test
    fun `handleUnauthorized uses the exception message`() {
        val response = handler.handleUnauthorized(UnauthorizedException("bad token"))

        assertThat(response.statusCode).isEqualTo(HttpStatus.UNAUTHORIZED)
        assertThat(response.body?.message).isEqualTo("bad token")
    }

    @Test
    fun `handleUnauthorized falls back to the default when the message is null`() {
        val response = handler.handleUnauthorized(mock<UnauthorizedException>())

        assertThat(response.body?.message).isEqualTo("Unauthorized")
    }

    // -- handleForbidden -----------------------------------------------------

    @Test
    fun `handleForbidden uses the exception message`() {
        val response = handler.handleForbidden(ForbiddenException("nope"))

        assertThat(response.statusCode).isEqualTo(HttpStatus.FORBIDDEN)
        assertThat(response.body?.message).isEqualTo("nope")
    }

    @Test
    fun `handleForbidden falls back to the default when the message is null`() {
        val response = handler.handleForbidden(mock<ForbiddenException>())

        assertThat(response.body?.message).isEqualTo("Forbidden")
    }

    // -- handleAuthorizationDenied -------------------------------------------

    @Test
    fun `handleAuthorizationDenied maps AuthorizationDeniedException to 403`() {
        val response = handler.handleAuthorizationDenied(AccessDeniedException("denied"))

        assertThat(response.statusCode).isEqualTo(HttpStatus.FORBIDDEN)
        // Always the fixed "Access denied" string regardless of the cause message.
        assertThat(response.body?.message).isEqualTo("Access denied")
    }

    @Test
    fun `handleAuthorizationDenied also accepts the Spring Security 7 subtype`() {
        val response = handler.handleAuthorizationDenied(mock<AuthorizationDeniedException>())

        assertThat(response.statusCode).isEqualTo(HttpStatus.FORBIDDEN)
        assertThat(response.body?.message).isEqualTo("Access denied")
    }

    // -- handleValidation ----------------------------------------------------

    @Test
    fun `handleValidation joins field errors into the message`() {
        val ex = mock<MethodArgumentNotValidException>()
        val binding = mock<BindingResult>()
        whenever(ex.bindingResult).thenReturn(binding)
        whenever(binding.fieldErrors).thenReturn(
            listOf(
                FieldError("obj", "name", "must not be blank"),
                FieldError("obj", "size", "must be positive"),
            ),
        )

        val response = handler.handleValidation(ex)

        assertThat(response.statusCode).isEqualTo(HttpStatus.BAD_REQUEST)
        assertThat(response.body?.message)
            .contains("name: must not be blank")
            .contains("size: must be positive")
    }

    @Test
    fun `handleValidation falls back to a generic message when no field errors are present`() {
        val ex = mock<MethodArgumentNotValidException>()
        val binding = mock<BindingResult>()
        whenever(ex.bindingResult).thenReturn(binding)
        whenever(binding.fieldErrors).thenReturn(emptyList())

        val response = handler.handleValidation(ex)

        // joinToString on an empty list is blank → ifBlank default.
        assertThat(response.body?.message).isEqualTo("Validation failed")
    }

    // -- handleConstraintViolation -------------------------------------------

    @Test
    fun `handleConstraintViolation keeps only the last path segment per violation`() {
        val violation = mock<ConstraintViolation<*>>()
        val path = mock<Path>()
        whenever(path.toString()).thenReturn("listPlugins.size")
        whenever(violation.propertyPath).thenReturn(path)
        whenever(violation.message).thenReturn("must be at most 100")
        val ex = ConstraintViolationException(setOf(violation))

        val response = handler.handleConstraintViolation(ex)

        assertThat(response.statusCode).isEqualTo(HttpStatus.BAD_REQUEST)
        assertThat(response.body?.message).isEqualTo("size: must be at most 100")
    }

    @Test
    fun `handleConstraintViolation falls back to a generic message when there are no violations`() {
        val ex = ConstraintViolationException(emptySet())

        val response = handler.handleConstraintViolation(ex)

        assertThat(response.body?.message).isEqualTo("Validation failed")
    }

    // -- handleUnreadableBody ------------------------------------------------

    @Test
    fun `handleUnreadableBody returns a fixed 400 message`() {
        val response = handler.handleUnreadableBody(
            HttpMessageNotReadableException("boom", MockHttpInputMessage(ByteArray(0))),
        )

        assertThat(response.statusCode).isEqualTo(HttpStatus.BAD_REQUEST)
        assertThat(response.body?.message).isEqualTo("Request body is missing or malformed")
    }

    // -- handleIllegalArgument -----------------------------------------------

    @Test
    fun `handleIllegalArgument uses the exception message`() {
        val response = handler.handleIllegalArgument(IllegalArgumentException("bad value"))

        assertThat(response.statusCode).isEqualTo(HttpStatus.BAD_REQUEST)
        assertThat(response.body?.message).isEqualTo("bad value")
    }

    @Test
    fun `handleIllegalArgument falls back to the default when the message is null`() {
        val response = handler.handleIllegalArgument(IllegalArgumentException())

        assertThat(response.body?.message).isEqualTo("Invalid request")
    }

    // -- handleSmtpNotConfigured ---------------------------------------------

    @Test
    fun `handleSmtpNotConfigured uses the exception message`() {
        val response = handler.handleSmtpNotConfigured(SmtpNotConfiguredException("configure SMTP first"))

        assertThat(response.statusCode).isEqualTo(HttpStatus.BAD_REQUEST)
        assertThat(response.body?.message).isEqualTo("configure SMTP first")
    }

    @Test
    fun `handleSmtpNotConfigured falls back to the default when the message is null`() {
        val response = handler.handleSmtpNotConfigured(mock<SmtpNotConfiguredException>())

        assertThat(response.body?.message).isEqualTo("SMTP is not configured")
    }

    // -- handleSmtpDelivery --------------------------------------------------

    @Test
    fun `handleSmtpDelivery maps to 502 with the exception message`() {
        val response = handler.handleSmtpDelivery(SmtpDeliveryException("relay refused"))

        assertThat(response.statusCode).isEqualTo(HttpStatus.BAD_GATEWAY)
        assertThat(response.body?.message).isEqualTo("relay refused")
    }

    @Test
    fun `handleSmtpDelivery falls back to the default when the message is null`() {
        val response = handler.handleSmtpDelivery(mock<SmtpDeliveryException>())

        assertThat(response.statusCode).isEqualTo(HttpStatus.BAD_GATEWAY)
        assertThat(response.body?.message).isEqualTo("SMTP server rejected the message")
    }

    // -- handleMailTemplateNotFound ------------------------------------------

    @Test
    fun `handleMailTemplateNotFound uses the exception message`() {
        val response = handler.handleMailTemplateNotFound(MailTemplateNotFoundException("welcome"))

        assertThat(response.statusCode).isEqualTo(HttpStatus.NOT_FOUND)
        assertThat(response.body?.message).contains("welcome")
    }

    @Test
    fun `handleMailTemplateNotFound falls back to the default when the message is null`() {
        val response = handler.handleMailTemplateNotFound(mock<MailTemplateNotFoundException>())

        assertThat(response.body?.message).isEqualTo("Mail template not found")
    }

    // -- token handlers ------------------------------------------------------

    @Test
    fun `handleInvalidVerificationToken uses the exception message`() {
        val response = handler.handleInvalidVerificationToken(InvalidVerificationTokenException("expired"))

        assertThat(response.statusCode).isEqualTo(HttpStatus.BAD_REQUEST)
        assertThat(response.body?.message).isEqualTo("expired")
    }

    @Test
    fun `handleInvalidVerificationToken falls back to the default when the message is null`() {
        val response = handler.handleInvalidVerificationToken(mock<InvalidVerificationTokenException>())

        assertThat(response.body?.message).isEqualTo("Verification token is invalid")
    }

    @Test
    fun `handleInvalidPasswordResetToken uses the exception message`() {
        val response = handler.handleInvalidPasswordResetToken(InvalidPasswordResetTokenException("already used"))

        assertThat(response.statusCode).isEqualTo(HttpStatus.BAD_REQUEST)
        assertThat(response.body?.message).isEqualTo("already used")
    }

    @Test
    fun `handleInvalidPasswordResetToken falls back to the default when the message is null`() {
        val response = handler.handleInvalidPasswordResetToken(mock<InvalidPasswordResetTokenException>())

        assertThat(response.body?.message).isEqualTo("Password-reset token is invalid")
    }

    @Test
    fun `handleSelfReset uses the exception message`() {
        val response = handler.handleSelfReset(SelfResetNotAllowedException("use profile"))

        assertThat(response.statusCode).isEqualTo(HttpStatus.BAD_REQUEST)
        assertThat(response.body?.message).isEqualTo("use profile")
    }

    @Test
    fun `handleSelfReset falls back to the default when the message is null`() {
        val response = handler.handleSelfReset(mock<SelfResetNotAllowedException>())

        assertThat(response.body?.message).isEqualTo("Self-reset not permitted via admin endpoint")
    }

    @Test
    fun `handleExternalUserReset uses the exception message`() {
        val response = handler.handleExternalUserReset(ExternalUserResetNotAllowedException("oidc user"))

        assertThat(response.statusCode).isEqualTo(HttpStatus.BAD_REQUEST)
        assertThat(response.body?.message).isEqualTo("oidc user")
    }

    @Test
    fun `handleExternalUserReset falls back to the default when the message is null`() {
        val response = handler.handleExternalUserReset(mock<ExternalUserResetNotAllowedException>())

        assertThat(response.body?.message).isEqualTo("Cannot reset password on an OIDC user")
    }

    // -- handleResponseStatus ------------------------------------------------

    @Test
    fun `handleResponseStatus honours the carried status and reason`() {
        val ex = ResponseStatusException(HttpStatus.SERVICE_UNAVAILABLE, "Mail is offline")

        val response = handler.handleResponseStatus(ex)

        assertThat(response.statusCode).isEqualTo(HttpStatus.SERVICE_UNAVAILABLE)
        assertThat(response.body?.message).isEqualTo("Mail is offline")
    }

    @Test
    fun `handleResponseStatus uses the status reason phrase when no reason is supplied`() {
        // reason == null → msg falls back to status.reasonPhrase.
        val ex = ResponseStatusException(HttpStatus.NOT_FOUND)

        val response = handler.handleResponseStatus(ex)

        assertThat(response.statusCode).isEqualTo(HttpStatus.NOT_FOUND)
        assertThat(response.body?.message).isEqualTo(HttpStatus.NOT_FOUND.reasonPhrase)
    }

    @Test
    fun `handleResponseStatus falls back to 500 for an unresolvable status code`() {
        // 799 has no HttpStatus → HttpStatus.resolve(...) is null → elvis default 500.
        val ex = ResponseStatusException(799, "weird", null)

        val response = handler.handleResponseStatus(ex)

        assertThat(response.statusCode).isEqualTo(HttpStatus.INTERNAL_SERVER_ERROR)
        assertThat(response.body?.message).isEqualTo("weird")
    }

    // -- handleDescriptorError -----------------------------------------------

    @Test
    fun `handleDescriptorError maps a descriptor exception to 422 with its message`() {
        val response = handler.handleDescriptorError(DescriptorNotFoundException("no manifest"))

        assertThat(response.statusCode).isEqualTo(HttpStatus.UNPROCESSABLE_ENTITY)
        assertThat(response.body?.message).isEqualTo("no manifest")
    }

    @Test
    fun `handleDescriptorError falls back to the default when the message is null`() {
        // DescriptorValidationException builds a non-null message, so mock to force null.
        val response = handler.handleDescriptorError(mock<DescriptorValidationException>())

        assertThat(response.statusCode).isEqualTo(HttpStatus.UNPROCESSABLE_ENTITY)
        assertThat(response.body?.message).isEqualTo("Plugin descriptor is invalid or missing")
    }

    // -- handleInvalidArtifact -----------------------------------------------

    @Test
    fun `handleInvalidArtifact uses the exception message`() {
        val response = handler.handleInvalidArtifact(InvalidArtifactException("not a jar"))

        assertThat(response.statusCode).isEqualTo(HttpStatus.UNPROCESSABLE_ENTITY)
        assertThat(response.body?.message).isEqualTo("not a jar")
    }

    @Test
    fun `handleInvalidArtifact falls back to the default when the message is null`() {
        val response = handler.handleInvalidArtifact(mock<InvalidArtifactException>())

        assertThat(response.body?.message).isEqualTo("Invalid artifact")
    }

    // -- handleFileTooLarge --------------------------------------------------

    @Test
    fun `handleFileTooLarge uses the exception message`() {
        val response = handler.handleFileTooLarge(FileTooLargeException(actualSizeBytes = 10_485_760, maxSizeMb = 5))

        assertThat(response.statusCode).isEqualTo(HttpStatus.PAYLOAD_TOO_LARGE)
        assertThat(response.body?.message).contains("exceeds maximum")
    }

    @Test
    fun `handleFileTooLarge falls back to the default when the message is null`() {
        val response = handler.handleFileTooLarge(mock<FileTooLargeException>())

        assertThat(response.body?.message).isEqualTo("File too large")
    }

    // -- handleMaxUploadSize -------------------------------------------------

    @Test
    fun `handleMaxUploadSize returns a fixed 413 message`() {
        val response = handler.handleMaxUploadSize(MaxUploadSizeExceededException(1024))

        assertThat(response.statusCode).isEqualTo(HttpStatus.PAYLOAD_TOO_LARGE)
        assertThat(response.body?.message).isEqualTo("Upload exceeds the maximum allowed file size")
    }

    // -- handleStorage -------------------------------------------------------

    @Test
    fun `handleStorage logs and returns a generic 500`() {
        val response = handler.handleStorage(ArtifactStorageException("disk full"))

        assertThat(response.statusCode).isEqualTo(HttpStatus.INTERNAL_SERVER_ERROR)
        // The detailed message is logged, never leaked to the client.
        assertThat(response.body?.message).isEqualTo("Storage operation failed")
    }

    // -- handleUnexpected ----------------------------------------------------

    @Test
    fun `handleUnexpected returns a generic 500 without leaking detail`() {
        val response = handler.handleUnexpected(RuntimeException("NPE in service"))

        assertThat(response.statusCode).isEqualTo(HttpStatus.INTERNAL_SERVER_ERROR)
        assertThat(response.body?.message).isEqualTo("An unexpected error occurred")
        assertThat(response.body?.timestamp).isNotNull()
    }
}
