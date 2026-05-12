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

import io.plugwerk.api.AdminStorageConsistencyApi
import io.plugwerk.api.model.BulkArtifactDeletionResult as ApiBulkArtifactDeletionResult
import io.plugwerk.api.model.ConsistencyReport as ApiConsistencyReport
import io.plugwerk.api.model.MissingArtifact as ApiMissingArtifact
import io.plugwerk.api.model.OrphanedArtifact as ApiOrphanedArtifact
import io.plugwerk.api.model.OrphanedArtifactDeletionRequest
import io.plugwerk.server.security.NamespaceAuthorizationService
import io.plugwerk.server.security.currentAuthentication
import io.plugwerk.server.service.storage.consistency.StorageConsistencyAdminService
import io.plugwerk.server.service.storage.consistency.StorageConsistencyService
import io.plugwerk.server.service.storage.consistency.StorageScanLimitExceededException
import org.springframework.http.HttpStatus
import org.springframework.http.ResponseEntity
import org.springframework.security.access.prepost.PreAuthorize
import org.springframework.web.bind.annotation.ExceptionHandler
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RestController
import java.util.UUID

/**
 * Admin endpoints for the storage-vs-DB consistency check (#190).
 *
 * Every operation is gated by superadmin via [PreAuthorize]; the generated
 * `AdminStorageConsistencyApi` is the OpenAPI-derived contract.
 */
@RestController
@RequestMapping("/api/v1")
class AdminStorageConsistencyController(
    private val consistencyService: StorageConsistencyService,
    private val adminService: StorageConsistencyAdminService,
    private val namespaceAuthorizationService: NamespaceAuthorizationService,
) : AdminStorageConsistencyApi {

    @PreAuthorize("@namespaceAuthorizationService.isCurrentUserSuperadmin()")
    override fun getStorageConsistencyReport(): ResponseEntity<ApiConsistencyReport> {
        namespaceAuthorizationService.requireSuperadmin(currentAuthentication())
        val report = consistencyService.scan()
        return ResponseEntity.ok(
            ApiConsistencyReport(
                missingArtifacts = report.missingArtifacts.map {
                    ApiMissingArtifact(
                        releaseId = it.releaseId,
                        pluginId = it.pluginId,
                        version = it.version,
                        artifactKey = it.artifactKey,
                    )
                },
                orphanedArtifacts = report.orphanedArtifacts.map {
                    ApiOrphanedArtifact(
                        key = it.key,
                        lastModified = it.lastModified.atOffset(java.time.ZoneOffset.UTC),
                        ageHours = it.ageHours,
                        sizeBytes = it.sizeBytes,
                    )
                },
                scannedAt = report.scannedAt.atOffset(java.time.ZoneOffset.UTC),
                totalDbRows = report.totalDbRows,
                totalStorageObjects = report.totalStorageObjects,
            ),
        )
    }

    @PreAuthorize("@namespaceAuthorizationService.isCurrentUserSuperadmin()")
    override fun deleteOrphanedRelease(releaseId: UUID): ResponseEntity<Unit> {
        namespaceAuthorizationService.requireSuperadmin(currentAuthentication())
        adminService.deleteOrphanedRelease(releaseId)
        return ResponseEntity.noContent().build()
    }

    @PreAuthorize("@namespaceAuthorizationService.isCurrentUserSuperadmin()")
    override fun deleteOrphanedArtifacts(
        orphanedArtifactDeletionRequest: OrphanedArtifactDeletionRequest,
    ): ResponseEntity<ApiBulkArtifactDeletionResult> {
        namespaceAuthorizationService.requireSuperadmin(currentAuthentication())
        val result = adminService.deleteOrphanedArtifacts(orphanedArtifactDeletionRequest.propertyKeys)
        return ResponseEntity.ok(
            ApiBulkArtifactDeletionResult(deleted = result.deleted, skipped = result.skipped),
        )
    }

    /**
     * Maps the circuit-breaker exception from `StorageConsistencyService` to
     * `409 CONFLICT` as documented in the OpenAPI spec — keeps the admin UI
     * from hanging on a 10M-key bucket.
     */
    @ExceptionHandler(StorageScanLimitExceededException::class)
    fun handleScanLimit(ex: StorageScanLimitExceededException): ResponseEntity<Map<String, Any>> =
        ResponseEntity.status(HttpStatus.CONFLICT).body(
            mapOf(
                "status" to 409,
                "error" to "Conflict",
                "message" to (ex.message ?: "Storage scan aborted by max-keys-per-scan circuit breaker"),
                "limit" to ex.limit,
                "scannedSoFar" to ex.scannedSoFar,
            ),
        )
}
