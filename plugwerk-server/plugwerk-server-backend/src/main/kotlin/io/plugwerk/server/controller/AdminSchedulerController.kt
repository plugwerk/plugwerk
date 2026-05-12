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

import io.plugwerk.api.AdminSchedulerApi
import io.plugwerk.api.model.SchedulerJobUpdateRequest
import io.plugwerk.server.security.NamespaceAuthorizationService
import io.plugwerk.server.security.currentAuthentication
import io.plugwerk.server.service.scheduler.SchedulerJobAdminService
import org.springframework.http.ResponseEntity
import org.springframework.security.access.prepost.PreAuthorize
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RestController
import java.time.ZoneOffset
import io.plugwerk.api.model.SchedulerJobDto as ApiSchedulerJobDto
import io.plugwerk.api.model.SchedulerJobOutcome as ApiSchedulerJobOutcome
import io.plugwerk.api.model.SchedulerJobRunResult as ApiSchedulerJobRunResult
import io.plugwerk.server.domain.SchedulerJobOutcome as DomainSchedulerJobOutcome

/**
 * Admin endpoints backing the scheduler control-plane dashboard (#516).
 *
 * Every operation is gated by superadmin via [PreAuthorize] plus an
 * inline `requireSuperadmin` defense-in-depth call. The generated
 * [AdminSchedulerApi] is the OpenAPI-derived contract.
 */
@RestController
@RequestMapping("/api/v1")
class AdminSchedulerController(
    private val adminService: SchedulerJobAdminService,
    private val namespaceAuthorizationService: NamespaceAuthorizationService,
) : AdminSchedulerApi {

    @PreAuthorize("@namespaceAuthorizationService.isCurrentUserSuperadmin()")
    override fun listSchedulerJobs(): ResponseEntity<List<ApiSchedulerJobDto>> {
        namespaceAuthorizationService.requireSuperadmin(currentAuthentication())
        return ResponseEntity.ok(adminService.listJobs().map { it.toDto() })
    }

    @PreAuthorize("@namespaceAuthorizationService.isCurrentUserSuperadmin()")
    override fun updateSchedulerJob(
        name: String,
        schedulerJobUpdateRequest: SchedulerJobUpdateRequest,
    ): ResponseEntity<ApiSchedulerJobDto> {
        namespaceAuthorizationService.requireSuperadmin(currentAuthentication())
        val updated = adminService.update(
            name = name,
            enabled = schedulerJobUpdateRequest.enabled,
            dryRun = schedulerJobUpdateRequest.dryRun,
        )
        return ResponseEntity.ok(updated.toDto())
    }

    @PreAuthorize("@namespaceAuthorizationService.isCurrentUserSuperadmin()")
    override fun runSchedulerJobNow(name: String): ResponseEntity<ApiSchedulerJobRunResult> {
        namespaceAuthorizationService.requireSuperadmin(currentAuthentication())
        val outcome = adminService.runNow(name)
        return ResponseEntity.ok(
            ApiSchedulerJobRunResult(
                name = name,
                outcome = outcome.outcome.toApi(),
                durationMs = outcome.durationMs,
                message = outcome.message,
            ),
        )
    }

    private fun SchedulerJobAdminService.JobView.toDto(): ApiSchedulerJobDto = ApiSchedulerJobDto(
        name = descriptor.name,
        description = descriptor.description,
        cronExpression = descriptor.cronExpression,
        supportsDryRun = descriptor.supportsDryRun,
        enabled = state.enabled,
        runCountTotal = state.runCountTotal,
        dryRun = state.dryRun,
        lastRunAt = state.lastRunAt?.atZoneSameInstant(ZoneOffset.UTC)?.toOffsetDateTime(),
        lastRunOutcome = state.lastRunOutcome?.toApi(),
        lastRunDurationMs = state.lastRunDurationMs,
        lastRunMessage = state.lastRunMessage,
    )

    private fun DomainSchedulerJobOutcome.toApi(): ApiSchedulerJobOutcome = when (this) {
        DomainSchedulerJobOutcome.SUCCESS -> ApiSchedulerJobOutcome.SUCCESS
        DomainSchedulerJobOutcome.FAILED -> ApiSchedulerJobOutcome.FAILED
        DomainSchedulerJobOutcome.SKIPPED_DISABLED -> ApiSchedulerJobOutcome.SKIPPED_DISABLED
        DomainSchedulerJobOutcome.SKIPPED_LOCK -> ApiSchedulerJobOutcome.SKIPPED_LOCK
        DomainSchedulerJobOutcome.ABORTED_LIMIT -> ApiSchedulerJobOutcome.ABORTED_LIMIT
    }
}
