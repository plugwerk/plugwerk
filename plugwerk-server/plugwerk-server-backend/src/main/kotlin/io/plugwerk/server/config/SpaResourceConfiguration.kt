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

import org.springframework.context.annotation.Configuration
import org.springframework.core.io.ClassPathResource
import org.springframework.core.io.Resource
import org.springframework.web.servlet.config.annotation.ResourceHandlerRegistry
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer
import org.springframework.web.servlet.resource.PathResourceResolver

/**
 * SPA fallback: serves index.html for any URL that does not resolve to an existing
 * static asset. This lets React Router handle client-side routes on deep links and
 * browser reloads (e.g. /admin/namespaces/acme, /profile, /onboarding).
 *
 * REST controller mappings under /api, /actuator, and /api-docs are matched before
 * this resolver runs, so they are not affected.
 */
@Configuration
class SpaResourceConfiguration : WebMvcConfigurer {
    override fun addResourceHandlers(registry: ResourceHandlerRegistry) {
        registry
            .addResourceHandler("/**")
            .addResourceLocations("classpath:/static/")
            .resourceChain(true)
            .addResolver(SpaPathResourceResolver())
    }

    private class SpaPathResourceResolver : PathResourceResolver() {
        private val indexHtml: Resource = ClassPathResource("/static/index.html")

        override fun getResource(resourcePath: String, location: Resource): Resource? {
            val requested = location.createRelative(resourcePath)
            return if (requested.exists() && requested.isReadable) requested else indexHtml
        }
    }
}
