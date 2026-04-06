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
package io.plugwerk.example.webapp.config;

import io.plugwerk.example.webapp.api.PageContribution;
import java.util.List;
import java.util.Optional;
import org.pf4j.PluginManager;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

/**
 * Registry of {@link PageContribution} extensions discovered from installed PF4J plugins.
 *
 * <p>Call {@link #refresh()} after installing or uninstalling a plugin to re-scan extensions.
 */
@Component
public class PluginContributionRegistry {

  private static final Logger log = LoggerFactory.getLogger(PluginContributionRegistry.class);

  private final PluginManager pluginManager;
  private volatile List<PageContribution> contributions;

  public PluginContributionRegistry(PluginManager pluginManager) {
    this.pluginManager = pluginManager;
    refresh();
  }

  /** Re-scans all installed plugins for {@link PageContribution} extensions. */
  public void refresh() {
    contributions = List.copyOf(pluginManager.getExtensions(PageContribution.class));
    log.info(
        "Discovered {} page contribution(s): {}",
        contributions.size(),
        contributions.stream().map(c -> c.getRoute() + " (" + c.getMenuLabel() + ")").toList());
  }

  /** Returns all currently registered page contributions. */
  public List<PageContribution> getContributions() {
    return contributions;
  }

  /** Finds a page contribution by its route segment. */
  public Optional<PageContribution> findByRoute(String route) {
    return contributions.stream().filter(c -> c.getRoute().equals(route)).findFirst();
  }
}
