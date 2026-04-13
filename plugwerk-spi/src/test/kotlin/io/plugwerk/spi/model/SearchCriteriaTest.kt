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
import org.junit.jupiter.api.Assertions.assertNotSame
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Test

class SearchCriteriaTest {

    @Test
    fun `builder creates criteria with all fields set`() {
        val criteria =
            SearchCriteria.Builder()
                .query("crm")
                .tag("salesforce")
                .compatibleWith("3.1.0")
                .build()

        assertEquals("crm", criteria.query)
        assertEquals("salesforce", criteria.tag)
        assertEquals("3.1.0", criteria.compatibleWith)
    }

    @Test
    fun `builder creates empty criteria when no fields set`() {
        val criteria = SearchCriteria.Builder().build()

        assertNull(criteria.query)
        assertNull(criteria.tag)
        assertNull(criteria.compatibleWith)
    }

    @Test
    fun `builder creates criteria with single field`() {
        val criteria = SearchCriteria.Builder().query("analytics").build()

        assertEquals("analytics", criteria.query)
        assertNull(criteria.tag)
        assertNull(criteria.compatibleWith)
    }

    @Test
    fun `builder produces independent instances`() {
        val builder = SearchCriteria.Builder().query("shared")
        val first = builder.build()
        val second = builder.tag("extra").build()

        assertNotSame(first, second)
        assertNull(first.tag)
        assertEquals("extra", second.tag)
    }
}
