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
