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
 * Lifecycle state of a specific plugin release.
 *
 * The status determines whether a release is installable:
 * - [PUBLISHED] is the only installable state; all others are excluded from update checks.
 * - [DRAFT] releases are only accessible to namespace maintainers.
 * - [DEPRECATED] releases remain downloadable but are not recommended for new installations.
 * - [YANKED] releases are considered unsafe and are blocked from installation.
 */
enum class ReleaseStatus {
    /**
     * The release has been uploaded but not yet approved for public use.
     * Only visible to namespace maintainers; not returned in public catalog queries.
     */
    DRAFT,

    /**
     * The release is publicly available and recommended for installation.
     * This is the only status that appears in update check results.
     */
    PUBLISHED,

    /**
     * The release is superseded by a newer version and is no longer recommended.
     * It can still be explicitly downloaded by its version string but is excluded
     * from update check results.
     */
    DEPRECATED,

    /**
     * The release has been pulled due to a critical bug or security vulnerability.
     * Installation is blocked; existing users should upgrade immediately.
     */
    YANKED,
}
