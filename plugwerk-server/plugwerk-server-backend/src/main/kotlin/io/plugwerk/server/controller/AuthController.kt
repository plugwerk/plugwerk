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
import io.plugwerk.api.model.ChangePasswordRequest
import io.plugwerk.api.model.LoginRequest
import io.plugwerk.api.model.LoginResponse
import io.plugwerk.server.repository.UserRepository
import io.plugwerk.server.security.UserCredentialValidator
import io.plugwerk.server.service.JwtTokenService
import io.plugwerk.server.service.UnauthorizedException
import io.plugwerk.server.service.UserService
import org.springframework.http.ResponseEntity
import org.springframework.security.core.context.SecurityContextHolder
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RestController

@RestController
@RequestMapping("/api/v1")
class AuthController(
    private val credentialValidator: UserCredentialValidator,
    private val jwtTokenService: JwtTokenService,
    private val userRepository: UserRepository,
    private val userService: UserService,
) : AuthApi {

    override fun login(loginRequest: LoginRequest): ResponseEntity<LoginResponse> {
        if (!credentialValidator.validate(loginRequest.username, loginRequest.password)) {
            return ResponseEntity.status(401).build()
        }
        val user = userRepository.findByUsername(loginRequest.username).orElse(null)
        val passwordChangeRequired = user?.passwordChangeRequired ?: false
        val token = jwtTokenService.generateToken(loginRequest.username)
        return ResponseEntity.ok(
            LoginResponse(
                accessToken = token,
                tokenType = "Bearer",
                expiresIn = jwtTokenService.tokenValiditySeconds(),
                passwordChangeRequired = passwordChangeRequired,
            ),
        )
    }

    override fun changePassword(changePasswordRequest: ChangePasswordRequest): ResponseEntity<Unit> {
        val username = SecurityContextHolder.getContext().authentication?.name
            ?: throw UnauthorizedException("Authentication required to change password")
        userService.changePassword(username, changePasswordRequest.currentPassword, changePasswordRequest.newPassword)
        return ResponseEntity.noContent().build()
    }
}
