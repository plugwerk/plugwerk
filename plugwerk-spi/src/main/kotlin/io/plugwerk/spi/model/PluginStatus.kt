/*
 * Copyright (c) 2025-present devtank42 GmbH
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
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
