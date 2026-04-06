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
package io.plugwerk.example.webapp.api;

import org.pf4j.ExtensionPoint;

/**
 * Extension point for plugins that contribute a page to the web application.
 *
 * <p>Each {@code PageContribution} adds a menu entry and a routable page to the Spring Boot host
 * application. The host renders the HTML returned by {@link #renderHtml()} inside a shared
 * Thymeleaf layout.
 *
 * <p>Example implementation:
 *
 * <pre>{@code
 * @Extension
 * public class StatusContribution implements PageContribution {
 *     public String getMenuLabel() { return "Status"; }
 *     public String getRoute()     { return "status"; }
 *     public String getTitle()     { return "System Status"; }
 *     public String renderHtml()   { return "<p>All systems operational.</p>"; }
 * }
 * }</pre>
 */
public interface PageContribution extends ExtensionPoint {

  /**
   * Returns the label displayed in the navigation menu.
   *
   * @return human-readable menu label; must not be {@code null} or blank
   */
  String getMenuLabel();

  /**
   * Returns the URL path segment for this page (e.g. {@code "sysinfo"}).
   *
   * <p>The page will be accessible at {@code /{route}}. The route must be unique across all
   * installed plugins and must not conflict with built-in paths ({@code /plugins/*}).
   *
   * @return URL-safe path segment; must not be {@code null} or blank
   */
  String getRoute();

  /**
   * Returns the page title displayed in the browser tab and the page header.
   *
   * @return human-readable page title; must not be {@code null} or blank
   */
  String getTitle();

  /**
   * Returns the HTML content for the page body.
   *
   * <p>The returned HTML is embedded inside the host application's Thymeleaf layout template. It
   * should be a fragment (no {@code <html>} or {@code <body>} wrapper). Inline styles are allowed
   * for self-contained rendering.
   *
   * @return HTML fragment for the page body; must not be {@code null}
   */
  String renderHtml();
}
