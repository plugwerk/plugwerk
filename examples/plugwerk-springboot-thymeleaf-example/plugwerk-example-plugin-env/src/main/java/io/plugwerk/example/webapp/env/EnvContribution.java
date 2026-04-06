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
package io.plugwerk.example.webapp.env;

import io.plugwerk.example.webapp.api.PageContribution;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;
import org.pf4j.Extension;

/**
 * Contributes an "Environment" page that displays environment variables.
 *
 * <p>Values for variables whose names contain sensitive keywords (SECRET, PASSWORD, KEY, TOKEN) are
 * masked to prevent accidental exposure.
 */
@Extension
public class EnvContribution implements PageContribution {

  private static final Set<String> SENSITIVE_SUFFIXES =
      Set.of("_SECRET", "_PASSWORD", "_KEY", "_TOKEN");

  @Override
  public String getMenuLabel() {
    return "Environment";
  }

  @Override
  public String getRoute() {
    return "env";
  }

  @Override
  public String getTitle() {
    return "Environment Variables";
  }

  @Override
  public String renderHtml() {
    Map<String, String> sortedEnv = new TreeMap<>(System.getenv());

    StringBuilder html = new StringBuilder();
    html.append(
        """
        <table style="width:100%%;border-collapse:collapse;background:#fff;border-radius:8px;\
        overflow:hidden;box-shadow:0 1px 3px rgba(0,0,0,0.08)">
          <thead>
            <tr>
              <th style="text-align:left;padding:0.75rem 1rem;font-size:0.75rem;font-weight:600;\
        text-transform:uppercase;letter-spacing:0.05em;color:#6c757d;background:#f8f9fa;\
        border-bottom:2px solid #dee2e6;width:300px">Variable</th>
              <th style="text-align:left;padding:0.75rem 1rem;font-size:0.75rem;font-weight:600;\
        text-transform:uppercase;letter-spacing:0.05em;color:#6c757d;background:#f8f9fa;\
        border-bottom:2px solid #dee2e6">Value</th>
            </tr>
          </thead>
          <tbody>
        """);

    for (Map.Entry<String, String> entry : sortedEnv.entrySet()) {
      String name = escapeHtml(entry.getKey());
      String value = isSensitive(entry.getKey()) ? "********" : escapeHtml(entry.getValue());
      html.append(
          "<tr><td style=\"padding:0.75rem 1rem;border-bottom:1px solid #eee;"
              + "font-family:monospace;font-size:0.8125rem;font-weight:500\">");
      html.append(name);
      html.append(
          "</td><td style=\"padding:0.75rem 1rem;border-bottom:1px solid #eee;"
              + "font-family:monospace;font-size:0.8125rem;word-break:break-all\">");
      html.append(value);
      html.append("</td></tr>\n");
    }

    html.append("</tbody></table>");
    return html.toString();
  }

  private static boolean isSensitive(String name) {
    String upper = name.toUpperCase(Locale.ROOT);
    return SENSITIVE_SUFFIXES.stream().anyMatch(upper::endsWith);
  }

  private static String escapeHtml(String text) {
    if (text == null) {
      return "";
    }
    return text.replace("&", "&amp;")
        .replace("<", "&lt;")
        .replace(">", "&gt;")
        .replace("\"", "&quot;");
  }
}
