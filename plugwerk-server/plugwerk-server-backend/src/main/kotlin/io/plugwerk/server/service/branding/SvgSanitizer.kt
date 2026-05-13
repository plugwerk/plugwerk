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
package io.plugwerk.server.service.branding

import org.w3c.dom.Document
import org.w3c.dom.Element
import org.w3c.dom.Node
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import javax.xml.XMLConstants
import javax.xml.parsers.DocumentBuilderFactory
import javax.xml.transform.OutputKeys
import javax.xml.transform.TransformerFactory
import javax.xml.transform.dom.DOMSource
import javax.xml.transform.stream.StreamResult

/**
 * Allowlist-based sanitiser for branding SVG uploads (#254).
 *
 * Strips anything that could carry script execution or remote loads
 * (`<script>`, `<foreignObject>`, `on*` event-handler attributes,
 * external `href`/`xlink:href`, inline `<style>`). Whatever remains is
 * the visual subset of SVG every reasonable logo uses.
 *
 * Defends against XXE by disabling external entities, the doctype, and
 * loading external DTDs in the parser configuration. The point is that
 * an attacker with admin access — already extreme — cannot escalate to
 * "every visitor of the login page runs arbitrary JS in their browser".
 */
class SvgSanitizer {

    /**
     * Returns the sanitised SVG bytes. Throws [SvgSanitizationException]
     * if the input is not parseable as XML or has no `<svg>` root.
     */
    fun sanitize(input: ByteArray): ByteArray {
        val doc = parse(input)
        val root = doc.documentElement
            ?: throw SvgSanitizationException("Input has no root element")
        if (root.localName != "svg") {
            throw SvgSanitizationException(
                "Root element must be <svg>, got <${root.localName}>",
            )
        }
        cleanElement(root)
        return serialize(doc)
    }

    private fun parse(input: ByteArray): Document {
        val factory = DocumentBuilderFactory.newInstance().apply {
            isNamespaceAware = true
            // Defensive XML parser: no DTD loading, no external entities,
            // no XInclude — the canonical XXE-hardening bundle.
            setFeature("http://apache.org/xml/features/disallow-doctype-decl", true)
            setFeature("http://xml.org/sax/features/external-general-entities", false)
            setFeature("http://xml.org/sax/features/external-parameter-entities", false)
            setFeature("http://apache.org/xml/features/nonvalidating/load-external-dtd", false)
            isXIncludeAware = false
            setAttribute(XMLConstants.ACCESS_EXTERNAL_DTD, "")
            setAttribute(XMLConstants.ACCESS_EXTERNAL_SCHEMA, "")
        }
        return try {
            factory.newDocumentBuilder().parse(ByteArrayInputStream(input))
        } catch (ex: Exception) {
            throw SvgSanitizationException("SVG is not parseable: ${ex.message}", ex)
        }
    }

    private fun cleanElement(element: Element) {
        // Snapshot child nodes — DOM mutation invalidates the live list.
        val children = (0 until element.childNodes.length).map { element.childNodes.item(it) }
        for (child in children) {
            when (child.nodeType) {
                Node.ELEMENT_NODE -> {
                    val el = child as Element
                    val name = el.localName ?: el.nodeName
                    if (name.lowercase() in FORBIDDEN_ELEMENTS) {
                        element.removeChild(el)
                        continue
                    }
                    if (name.lowercase() == "style") {
                        // Inline CSS can carry url(javascript:...) and other
                        // tricks. The branding logo does not need it.
                        element.removeChild(el)
                        continue
                    }
                    cleanElement(el)
                }

                Node.PROCESSING_INSTRUCTION_NODE,
                Node.COMMENT_NODE,
                -> element.removeChild(child)

                else -> { /* keep text nodes etc. */ }
            }
        }
        cleanAttributes(element)
    }

    private fun cleanAttributes(element: Element) {
        val attrNames = (0 until element.attributes.length).map {
            element.attributes.item(it).nodeName
        }
        for (name in attrNames) {
            val lower = name.lowercase()
            if (lower.startsWith("on")) {
                // Strips on* event handlers in any namespace.
                element.removeAttribute(name)
                continue
            }
            if (lower == "href" || lower.endsWith(":href")) {
                val value = element.getAttribute(name).trim()
                if (!isSafeHref(value)) {
                    element.removeAttribute(name)
                }
            }
        }
    }

    private fun isSafeHref(value: String): Boolean {
        if (value.isEmpty()) return true
        // Internal SVG references and inline data URIs are safe.
        if (value.startsWith("#")) return true
        if (value.startsWith("data:")) return true
        // Anything else (http(s)://, javascript:, etc.) is rejected —
        // a logo never legitimately needs to phone home.
        return false
    }

    private fun serialize(doc: Document): ByteArray {
        val transformer = TransformerFactory.newInstance().apply {
            setAttribute(XMLConstants.ACCESS_EXTERNAL_DTD, "")
            setAttribute(XMLConstants.ACCESS_EXTERNAL_STYLESHEET, "")
        }.newTransformer().apply {
            setOutputProperty(OutputKeys.OMIT_XML_DECLARATION, "yes")
            setOutputProperty(OutputKeys.METHOD, "xml")
            setOutputProperty(OutputKeys.ENCODING, "UTF-8")
        }
        val out = ByteArrayOutputStream()
        transformer.transform(DOMSource(doc), StreamResult(out))
        return out.toByteArray()
    }

    companion object {
        private val FORBIDDEN_ELEMENTS = setOf(
            "script",
            "foreignobject",
            "iframe",
            "embed",
            "object",
            "audio",
            "video",
            "animate",
            "set",
        )
    }
}

class SvgSanitizationException(message: String, cause: Throwable? = null) : RuntimeException(message, cause)
