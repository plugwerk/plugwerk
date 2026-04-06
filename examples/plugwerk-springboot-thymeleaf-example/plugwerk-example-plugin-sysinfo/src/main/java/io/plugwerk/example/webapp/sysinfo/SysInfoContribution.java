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
package io.plugwerk.example.webapp.sysinfo;

import io.plugwerk.example.webapp.api.PageContribution;
import java.lang.management.ManagementFactory;
import java.time.Duration;
import org.pf4j.Extension;

/**
 * Contributes a "SysInfo" page that displays JVM and OS information.
 *
 * <p>Shows CPU count, OS details, JVM memory usage, and uptime.
 */
@Extension
public class SysInfoContribution implements PageContribution {

  @Override
  public String getMenuLabel() {
    return "SysInfo";
  }

  @Override
  public String getRoute() {
    return "sysinfo";
  }

  @Override
  public String getTitle() {
    return "System Information";
  }

  @Override
  public String renderHtml() {
    Runtime runtime = Runtime.getRuntime();
    long maxMemoryMb = runtime.maxMemory() / (1024 * 1024);
    long totalMemoryMb = runtime.totalMemory() / (1024 * 1024);
    long freeMemoryMb = runtime.freeMemory() / (1024 * 1024);
    long usedMemoryMb = totalMemoryMb - freeMemoryMb;
    Duration uptime = Duration.ofMillis(ManagementFactory.getRuntimeMXBean().getUptime());

    return """
        <table style="width:100%%;border-collapse:collapse;background:#fff;border-radius:8px;\
        overflow:hidden;box-shadow:0 1px 3px rgba(0,0,0,0.08)">
          <thead>
            <tr>
              <th style="text-align:left;padding:0.75rem 1rem;font-size:0.75rem;font-weight:600;\
        text-transform:uppercase;letter-spacing:0.05em;color:#6c757d;background:#f8f9fa;\
        border-bottom:2px solid #dee2e6;width:200px">Property</th>
              <th style="text-align:left;padding:0.75rem 1rem;font-size:0.75rem;font-weight:600;\
        text-transform:uppercase;letter-spacing:0.05em;color:#6c757d;background:#f8f9fa;\
        border-bottom:2px solid #dee2e6">Value</th>
            </tr>
          </thead>
          <tbody>
            <tr><td style="padding:0.75rem 1rem;border-bottom:1px solid #eee;font-weight:500">\
        Operating System</td><td style="padding:0.75rem 1rem;border-bottom:1px solid #eee">%s %s (%s)\
        </td></tr>
            <tr><td style="padding:0.75rem 1rem;border-bottom:1px solid #eee;font-weight:500">\
        Java Version</td><td style="padding:0.75rem 1rem;border-bottom:1px solid #eee">%s (%s)\
        </td></tr>
            <tr><td style="padding:0.75rem 1rem;border-bottom:1px solid #eee;font-weight:500">\
        JVM</td><td style="padding:0.75rem 1rem;border-bottom:1px solid #eee">%s %s\
        </td></tr>
            <tr><td style="padding:0.75rem 1rem;border-bottom:1px solid #eee;font-weight:500">\
        Available Processors</td><td style="padding:0.75rem 1rem;border-bottom:1px solid #eee">\
        %d</td></tr>
            <tr><td style="padding:0.75rem 1rem;border-bottom:1px solid #eee;font-weight:500">\
        Heap Memory (Used / Allocated / Max)</td>\
        <td style="padding:0.75rem 1rem;border-bottom:1px solid #eee">\
        %d MB / %d MB / %d MB</td></tr>
            <tr><td style="padding:0.75rem 1rem;font-weight:500">Uptime</td>\
        <td style="padding:0.75rem 1rem">%s</td></tr>
          </tbody>
        </table>
        """
        .formatted(
            System.getProperty("os.name"),
            System.getProperty("os.version"),
            System.getProperty("os.arch"),
            System.getProperty("java.version"),
            System.getProperty("java.vendor"),
            System.getProperty("java.vm.name"),
            System.getProperty("java.vm.version"),
            runtime.availableProcessors(),
            usedMemoryMb,
            totalMemoryMb,
            maxMemoryMb,
            formatDuration(uptime));
  }

  private static String formatDuration(Duration duration) {
    long hours = duration.toHours();
    long minutes = duration.toMinutesPart();
    long seconds = duration.toSecondsPart();
    if (hours > 0) {
      return "%dh %dm %ds".formatted(hours, minutes, seconds);
    }
    if (minutes > 0) {
      return "%dm %ds".formatted(minutes, seconds);
    }
    return "%ds".formatted(seconds);
  }
}
