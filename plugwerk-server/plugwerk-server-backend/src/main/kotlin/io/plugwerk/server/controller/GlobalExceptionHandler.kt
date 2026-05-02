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

import io.plugwerk.api.model.ErrorResponse
import io.plugwerk.descriptor.DescriptorNotFoundException
import io.plugwerk.descriptor.DescriptorParseException
import io.plugwerk.descriptor.DescriptorValidationException
import io.plugwerk.server.service.ArtifactNotFoundException
import io.plugwerk.server.service.ArtifactStorageException
import io.plugwerk.server.service.ConflictException
import io.plugwerk.server.service.EntityNotFoundException
import io.plugwerk.server.service.FileTooLargeException
import io.plugwerk.server.service.ForbiddenException
import io.plugwerk.server.service.InvalidArtifactException
import io.plugwerk.server.service.NamespaceAlreadyExistsException
import io.plugwerk.server.service.NamespaceNotFoundException
import io.plugwerk.server.service.PluginAlreadyExistsException
import io.plugwerk.server.service.PluginNotFoundException
import io.plugwerk.server.service.ReleaseAlreadyExistsException
import io.plugwerk.server.service.ReleaseNotFoundException
import io.plugwerk.server.service.UnauthorizedException
import jakarta.validation.ConstraintViolationException
import org.slf4j.LoggerFactory
import org.springframework.http.HttpStatus
import org.springframework.http.ResponseEntity
import org.springframework.http.converter.HttpMessageNotReadableException
import org.springframework.security.access.AccessDeniedException
import org.springframework.security.authorization.AuthorizationDeniedException
import org.springframework.web.bind.MethodArgumentNotValidException
import org.springframework.web.bind.annotation.ExceptionHandler
import org.springframework.web.bind.annotation.RestControllerAdvice
import org.springframework.web.multipart.MaxUploadSizeExceededException
import java.time.OffsetDateTime

@RestControllerAdvice
class GlobalExceptionHandler {

    private val log = LoggerFactory.getLogger(GlobalExceptionHandler::class.java)

    @ExceptionHandler(
        NamespaceNotFoundException::class,
        PluginNotFoundException::class,
        ReleaseNotFoundException::class,
        ArtifactNotFoundException::class,
        EntityNotFoundException::class,
    )
    fun handleNotFound(ex: RuntimeException): ResponseEntity<ErrorResponse> =
        errorResponse(HttpStatus.NOT_FOUND, ex.message ?: "Resource not found")

    @ExceptionHandler(
        NamespaceAlreadyExistsException::class,
        PluginAlreadyExistsException::class,
        ReleaseAlreadyExistsException::class,
        ConflictException::class,
    )
    fun handleConflict(ex: RuntimeException): ResponseEntity<ErrorResponse> =
        errorResponse(HttpStatus.CONFLICT, ex.message ?: "Resource already exists")

    @ExceptionHandler(UnauthorizedException::class)
    fun handleUnauthorized(ex: UnauthorizedException): ResponseEntity<ErrorResponse> =
        errorResponse(HttpStatus.UNAUTHORIZED, ex.message ?: "Unauthorized")

    @ExceptionHandler(ForbiddenException::class)
    fun handleForbidden(ex: ForbiddenException): ResponseEntity<ErrorResponse> =
        errorResponse(HttpStatus.FORBIDDEN, ex.message ?: "Forbidden")

    /**
     * Maps `@PreAuthorize`-denied requests (Spring Security 7's
     * [AuthorizationDeniedException] and its parent [AccessDeniedException]) to the
     * same 403 JSON envelope used for [ForbiddenException]. Without this handler Spring
     * falls back to a plain 403 response that bypasses the API's error-envelope contract.
     */
    @ExceptionHandler(AuthorizationDeniedException::class, AccessDeniedException::class)
    fun handleAuthorizationDenied(ex: RuntimeException): ResponseEntity<ErrorResponse> =
        errorResponse(HttpStatus.FORBIDDEN, "Access denied")

    @ExceptionHandler(MethodArgumentNotValidException::class)
    fun handleValidation(ex: MethodArgumentNotValidException): ResponseEntity<ErrorResponse> {
        val details = ex.bindingResult.fieldErrors.joinToString("; ") { "${it.field}: ${it.defaultMessage}" }
        return errorResponse(HttpStatus.BAD_REQUEST, details.ifBlank { "Validation failed" })
    }

