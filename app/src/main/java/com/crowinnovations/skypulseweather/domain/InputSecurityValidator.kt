package com.crowinnovations.skypulseweather.domain

import java.net.URI
import java.util.Locale

/**
 * Centralized OWASP MASVS-aligned input sanitization, allowlist validation,
 * coordinate bounds verification, and SSRF-resistant HTTPS gateway validation.
 */
object InputSecurityValidator {

    const val MAX_SEARCH_QUERY_LENGTH = 80
    const val MAX_GATEWAY_URL_LENGTH = 256
    private const val MAX_LABEL_LENGTH = 120
    private const val MAX_ALERT_BODY_LENGTH = 2_400

    // Unicode control, zero-width, and bidirectional override characters (anti-UI-spoofing)
    private val UNSAFE_INVISIBLE_OR_CONTROL_REGEX =
        Regex("[\\p{Cntrl}\\u200B-\\u200F\\u202A-\\u202E\\u2060-\\u2069\\uFEFF]")

    // Allowlist for ZIP / postal / city / state search queries: Unicode letters, digits, spaces, and safe punctuation
    private val SAFE_LOCATION_QUERY_REGEX =
        Regex("^[\\p{L}\\p{N}\\s,.'\\-]{1,80}$")

    // Valid DNS label per RFC 1035 / RFC 1123
    private val DNS_LABEL_REGEX =
        Regex("^[a-z0-9]([a-z0-9\\-]{0,61}[a-z0-9])?$")

    // Raw IPv4 address check
    private val IPV4_LITERAL_REGEX =
        Regex("^\\d{1,3}(\\.\\d{1,3}){3}$")

    sealed class GatewayValidationResult {
        data class Valid(val normalizedUrl: String) : GatewayValidationResult()
        data class Invalid(val userMessage: String) : GatewayValidationResult()
    }

    /**
     * Strips control characters, bidi/zero-width spoofing characters, and collapses whitespace
     * while enforcing a maximum character length for live text-field input.
     */
    fun sanitizeSearchInput(raw: String): String {
        if (raw.isEmpty()) return ""
        return raw
            .replace(UNSAFE_INVISIBLE_OR_CONTROL_REGEX, "")
            .replace(Regex("\\s+"), " ")
            .take(MAX_SEARCH_QUERY_LENGTH)
    }

    /**
     * Validates a sanitized location search query against the strict postal/city allowlist.
     * Returns null if rejected (empty, too short, or contains injection/HTML/path metacharacters).
     */
    fun validateLocationSearchQuery(raw: String): String? {
        val cleaned = sanitizeSearchInput(raw).trim()
        if (cleaned.length < 2) return null
        if (cleaned.contains("..")) return null
        if (!SAFE_LOCATION_QUERY_REGEX.matches(cleaned)) return null
        return cleaned
    }

    /**
     * Sanitizes untrusted external API display strings (city names, alert titles/descriptions)
     * to prevent control/bidi UI spoofing and unbounded memory payloads.
     */
    fun sanitizeDisplayText(raw: String?, maxLength: Int = MAX_LABEL_LENGTH): String {
        if (raw.isNullOrBlank()) return ""
        return raw
            .replace(UNSAFE_INVISIBLE_OR_CONTROL_REGEX, " ")
            .replace(Regex("[ \\t\\x0B\\f\\r]+"), " ")
            .trim()
            .take(maxLength)
    }

    /**
     * Preserves paragraph newlines in NWS severe weather alert descriptions/instructions
     * while stripping control/bidi spoofing characters and capping payload length.
     */
    fun sanitizeMultilineAlertText(raw: String?): String {
        if (raw.isNullOrBlank()) return ""
        return raw
            .replace(Regex("[\\u0000-\\u0008\\u000B\\u000C\\u000E-\\u001F\\u007F\\u200B-\\u200F\\u202A-\\u202E\\u2060-\\u2069\\uFEFF]"), "")
            .trim()
            .take(MAX_ALERT_BODY_LENGTH)
    }

    /**
     * Sanitizes ISO 3166-1 alpha-2 country codes.
     */
    fun sanitizeCountryCode(raw: String?): String {
        val cleaned = raw?.trim()?.uppercase(Locale.US).orEmpty()
        return if (cleaned.length == 2 && cleaned.all { it in 'A'..'Z' }) cleaned else "US"
    }

