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

import org.springframework.beans.factory.annotation.Value;
import org.springframework.web.bind.annotation.ControllerAdvice;
import org.springframework.web.bind.annotation.ModelAttribute;

/** Exposes Plugwerk connection info to all Thymeleaf templates. */
@ControllerAdvice
public class GlobalModelAttributes {

  @Value("${plugwerk.server-url:http://localhost:8080}")
  private String serverUrl;

  @Value("${plugwerk.namespace:default}")
  private String namespace;

  @Value("${plugwerk.api-key:}")
  private String apiKey;

  @ModelAttribute("plugwerkServerUrl")
  public String serverUrl() {
    return serverUrl;
  }

  @ModelAttribute("plugwerkNamespace")
  public String namespace() {
    return namespace;
  }

  @ModelAttribute("plugwerkApiKey")
  public String apiKey() {
    return apiKey;
  }
}
