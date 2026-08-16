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
 * Gli host a cui {@code core.http} può parlare, e con che limiti.
 *
 * <p>Stessa forma dei confini di {@link IoRoots}: niente è raggiungibile finché un amministratore non
 * lo dichiara in {@code config/blossomscript.json}. Un permesso di rete concesso per default
 * trasformerebbe ogni app installata in un canale di uscita dei dati del server.
 *
 * <p>Un host si dichiara esatto ({@code api.example.com}) o con un solo carattere jolly iniziale
 * ({@code *.example.com}, che vale per i sotto-domini e non per {@code example.com} stesso). Non
 * esistono pattern più liberi: {@code *} da solo è l'unico modo di aprire tutto, e chi lo scrive lo
 * sta facendo apposta.
 *
 * @param hosts i pattern consentiti, vuoto = niente rete
 * @param schemes gli schemi ammessi, in minuscolo (di norma solo {@code https})
 * @param timeoutMillis quanto si aspetta una risposta prima di dichiarare fallita la richiesta
 * @param maxResponseBytes oltre questa dimensione il corpo viene rifiutato invece di essere letto
 * @param followRedirects se seguire i 3xx, ricontrollando l'host di destinazione
 */
public record HttpAccess(List<String> hosts, Set<String> schemes, int timeoutMillis,
                         long maxResponseBytes, boolean followRedirects)
{
    /** Nessun host: {@code core.http} c'è ma non raggiunge niente. */
    public static final HttpAccess NONE = new HttpAccess(List.of(), Set.of("https"), 5_000, 1L << 20, false);

    /** i metodi che una richiesta può usare: quelli con una semantica HTTP definita e un corpo noto */
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
     * Controlla un URL prima che venga aperto.
     *
     * @param method il metodo HTTP, già in maiuscolo
     * @return l'URL analizzato, pronto per la richiesta
     * @throws BlossomRuntimeError se l'URL è malformato, o se schema/host/metodo non sono consentiti
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

    /** @return se {@code host} corrisponde a uno dei pattern dichiarati */
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
     * Un confine violato è un errore del programma, non una condizione di rete: viene lanciato come
     * errore e non come {@code http_exception}, così un {@code try} messo lì per gestire un server
     * lento non finisce per nascondere una configurazione mancante.
     */
    private static BlossomRuntimeError refuse(String message, @Nullable String suggestion)
    {
        return new BlossomRuntimeError(SourcePosition.UNKNOWN, "http", message, suggestion);
    }
}
