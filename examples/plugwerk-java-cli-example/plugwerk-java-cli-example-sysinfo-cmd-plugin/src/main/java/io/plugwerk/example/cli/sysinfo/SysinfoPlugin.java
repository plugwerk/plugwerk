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
package io.plugwerk.example.cli.sysinfo;

import org.pf4j.Plugin;

/**
 * PF4J plugin entry point for the sysinfo-cli-plugin.
 *
 * <p>Contributes the {@link SysinfoCommand} subcommand to the CLI host application via the {@link
 * io.plugwerk.example.cli.api.CliCommand} extension point.
 */
public class SysinfoPlugin extends Plugin {}
