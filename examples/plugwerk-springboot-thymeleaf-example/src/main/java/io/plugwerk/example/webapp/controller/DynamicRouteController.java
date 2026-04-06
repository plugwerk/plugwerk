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

import io.plugwerk.example.webapp.api.PageContribution;
import io.plugwerk.example.webapp.config.PluginContributionRegistry;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.server.ResponseStatusException;

/**
 * Routes dynamic page requests to the matching {@link PageContribution} extension.
 *
 * <p>This controller has a low priority (catch-all {@code /{route}}) and only matches routes that
 * are registered by installed plugins. Unknown routes result in a 404.
 */
@Controller
public class DynamicRouteController {

  private final PluginContributionRegistry registry;

  public DynamicRouteController(PluginContributionRegistry registry) {
    this.registry = registry;
  }

  @GetMapping("/page/{route}")
  public String dynamicPage(@PathVariable String route, Model model) {
    PageContribution contribution =
        registry
            .findByRoute(route)
            .orElseThrow(
                () ->
                    new ResponseStatusException(
                        HttpStatus.NOT_FOUND, "No plugin page found for route: " + route));

    model.addAttribute("contributions", registry.getContributions());
    model.addAttribute("pageTitle", contribution.getTitle());
    model.addAttribute("content", contribution.renderHtml());

    return "dynamic-page";
  }
}
