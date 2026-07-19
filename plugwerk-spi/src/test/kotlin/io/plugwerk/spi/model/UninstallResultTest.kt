/*
 * Copyright (c) 2025-present devtank42 GmbH
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package io.plugwerk.spi.model

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertSame
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class UninstallResultTest {

    private val success = UninstallResult.Success("com.acme.plugin")
    private val failure = UninstallResult.Failure("com.acme.plugin", "Plugin not installed")

    @Test
    fun `pluginId accessible on base type without cast`() {
        val result: UninstallResult = success
        assertEquals("com.acme.plugin", result.pluginId)
    }

    @Test
    fun `pluginId accessible on Failure without cast`() {
        val result: UninstallResult = failure
        assertEquals("com.acme.plugin", result.pluginId)
    }

    @Test
    fun `isSuccess returns true for Success`() {
        assertTrue(success.isSuccess())
        assertFalse(success.isFailure())
    }

    @Test
    fun `isFailure returns true for Failure`() {
        assertTrue(failure.isFailure())
        assertFalse(failure.isSuccess())
    }

    @Test
    fun `onSuccess executes action for Success`() {
        var captured: String? = null
        success.onSuccess { captured = it.pluginId }
        assertEquals("com.acme.plugin", captured)
    }

    @Test
    fun `onSuccess skips action for Failure`() {
        var called = false
        failure.onSuccess { called = true }
        assertFalse(called)
    }

    @Test
    fun `onFailure executes action for Failure`() {
        var captured: String? = null
        failure.onFailure { captured = it.reason }
        assertEquals("Plugin not installed", captured)
    }

    @Test
    fun `onFailure skips action for Success`() {
        var called = false
        success.onFailure { called = true }
        assertFalse(called)
    }

    @Test
    fun `onSuccess returns same instance for chaining`() {
        assertSame(success, success.onSuccess { })
        assertSame(failure, failure.onSuccess { })
    }

    @Test
    fun `onFailure returns same instance for chaining`() {
        assertSame(success, success.onFailure { })
        assertSame(failure, failure.onFailure { })
    }

    @Test
    fun `onSuccess and onFailure are chainable`() {
        var successCalled = false
        var failureCalled = false

        success
            .onSuccess { successCalled = true }
            .onFailure { failureCalled = true }

        assertTrue(successCalled)
        assertFalse(failureCalled)
    }

    @Test
    fun `fold maps Success to value`() {
        val message = success.fold(
            onSuccess = { "Removed ${it.pluginId}" },
            onFailure = { "Failed: ${it.reason}" },
        )
        assertEquals("Removed com.acme.plugin", message)
    }

    @Test
    fun `fold maps Failure to value`() {
        val message = failure.fold(
            onSuccess = { "Removed ${it.pluginId}" },
            onFailure = { "Failed: ${it.reason}" },
        )
        assertEquals("Failed: Plugin not installed", message)
    }

    @Test
    fun `reasonOrNull returns reason for Failure`() {
        assertEquals("Plugin not installed", failure.reasonOrNull())
    }

    @Test
    fun `reasonOrNull returns null for Success`() {
        assertNull(success.reasonOrNull())
    }
}
