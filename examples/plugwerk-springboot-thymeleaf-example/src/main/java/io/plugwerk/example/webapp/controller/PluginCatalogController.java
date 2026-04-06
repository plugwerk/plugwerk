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
package io.plugwerk.example.webapp.controller;

import io.plugwerk.example.webapp.config.PluginContributionRegistry;
import io.plugwerk.spi.extension.PlugwerkMarketplace;
import io.plugwerk.spi.model.InstallResult;
import io.plugwerk.spi.model.PluginInfo;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;
import org.pf4j.PluginManager;
import org.pf4j.PluginWrapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.lang.Nullable;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

/** Lists available plugins from the Plugwerk server and handles install requests. */
@Controller
@RequestMapping("/plugins")
public class PluginCatalogController {

  private static final Logger log = LoggerFactory.getLogger(PluginCatalogController.class);

  private final PluginManager pluginManager;
  private final @Nullable PlugwerkMarketplace marketplace;
  private final PluginContributionRegistry registry;

  public PluginCatalogController(
      PluginManager pluginManager,
      @Nullable PlugwerkMarketplace marketplace,
      PluginContributionRegistry registry) {
    this.pluginManager = pluginManager;
    this.marketplace = marketplace;
    this.registry = registry;
  }

  @GetMapping("/available")
  public String available(Model model) {
    model.addAttribute("contributions", registry.getContributions());

    Set<String> installedIds =
        pluginManager.getPlugins().stream()
            .map(PluginWrapper::getPluginId)
            .collect(Collectors.toSet());
    model.addAttribute("installedIds", installedIds);

    if (marketplace == null) {
      model.addAttribute("plugins", List.of());
      model.addAttribute("serverUnavailable", true);
      return "plugins-available";
    }

    try {
      List<PluginInfo> plugins = marketplace.catalog().listPlugins();
      model.addAttribute("plugins", plugins);
      model.addAttribute("serverUnavailable", false);
    } catch (Exception e) {
      log.error("Failed to fetch plugin catalog", e);
      model.addAttribute("plugins", List.of());
      model.addAttribute("serverUnavailable", true);
      model.addAttribute("errorMessage", "Failed to connect to Plugwerk server: " + e.getMessage());
    }

    return "plugins-available";
  }

  @PostMapping("/install")
  public String install(
      @RequestParam String pluginId,
      @RequestParam String version,
      RedirectAttributes redirectAttributes) {

    if (marketplace == null) {
      redirectAttributes.addFlashAttribute("errorMessage", "Plugwerk server is not configured.");
      return "redirect:/plugins/available";
    }

    try {
      InstallResult result = marketplace.installer().install(pluginId, version);
      if (result instanceof InstallResult.Success s) {
        // Load and start the newly downloaded plugin so PF4J recognises it
        pluginManager.loadPlugins();
        pluginManager.startPlugins();
        registry.refresh();
        redirectAttributes.addFlashAttribute(
            "successMessage", "Successfully installed " + s.getPluginId() + "@" + s.getVersion());
      } else if (result instanceof InstallResult.Failure f) {
        redirectAttributes.addFlashAttribute(
            "errorMessage", "Installation failed: " + f.getReason());
      }
    } catch (Exception e) {
      log.error("Failed to install plugin {}@{}", pluginId, version, e);
      redirectAttributes.addFlashAttribute(
          "errorMessage", "Installation failed: " + e.getMessage());
    }

    return "redirect:/plugins/installed";
  }
}
