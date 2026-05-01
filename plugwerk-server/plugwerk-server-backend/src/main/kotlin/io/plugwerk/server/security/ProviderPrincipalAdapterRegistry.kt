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
package io.plugwerk.server.security

import io.plugwerk.server.domain.OidcProviderType
import org.springframework.stereotype.Component

/**
 * Lookup boundary for [ProviderPrincipalAdapter] implementations. Builds a
 * `OidcProviderType → adapter` map at construction time from every
 * [ProviderPrincipalAdapter] bean Spring discovers, fails fast on duplicates
 * (two adapters claiming the same provider type) and on lookups for
 * unconfigured types (the early signal a new provider type was added without
 * a matching adapter, see [OidcProviderType] vs adapter coverage).
 */
@Component
class ProviderPrincipalAdapterRegistry(adapters: List<ProviderPrincipalAdapter>) {

    private val byType: Map<OidcProviderType, ProviderPrincipalAdapter> = buildMap {
        for (adapter in adapters) {
            for (type in adapter.providerTypes) {
                val previous = put(type, adapter)
                require(previous == null) {
                    "Multiple ProviderPrincipalAdapter beans claim provider type $type: " +
                        "${previous!!.javaClass.simpleName} and ${adapter.javaClass.simpleName}. " +
                        "Each provider type must map to exactly one adapter."
                }
            }
        }
    }

    /**
     * Returns the adapter for [providerType]. Throws [IllegalStateException]
     * with an actionable message if no adapter is registered — that signals a
     * new entry in [OidcProviderType] without a matching adapter implementation.
     */
    fun forProviderType(providerType: OidcProviderType): ProviderPrincipalAdapter = byType[providerType]
        ?: error(
            "No ProviderPrincipalAdapter registered for provider type $providerType — " +
                "browser login flow is not implemented for this provider type yet. " +
                "See issue #357 for the rollout plan.",
        )
}
