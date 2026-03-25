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
package io.plugwerk.server.service

class NamespaceNotFoundException(slug: String) : RuntimeException("Namespace not found: $slug")

class NamespaceAlreadyExistsException(slug: String) : RuntimeException("Namespace already exists: $slug")

class PluginNotFoundException(namespaceSlug: String, pluginId: String) :
    RuntimeException("Plugin not found: $pluginId in namespace $namespaceSlug")

class PluginAlreadyExistsException(namespaceSlug: String, pluginId: String) :
    RuntimeException("Plugin already exists: $pluginId in namespace $namespaceSlug")

class ReleaseAlreadyExistsException(pluginId: String, version: String) :
    RuntimeException("Release already exists: $pluginId@$version")

class ReleaseNotFoundException(pluginId: String, version: String) :
    RuntimeException("Release not found: $pluginId@$version")

class ArtifactNotFoundException(key: String) : RuntimeException("Artifact not found: $key")

class ArtifactStorageException(message: String, cause: Throwable? = null) : RuntimeException(message, cause)

class EntityNotFoundException(entity: String, id: String) : RuntimeException("$entity not found: $id")

class ConflictException(message: String) : RuntimeException(message)

class UnauthorizedException(message: String) : RuntimeException(message)

class ForbiddenException(message: String) : RuntimeException(message)
