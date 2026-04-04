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
package io.plugwerk.spi.model

/**
 * Lifecycle state of a plugin in the Plugwerk catalog.
 *
 * The status controls visibility and installability:
 * - Only [ACTIVE] plugins are returned by default catalog listings.
 * - [SUSPENDED] and [ARCHIVED] plugins can still be retrieved by ID but are hidden from search results.
 */
enum class PluginStatus {
    /** The plugin is published, visible in the catalog, and available for installation. */
    ACTIVE,

    /**
     * The plugin is temporarily unavailable, e.g. due to a policy violation under review.
     * Existing installations continue to work but new installs are blocked.
     */
    SUSPENDED,

    /**
     * The plugin has been retired and is no longer actively maintained.
     * It remains in the catalog for reference but is excluded from search results and update checks.
     */
    ARCHIVED,
}
