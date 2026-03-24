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
package io.plugwerk.client

import org.pf4j.Plugin

/**
 * PF4J plugin entry point for the Plugwerk Client SDK.
 *
 * This class is referenced in `MANIFEST.MF` via `Plugin-Class`. PF4J instantiates it
 * when the SDK JAR is loaded as a plugin. The actual SDK functionality is exposed through
 * [PlugwerkMarketplaceImpl], which is discovered via PF4J's `@Extension` mechanism.
 */
class PlugwerkMarketplacePlugin : Plugin()
