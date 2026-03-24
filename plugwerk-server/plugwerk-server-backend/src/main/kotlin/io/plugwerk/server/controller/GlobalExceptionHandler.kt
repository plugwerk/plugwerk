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
package io.plugwerk.server.controller

import io.plugwerk.api.model.ErrorResponse
import io.plugwerk.descriptor.DescriptorNotFoundException
import io.plugwerk.descriptor.DescriptorParseException
import io.plugwerk.descriptor.DescriptorValidationException
import io.plugwerk.server.service.ArtifactNotFoundException
import io.plugwerk.server.service.ArtifactStorageException
import io.plugwerk.server.service.NamespaceAlreadyExistsException
import io.plugwerk.server.service.NamespaceNotFoundException
import io.plugwerk.server.service.PluginAlreadyExistsException
import io.plugwerk.server.service.PluginNotFoundException
import io.plugwerk.server.service.ReleaseAlreadyExistsException
import io.plugwerk.server.service.ReleaseNotFoundException
import org.slf4j.LoggerFactory
import org.springframework.http.HttpStatus
import org.springframework.http.ResponseEntity
import org.springframework.http.converter.HttpMessageNotReadableException
import org.springframework.web.bind.MethodArgumentNotValidException
import org.springframework.web.bind.annotation.ExceptionHandler
import org.springframework.web.bind.annotation.RestControllerAdvice
import java.time.OffsetDateTime

@RestControllerAdvice
class GlobalExceptionHandler {

    private val log = LoggerFactory.getLogger(GlobalExceptionHandler::class.java)

    @ExceptionHandler(
        NamespaceNotFoundException::class,
        PluginNotFoundException::class,
        ReleaseNotFoundException::class,
        ArtifactNotFoundException::class,
    )
    fun handleNotFound(ex: RuntimeException): ResponseEntity<ErrorResponse> =
        errorResponse(HttpStatus.NOT_FOUND, ex.message ?: "Resource not found")

    @ExceptionHandler(
        NamespaceAlreadyExistsException::class,
        PluginAlreadyExistsException::class,
        ReleaseAlreadyExistsException::class,
    )
    fun handleConflict(ex: RuntimeException): ResponseEntity<ErrorResponse> =
        errorResponse(HttpStatus.CONFLICT, ex.message ?: "Resource already exists")

    @ExceptionHandler(MethodArgumentNotValidException::class)
    fun handleValidation(ex: MethodArgumentNotValidException): ResponseEntity<ErrorResponse> {
        val details = ex.bindingResult.fieldErrors.joinToString("; ") { "${it.field}: ${it.defaultMessage}" }
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
