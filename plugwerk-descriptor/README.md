# plugwerk-descriptor

Parser and validator for plugin descriptors embedded in artifact JARs and ZIPs.

## Purpose

When a plugin artifact is uploaded, its metadata must be extracted and validated. This module handles:

1. **Parsing** `plugwerk.yml` (the Plugwerk-native descriptor format)
2. **Fallback parsing** of PF4J's `MANIFEST.MF` (`Plugin-Id`, `Plugin-Version`, ...) and `plugin.properties`
3. **Resolving** which descriptor source to use (YAML preferred, then manifest, then properties)
4. **Validating** all fields (SemVer format, ID format, length limits, HTML/script injection detection)

## Contents

| Class | Responsibility |
|-------|---------------|
| `PlugwerkDescriptor` | Immutable data class representing parsed plugin metadata |
| `PlugwerkDescriptorParser` | Parses `plugwerk.yml` from a JAR or raw `InputStream` |
| `Pf4jManifestParser` | Parses PF4J `MANIFEST.MF` attributes and `plugin.properties` |
| `DescriptorResolver` | Tries all parsers in priority order (YAML > manifest > properties) for JARs and ZIP bundles |
| `DescriptorValidator` | Validates field format, length, cardinality, and rejects dangerous content |
| `DescriptorExceptions` | `DescriptorParseException`, `DescriptorValidationException`, `DescriptorNotFoundException` |

## Descriptor Priority

When resolving from a JAR:

1. `plugwerk.yml` at the JAR root
2. `MANIFEST.MF` with `Plugin-Id` attribute
3. `plugin.properties` with `plugin.id` key

When resolving from a ZIP bundle, root-level JARs are checked before `lib/` JARs.

## Validation Rules

- Plugin ID: `[a-zA-Z0-9][a-zA-Z0-9._-]*`, max 128 characters, no path traversal
- Version: valid SemVer (via semver4j)
- Text fields (name, description, author, license): max 255 characters
- Lists (categories, tags, screenshots): max 20 entries, each max 64 characters
- Dependencies: valid IDs and SemVer version ranges
- HTML/script content: rejected (defense-in-depth XSS prevention)

## Compatibility

- **JVM target:** 11
- **Dependencies:** `plugwerk-spi`, Jackson (YAML + Kotlin module), SLF4J