    /**
     * Strictly verifies finite WGS-84 geographic coordinates.
     */
    fun areValidCoordinates(latitude: Double, longitude: Double): Boolean {
        return latitude.isFinite() &&
            longitude.isFinite() &&
            latitude in -90.0..90.0 &&
            longitude in -180.0..180.0
    }

    /**
     * Validates an optional HTTPS Gateway Override URL with strict SSRF & credential-leak protection:
     * - Empty string is valid (clears override -> uses default https://api.open-meteo.com/v1/forecast)
     * - Must be strict `https://` scheme, `<= 256` chars, no embedded userinfo (`user:pass@`), no `#fragment`
     * - Host must be a public FQDN (blocks `localhost`, `.local`, `.internal`, IPv6 literals, and private/loopback IPv4)
     */
    fun validateHttpsGatewayUrl(raw: String): GatewayValidationResult {
        val cleaned = raw.replace(UNSAFE_INVISIBLE_OR_CONTROL_REGEX, "").trim()
        if (cleaned.isEmpty()) {
            return GatewayValidationResult.Valid("")
        }
        if (cleaned.length > MAX_GATEWAY_URL_LENGTH) {
            return GatewayValidationResult.Invalid("Gateway URL must be $MAX_GATEWAY_URL_LENGTH characters or fewer.")
        }
        if (cleaned.any { it.isWhitespace() }) {
            return GatewayValidationResult.Invalid("Gateway URL must not contain spaces.")
        }

        val parsed = runCatching { URI(cleaned) }.getOrElse {
            return GatewayValidationResult.Invalid("Enter a valid HTTPS URL (e.g., https://gateway.example.com/v1/forecast).")
        }

        val scheme = parsed.scheme?.lowercase(Locale.US)
        if (scheme != "https") {
            return GatewayValidationResult.Invalid("Gateway URL must use HTTPS (https://).")
        }
        if (parsed.rawUserInfo != null || cleaned.substringAfter("https://").substringBefore('/').contains('@')) {
            return GatewayValidationResult.Invalid("Embedded credentials in URLs are not permitted.")
        }
        if (parsed.rawFragment != null) {
            return GatewayValidationResult.Invalid("URL fragments (#) are not permitted in gateway endpoints.")
        }

        val host = parsed.host?.lowercase(Locale.US)?.trimEnd('.')
            ?: return GatewayValidationResult.Invalid("Gateway URL must specify a valid public hostname.")

        if (!isSafePublicHost(host)) {
            return GatewayValidationResult.Invalid("Gateway host must be a public domain (local/private addresses blocked).")
        }

        val port = parsed.port
        if (port != -1 && port !in listOf(443, 8443)) {
            return GatewayValidationResult.Invalid("Only standard HTTPS ports (443 or 8443) are permitted.")
        }

        val rawPath = parsed.rawPath.orEmpty()
        if (rawPath.contains("..") || rawPath.contains('\\')) {
            return GatewayValidationResult.Invalid("Gateway path must not contain traversal sequences.")
        }

        val normalized = URI(
            "https",
            null,
            host,
            port,
            rawPath.ifEmpty { "/" },
            parsed.rawQuery,
            null
        ).toASCIIString()

        return GatewayValidationResult.Valid(normalized)
    }

    /**
     * Checks whether a host is a safe public DNS domain (blocking loopback, RFC1918, link-local,
     * cloud metadata IPs, IPv6 literals, and internal/mDNS suffixes).
     */
    fun isSafePublicHost(rawHost: String): Boolean {
        val host = rawHost.lowercase(Locale.US).trim().trimEnd('.')
        if (host.isEmpty() || host.length > 253) return false
        if (host.startsWith("[") || host.contains(":")) return false // Block raw IPv6 literals

        if (host == "localhost" ||
            host.endsWith(".localhost") ||
            host.endsWith(".local") ||
            host.endsWith(".internal") ||
            host.endsWith(".lan") ||
            host.endsWith(".home.arpa") ||
            host.endsWith(".onion")
        ) {
            return false
        }

        if (IPV4_LITERAL_REGEX.matches(host)) {
            // Reject all raw IPv4 literals (require TLS SNI + public FQDN certificate verification)
            return false
        }

        val labels = host.split('.')
        if (labels.size < 2) return false
        val tld = labels.last()
        if (tld.length < 2 || tld.all { it.isDigit() }) return false

        return labels.all { label -> DNS_LABEL_REGEX.matches(label) }
    }
}
