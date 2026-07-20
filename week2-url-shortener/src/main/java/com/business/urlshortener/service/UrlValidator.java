// TASK-03 — URL validation & SSRF protection
// Implements: REQ-SHORT-008 (reject invalid / non-http(s) URLs),
//             REQ-SHORT-010 (block localhost / private / loopback / link-local / internal hosts — SSRF),
//             REQ-SHORT-012 (only validated URLs may become redirect targets — no open redirect).
package com.business.urlshortener.service;

import com.business.urlshortener.exception.ValidationException;
import java.net.InetAddress;
import java.net.URI;
import java.net.UnknownHostException;
import java.util.Set;
import java.util.regex.Pattern;
import org.springframework.stereotype.Component;

@Component
public class UrlValidator {

    private static final Set<String> ALLOWED_SCHEMES = Set.of("http", "https");

    // Upper bound on stored URLs (matches the original_url column length) so an
    // over-long URL is rejected as 400 rather than surfacing as a DB error / 500.
    private static final int MAX_URL_LENGTH = 2048;

    // Matches an IPv4 dotted-quad literal.
    private static final Pattern IPV4_LITERAL =
            Pattern.compile("^\\d{1,3}\\.\\d{1,3}\\.\\d{1,3}\\.\\d{1,3}$");

    // Obfuscated numeric host forms that can decode to an internal address, e.g.
    // decimal (2130706433), hex (0x7f000001), or octal (0177.0.0.1) encodings of 127.0.0.1.
    private static final Pattern DECIMAL_HOST = Pattern.compile("^\\d+$");
    private static final Pattern HEX_HOST = Pattern.compile("^0x[0-9a-f]+$");
    private static final Pattern NUMERIC_DOTTED = Pattern.compile("^[0-9.]+$");
    // A component with a leading zero (and more digits) is an octal-encoded octet.
    private static final Pattern LEADING_ZERO_OCTET = Pattern.compile("(^|\\.)0\\d+");

    /**
     * Validates a submitted URL. Throws {@link ValidationException} when the URL is
     * syntactically invalid, over-long, carries embedded credentials, uses a non-http(s)
     * scheme, or targets a blocked/internal host. REQ-SHORT-008, REQ-SHORT-010, REQ-SHORT-012.
     */
    public void validate(String rawUrl) {
        if (rawUrl == null || rawUrl.isBlank()) {
            throw new ValidationException("URL must not be blank");
        }
        if (rawUrl.length() > MAX_URL_LENGTH) {
            throw new ValidationException("URL exceeds the maximum length of " + MAX_URL_LENGTH);
        }

        final URI uri;
        try {
            uri = new URI(rawUrl.trim());
        } catch (Exception e) {
            throw new ValidationException("Malformed URL");
        }

        if (!uri.isAbsolute()) {
            throw new ValidationException("URL must be absolute");
        }

        String scheme = uri.getScheme();
        if (scheme == null || !ALLOWED_SCHEMES.contains(scheme.toLowerCase())) {
            throw new ValidationException("Only http and https URLs are allowed");
        }

        // Reject embedded credentials (http://user:pass@host) — a common tactic to
        // obscure the real host and smuggle internal targets past naive filters.
        if (uri.getUserInfo() != null) {
            throw new ValidationException("URLs must not contain embedded credentials");
        }

        String host = uri.getHost();
        if (host == null || host.isBlank()) {
            throw new ValidationException("URL must contain a valid host");
        }

        if (isBlockedHost(host)) {
            throw new ValidationException("URL targets a blocked or internal host");
        }
    }

    /**
     * Returns true when the host resolves to (or is a literal for) an internal,
     * loopback, link-local, private, or otherwise non-public address (REQ-SHORT-010).
     *
     * <p>Literal IPs are classified directly. Obfuscated numeric encodings are rejected
     * outright. Public hostnames are resolved on a best-effort basis and every resolved
     * address is checked — this catches hostnames that map to private space. A hostname
     * that cannot be resolved is allowed (it cannot be a known-internal target), so the
     * check stays usable offline.
     */
    boolean isBlockedHost(String host) {
        String h = host.toLowerCase();

        // Strip IPv6 brackets, e.g. [::1] -> ::1
        if (h.startsWith("[") && h.endsWith("]")) {
            h = h.substring(1, h.length() - 1);
        }

        if (h.equals("localhost") || h.endsWith(".localhost")) {
            return true;
        }

        boolean numericDotted = NUMERIC_DOTTED.matcher(h).matches();

        // Reject obfuscated numeric host encodings outright — they can decode to an
        // internal address but would otherwise slip past canonical range checks:
        //   pure decimal (2130706433), 0x hex (0x7f000001), or any octal octet (0177.0.0.1).
        if (DECIMAL_HOST.matcher(h).matches()
                || HEX_HOST.matcher(h).matches()
                || (numericDotted && LEADING_ZERO_OCTET.matcher(h).find())) {
            return true;
        }

        boolean ipLiteral = IPV4_LITERAL.matcher(h).matches() || numericDotted || h.contains(":");

        try {
            if (ipLiteral) {
                // Parsing a literal does not perform a DNS lookup.
                return isInternalAddress(InetAddress.getByName(h));
            }
            // Public hostname: resolve best-effort and reject if ANY address is internal
            // (defends against a benign-looking name pointing into private space).
            for (InetAddress addr : InetAddress.getAllByName(h)) {
                if (isInternalAddress(addr)) {
                    return true;
                }
            }
            return false;
        } catch (UnknownHostException e) {
            // Literal IPs that fail to parse are unsafe; unresolvable hostnames are not a
            // known-internal target, so only block the literal case.
            return ipLiteral;
        }
    }

    /** Classifies an address as internal/non-public across IPv4 and IPv6. */
    private boolean isInternalAddress(InetAddress addr) {
        if (addr.isLoopbackAddress()      // 127.0.0.0/8, ::1
                || addr.isAnyLocalAddress()   // 0.0.0.0, ::
                || addr.isLinkLocalAddress()  // 169.254.0.0/16, fe80::/10
                || addr.isSiteLocalAddress()  // 10/8, 172.16/12, 192.168/16
                || addr.isMulticastAddress()) {
            return true;
        }

        byte[] b = addr.getAddress();
        if (b.length == 4) {
            int first = b[0] & 0xFF;
            int second = b[1] & 0xFF;
            // 0.0.0.0/8 (also "this network") and 100.64.0.0/10 (carrier-grade NAT).
            if (first == 0) {
                return true;
            }
            return first == 100 && second >= 64 && second <= 127;
        }
        if (b.length == 16) {
            // IPv6 unique local addresses fc00::/7 (not covered by isSiteLocalAddress).
            return (b[0] & 0xFE) == 0xFC;
        }
        return false;
    }
}