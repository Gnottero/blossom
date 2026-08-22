package blossom.language;

import java.net.URI;
import java.net.URISyntaxException;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;

import org.jspecify.annotations.Nullable;

import blossom.exception.BlossomRuntimeError;
import blossom.exception.SourcePosition;
import blossom.exception.Suggestions;

/**
 * The hosts {@code core.http} is allowed to talk to, and under what limits.
 *
 * <p>Same shape as the boundaries in {@link IoRoots}: nothing is reachable until an administrator
 * declares it in {@code config/blossomscript.json}. A network permission granted by default would
 * turn every installed app into an exfiltration channel for the server's data.
 *
 * <p>A host is declared exactly ({@code api.example.com}) or with a single leading wildcard
 * ({@code *.example.com}, which covers subdomains but not {@code example.com} itself). No looser
 * patterns exist: {@code *} alone is the only way to open everything up, and whoever writes it is
 * doing so on purpose.
 *
 * @param hosts the allowed patterns, empty = no network access
 * @param schemes the allowed schemes, lowercase (usually just {@code https})
 * @param timeoutMillis how long to wait for a response before declaring the request failed
 * @param maxResponseBytes beyond this size the body is rejected instead of being read
 * @param followRedirects whether to follow 3xx responses, re-checking the destination host
 */
public record HttpAccess(List<String> hosts, Set<String> schemes, int timeoutMillis,
                         long maxResponseBytes, boolean followRedirects)
{
    /** No hosts: {@code core.http} exists but cannot reach anything. */
    public static final HttpAccess NONE = new HttpAccess(List.of(), Set.of("https"), 5_000, 1L << 20, false);

    /** The methods a request can use: those with a defined HTTP semantics and a known body. */
    public static final Set<String> METHODS = Set.of("GET", "POST", "PUT", "PATCH", "DELETE", "HEAD");

    public HttpAccess
    {
        hosts = List.copyOf(hosts);
        schemes = Set.copyOf(schemes.stream().map(scheme -> scheme.toLowerCase(Locale.ROOT))
                .collect(java.util.stream.Collectors.toCollection(LinkedHashSet::new)));
        timeoutMillis = Math.max(1, timeoutMillis);
        maxResponseBytes = Math.max(1, maxResponseBytes);
    }

    /**
     * Checks a URL before it is opened.
     *
     * @param method the HTTP method, already uppercase
     * @return the parsed URL, ready for the request
     * @throws BlossomRuntimeError if the URL is malformed, or if scheme/host/method is not allowed
     */
    public URI check(String url, String method)
    {
        if (!METHODS.contains(method))
        {
            throw refuse("Unsupported HTTP method '" + method + "'. Supported: "
                    + METHODS.stream().sorted().toList() + ".", Suggestions.closestTo(method, METHODS));
        }
        URI uri;
        try
        {
            uri = new URI(url);
        }
        catch (URISyntaxException e)
        {
            throw refuse("Malformed url '" + url + "': " + e.getReason() + ".", null);
        }
        if (uri.getScheme() == null || uri.getHost() == null)
        {
            throw refuse("Url '" + url + "' is not absolute: it needs a scheme and a host,"
                    + " e.g. https://api.example.com/path.", null);
        }
        if (!schemes.contains(uri.getScheme().toLowerCase(Locale.ROOT)))
        {
            throw refuse("Scheme '" + uri.getScheme() + "' is not allowed: the allowed ones are "
                    + schemes.stream().sorted().toList() + ".", null);
        }
        if (!allows(uri.getHost()))
        {
            throw refuse(hosts.isEmpty()
                    ? "No http host is configured on this server: core.http cannot reach anything."
                            + " Hosts are declared in the mod configuration."
                    : "Host '" + uri.getHost() + "' is not allowed: the allowed ones are " + hosts + ".",
                    Suggestions.closestTo(uri.getHost(), hosts));
        }
        return uri;
    }

    /** @return whether {@code host} matches one of the declared patterns */
    public boolean allows(String host)
    {
        String candidate = host.toLowerCase(Locale.ROOT);
        return hosts.stream().anyMatch(pattern -> matches(pattern.toLowerCase(Locale.ROOT), candidate));
    }

    private static boolean matches(String pattern, String host)
    {
        if (pattern.equals("*"))
        {
            return true;
        }
        if (pattern.startsWith("*."))
        {
            return host.endsWith(pattern.substring(1));
        }
        return pattern.equals(host);
    }

    /**
     * A violated boundary is a programming error, not a network condition: it is thrown as an
     * error rather than as {@code http_exception}, so a {@code try} placed there to handle a slow
     * server doesn't end up hiding a missing configuration.
     */
    private static BlossomRuntimeError refuse(String message, @Nullable String suggestion)
    {
        return new BlossomRuntimeError(SourcePosition.UNKNOWN, "http", message, suggestion);
    }
}
