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
package io.plugwerk.server.controller

import io.plugwerk.api.AuthApi
import io.plugwerk.api.model.LoginRequest
import io.plugwerk.api.model.LoginResponse
import io.plugwerk.server.security.UserCredentialValidator
import io.plugwerk.server.service.JwtTokenService
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RestController

@RestController
@RequestMapping("/api")
class AuthController(
    private val credentialValidator: UserCredentialValidator,
    private val jwtTokenService: JwtTokenService,
) : AuthApi {

    override fun login(loginRequest: LoginRequest): ResponseEntity<LoginResponse> {
        if (!credentialValidator.validate(loginRequest.username, loginRequest.password)) {
            return ResponseEntity.status(401).build()
        }
        val token = jwtTokenService.generateToken(loginRequest.username)
        return ResponseEntity.ok(
            LoginResponse(
                accessToken = token,
                tokenType = "Bearer",
                expiresIn = jwtTokenService.tokenValiditySeconds(),
            ),
        )
    }
}