    /**
     * Maps `@Validated` class + `@Min` / `@Max` / `@Pattern` / `@Size` violations
     * on `@RequestParam` and `@PathVariable` arguments to a clean 400 response
     * with the same `ErrorResponse` envelope as [handleValidation] (#430).
     *
     * Without this handler such violations would fall through to
     * [handleUnexpected] and surface as 500 Internal Server Error — confusing
     * callers and breaking the API's documented contract that bad input
     * produces 400.
     *
     * `@Valid @RequestBody` violations go through [handleValidation] (different
     * exception type — `MethodArgumentNotValidException`); the two paths cover
     * the full Bean Validation surface.
     */
    @ExceptionHandler(ConstraintViolationException::class)
    fun handleConstraintViolation(ex: ConstraintViolationException): ResponseEntity<ErrorResponse> {
        val details = ex.constraintViolations
            .joinToString("; ") { violation ->
                // propertyPath is "methodName.paramName" (e.g. "listPlugins.size") —
                // keep only the last segment for a caller-friendly field name.
                val field = violation.propertyPath.toString().substringAfterLast('.')
                "$field: ${violation.message}"
            }
        return errorResponse(HttpStatus.BAD_REQUEST, details.ifBlank { "Validation failed" })
    }

    @ExceptionHandler(HttpMessageNotReadableException::class)
    fun handleUnreadableBody(ex: HttpMessageNotReadableException): ResponseEntity<ErrorResponse> =
        errorResponse(HttpStatus.BAD_REQUEST, "Request body is missing or malformed")

    @ExceptionHandler(IllegalArgumentException::class)
    fun handleIllegalArgument(ex: IllegalArgumentException): ResponseEntity<ErrorResponse> =
        errorResponse(HttpStatus.BAD_REQUEST, ex.message ?: "Invalid request")

    @ExceptionHandler(
        DescriptorNotFoundException::class,
        DescriptorParseException::class,
        DescriptorValidationException::class,
    )
    fun handleDescriptorError(ex: RuntimeException): ResponseEntity<ErrorResponse> =
        errorResponse(HttpStatus.UNPROCESSABLE_ENTITY, ex.message ?: "Plugin descriptor is invalid or missing")

    @ExceptionHandler(InvalidArtifactException::class)
    fun handleInvalidArtifact(ex: InvalidArtifactException): ResponseEntity<ErrorResponse> =
        errorResponse(HttpStatus.UNPROCESSABLE_ENTITY, ex.message ?: "Invalid artifact")

    @ExceptionHandler(FileTooLargeException::class)
    fun handleFileTooLarge(ex: FileTooLargeException): ResponseEntity<ErrorResponse> =
        errorResponse(HttpStatus.PAYLOAD_TOO_LARGE, ex.message ?: "File too large")

    @ExceptionHandler(MaxUploadSizeExceededException::class)
    fun handleMaxUploadSize(ex: MaxUploadSizeExceededException): ResponseEntity<ErrorResponse> =
        errorResponse(HttpStatus.PAYLOAD_TOO_LARGE, "Upload exceeds the maximum allowed file size")

    @ExceptionHandler(ArtifactStorageException::class)
    fun handleStorage(ex: ArtifactStorageException): ResponseEntity<ErrorResponse> {
        log.error("Artifact storage error", ex)
        return errorResponse(HttpStatus.INTERNAL_SERVER_ERROR, "Storage operation failed")
    }

    @ExceptionHandler(Exception::class)
    fun handleUnexpected(ex: Exception): ResponseEntity<ErrorResponse> {
        log.error("Unexpected error", ex)
        return errorResponse(HttpStatus.INTERNAL_SERVER_ERROR, "An unexpected error occurred")
    }

    private fun errorResponse(status: HttpStatus, message: String): ResponseEntity<ErrorResponse> =
        ResponseEntity.status(status).body(
            ErrorResponse(
                status = status.value(),
                error = status.reasonPhrase,
                message = message,
                timestamp = OffsetDateTime.now(),
            ),
        )
}
