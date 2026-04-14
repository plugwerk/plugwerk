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
import io.plugwerk.spi.PlugwerkPlugin;
import io.plugwerk.spi.extension.PlugwerkMarketplace;
import io.plugwerk.spi.model.UpdateInfo;
import java.util.List;
import java.util.Map;
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

/** Displays installed plugins and handles uninstall/update operations. */
@Controller
@RequestMapping("/plugins")
public class PluginInstalledController {

  private static final Logger log = LoggerFactory.getLogger(PluginInstalledController.class);

  private final PluginManager pluginManager;
  private final @Nullable PlugwerkMarketplace marketplace;
  private final PluginContributionRegistry registry;

  public PluginInstalledController(
      PluginManager pluginManager,
      @Nullable PlugwerkMarketplace marketplace,
      PluginContributionRegistry registry) {
    this.pluginManager = pluginManager;
    this.marketplace = marketplace;
    this.registry = registry;
  }

  @GetMapping("/installed")
  public String installed(Model model) {
    model.addAttribute("contributions", registry.getContributions());

    // Exclude the SDK plugin from the display — it is an infrastructure concern
    List<PluginWrapper> plugins =
        pluginManager.getPlugins().stream()
            .filter(p -> !PlugwerkPlugin.PLUGIN_ID.equals(p.getPluginId()))
            .toList();
    model.addAttribute("plugins", plugins);

    // Check for available updates if the marketplace is configured
    if (marketplace != null && !plugins.isEmpty()) {
      try {
        Map<String, String> installed =
            plugins.stream()
                .collect(
                    Collectors.toMap(
                        PluginWrapper::getPluginId, p -> p.getDescriptor().getVersion()));
        List<UpdateInfo> updates = marketplace.updateChecker().checkForUpdates(installed);
        Map<String, String> updateMap =
            updates.stream()
                .collect(
                    Collectors.toMap(UpdateInfo::getPluginId, UpdateInfo::getAvailableVersion));
        model.addAttribute("updates", updateMap);
      } catch (Exception e) {
        log.warn("Failed to check for updates", e);
        model.addAttribute("updates", Map.of());
      }
    } else {
      model.addAttribute("updates", Map.of());
    }

    return "plugins-installed";
  }

  @PostMapping("/uninstall")
  public String uninstall(@RequestParam String pluginId, RedirectAttributes redirectAttributes) {

    if (marketplace == null) {
      redirectAttributes.addFlashAttribute("errorMessage", "Plugwerk server is not configured.");
      return "redirect:/plugins/installed";
    }

    try {
      // Stop and unload the plugin from PF4J before removing the file
      pluginManager.stopPlugin(pluginId);
      pluginManager.unloadPlugin(pluginId);

      marketplace
          .installer()
          .uninstall(pluginId)
          .onSuccess(
              s -> {
                registry.refresh();
                redirectAttributes.addFlashAttribute(
                    "successMessage", "Successfully uninstalled " + s.getPluginId());
              })
          .onFailure(
              f ->
                  redirectAttributes.addFlashAttribute(
                      "errorMessage", "Uninstall failed: " + f.getReason()));
    } catch (Exception e) {
      log.error("Failed to uninstall plugin {}", pluginId, e);
      redirectAttributes.addFlashAttribute("errorMessage", "Uninstall failed: " + e.getMessage());
    }

    return "redirect:/plugins/installed";
  }

  @PostMapping("/update")
  public String update(
      @RequestParam String pluginId,
      @RequestParam String version,
      RedirectAttributes redirectAttributes) {

    if (marketplace == null) {
      redirectAttributes.addFlashAttribute("errorMessage", "Plugwerk server is not configured.");
      return "redirect:/plugins/installed";
    }

    try {
      // Uninstall the current version, then install the new one
      marketplace.installer().uninstall(pluginId);
      marketplace
          .installer()
          .install(pluginId, version)
          .onSuccess(
              s -> {
                pluginManager.loadPlugins();
                pluginManager.startPlugins();
                registry.refresh();
                redirectAttributes.addFlashAttribute(
                    "successMessage",
                    "Successfully updated " + s.getPluginId() + " to " + s.getVersion());
              })
          .onFailure(
              f ->
                  redirectAttributes.addFlashAttribute(
                      "errorMessage", "Update failed: " + f.getReason()));
    } catch (Exception e) {
      log.error("Failed to update plugin {}@{}", pluginId, version, e);
      redirectAttributes.addFlashAttribute("errorMessage", "Update failed: " + e.getMessage());
    }

    return "redirect:/plugins/installed";
  }
}
