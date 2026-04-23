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

import io.plugwerk.server.service.settings.ApplicationSettingsService
import io.plugwerk.server.service.settings.MAX_ALLOWED_UPLOAD_MB
import jakarta.servlet.MultipartConfigElement
import org.slf4j.LoggerFactory
import org.springframework.boot.servlet.MultipartConfigFactory
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.util.unit.DataSize

/**
 * Wires Spring's [MultipartConfigElement] from the DB-backed setting
 * `upload.max_file_size_mb` (ADR-0016).
 *
 * The bean is built at context refresh time — **after** Liquibase has run and
 * `ApplicationSettingsService.@PostConstruct` has populated its cache — but **before** Tomcat
 * registers the multipart filter. Spring Boot then uses this element to configure the
 * servlet container's max file size and max request size.
 *
 * **Runtime changes require a restart.** The `MultipartConfigElement` is read once when
 * the servlet is registered; changing the DB value afterwards updates the in-memory cache
 * but does not reconfigure the servlet. The Admin UI displays a "restart pending" notice
 * whenever the DB value has diverged from the boot value (see
 * [ApplicationSettingsService.listAll] and `SettingSnapshot.restartPending`). A follow-up
 * issue tracks runtime reconfiguration.
 *
 * A hard safety ceiling of [MAX_ALLOWED_UPLOAD_MB] MB is always applied on top
 * of the DB value — if someone writes a larger number directly, the multipart filter still
 * caps at the ceiling.
 */
@Configuration
class MultipartConfig {

    private val log = LoggerFactory.getLogger(MultipartConfig::class.java)

    @Bean
    fun multipartConfigElement(settingsService: ApplicationSettingsService): MultipartConfigElement {
        val dbValue = settingsService.maxUploadSizeMb()
        val effective = dbValue.coerceAtMost(MAX_ALLOWED_UPLOAD_MB)
        if (effective != dbValue) {
            log.warn(
                "upload.max_file_size_mb={} exceeds hard ceiling {}; capping",
                dbValue,
                MAX_ALLOWED_UPLOAD_MB,
            )
        }
        log.info("Configuring Servlet multipart limits to {} MB (from application_setting)", effective)
        val factory = MultipartConfigFactory()
        factory.setMaxFileSize(DataSize.ofMegabytes(effective.toLong()))
        factory.setMaxRequestSize(DataSize.ofMegabytes(effective.toLong()))
        return factory.createMultipartConfig()
    }
}
