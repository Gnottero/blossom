package blossom.language;

import java.io.IOException;
import java.io.InputStream;
import java.net.URI;
import java.net.URLDecoder;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

import blossom.exception.BlossomRuntimeError;
import blossom.exception.SourcePosition;
import blossom.exception.ThrowStatement;
import blossom.exception.Throwables;
import blossom.interpreter.Context;
import blossom.interpreter.Expression;
import blossom.interpreter.ScriptHost;
import blossom.value.ListValue;
import blossom.value.MapValue;
import blossom.value.NumericValue;
import blossom.value.StringValue;
import blossom.value.Value;

/**
 * {@code core.http}: richieste HTTP verso host dichiarati.
 *
 * <p>Ogni chiamata restituisce una mappa {@code {'status', 'body', 'headers', 'url'}}. Uno stato
 * fuori dai 2xx non è un errore: è una risposta, e sta a chi scrive decidere cosa farne. Sono errori
 * le cose che una risposta non la producono - host irraggiungibile, timeout, corpo troppo grande -
 * e arrivano come {@code http_exception}, quindi si catturano con
 * {@code try(..., 'http_exception', ...)}.
 *
 * <p>Due confini, entrambi voluti e non aggirabili da uno script:
 *
 * <ul>
 *   <li>gli host raggiungibili li dichiara un amministratore (vedi {@link HttpAccess}); senza
 *       dichiarazioni queste funzioni esistono ma non aprono niente;</li>
 *   <li>una richiesta non parte dal thread del server. Una risposta lenta lì bloccherebbe il tick,
 *       e il watchdog non può interrompere una lettura di socket: il codice va dentro un
 *       {@code task(...)}, dove il tempo che passa non lo paga nessun giocatore.</li>
 * </ul>
 */
public final class Http
{
    /** i simboli esportati da {@code core.http}: nome pubblico → nome dell'implementazione */
    public static final Map<String, String> MODULE = Map.of(
            "get", "http_get",
            "post", "http_post",
            "request", "http_request",
            "get_json", "http_get_json",
            "post_json", "http_post_json",
            "hosts", "http_hosts",
            "encode_url", "url_encode",
            "decode_url", "url_decode",
            "encode_query", "url_encode_query");

    /** oltre questi salti una catena di redirect è un anello, non un percorso */
    private static final int MAX_REDIRECTS = 5;

    /**
     * Un client per configurazione: la creazione apre un pool di connessioni e un selettore, e farlo
     * ad ogni richiesta costerebbe più della richiesta stessa.
     */
    private static final Map<Integer, HttpClient> CLIENTS = new ConcurrentHashMap<>();

    private Http()
    {
    }

    public static void apply(Expression expression)
    {
        // quali host sono raggiungibili: uno script che vuole essere gentile puo' dirlo prima di
        // fallire, come fa io.roots()
        expression.addContextFunction("http_hosts", 0, (c, t, lv) ->
                ListValue.wrap(access(c).hosts().stream().map(StringValue::of)));

        expression.addContextFunction("http_get", -1, (c, t, lv) ->
        {
            arity("get", lv, 1, 2);
            return send(c, "GET", lv.get(0).getString(), null, headers(lv, 1));
        });

        expression.addContextFunction("http_post", -1, (c, t, lv) ->
        {
            arity("post", lv, 2, 3);
            return send(c, "POST", lv.get(0).getString(), lv.get(1).getString(), headers(lv, 2));
        });

        expression.addContextFunction("http_request", -1, (c, t, lv) ->
        {
            arity("request", lv, 2, 4);
            String method = lv.get(0).getString().toUpperCase(Locale.ROOT);
            String body = lv.size() > 2 && !lv.get(2).isNull() ? lv.get(2).getString() : null;
            return send(c, method, lv.get(1).getString(), body, headers(lv, 3));
        });

        expression.addContextFunction("http_get_json", -1, (c, t, lv) ->
        {
            arity("get_json", lv, 1, 2);
            return json(send(c, "GET", lv.get(0).getString(), null,
                    with(headers(lv, 1), "accept", "application/json")));
        });

        expression.addContextFunction("http_post_json", -1, (c, t, lv) ->
        {
            arity("post_json", lv, 2, 3);
            Map<String, String> headers = with(headers(lv, 2), "content-type", "application/json");
            return json(send(c, "POST", lv.get(0).getString(), lv.get(1).toJson().toString(),
                    with(headers, "accept", "application/json")));
        });

        expression.addUnaryFunction("url_encode", v ->
                StringValue.of(URLEncoder.encode(v.getString(), StandardCharsets.UTF_8)));

        expression.addUnaryFunction("url_decode", v ->
        {
            try
            {
                return StringValue.of(URLDecoder.decode(v.getString(), StandardCharsets.UTF_8));
            }
            catch (IllegalArgumentException e)
            {
                throw new ThrowStatement(StringValue.of("cannot decode '" + v.getString() + "': "
                        + e.getMessage()), Throwables.VALUE_EXCEPTION);
            }
        });

        // una query string scritta a mano e' il modo piu' facile di spedire un valore non
        // codificato: qui le chiavi e i valori passano tutti per lo stesso encoder
        expression.addUnaryFunction("url_encode_query", v ->
        {
            if (!(v instanceof final MapValue parameters))
            {
                throw new BlossomRuntimeError(SourcePosition.UNKNOWN, "http",
                        "'encode_query' wants a map of parameters, e.g. {'q' -> 'blossom'}.");
            }
            List<String> pairs = new ArrayList<>();
            parameters.unpack().forEach(pair ->
            {
                List<Value> entry = ((ListValue) pair).getItems();
                pairs.add(URLEncoder.encode(entry.get(0).getString(), StandardCharsets.UTF_8) + "="
                        + URLEncoder.encode(entry.get(1).getString(), StandardCharsets.UTF_8));
            });
            java.util.Collections.sort(pairs);
            return StringValue.of(String.join("&", pairs));
        });
    }

    /**
     * Esegue la richiesta, seguendo i redirect a mano se la configurazione lo consente.
     *
     * <p>I redirect non li segue {@link HttpClient} perché la sua politica non ricontrolla l'host di
     * arrivo: un {@code 302} verso un dominio non dichiarato aggirerebbe l'intera allowlist.
     */
    private static Value send(Context c, String method, String url, String body, Map<String, String> headers)
    {
        HttpAccess access = access(c);
        requireOffMainThread(method, url);
        String target = url;
        for (int hop = 0; ; hop++)
        {
            URI uri = access.check(target, method);
            HttpResponse<InputStream> response = exchange(access, uri, method, body, headers);
            String location = response.headers().firstValue("location").orElse(null);
            boolean redirect = access.followRedirects() && isRedirect(response.statusCode()) && location != null;
            if (!redirect)
            {
                return toValue(access, uri, response);
            }
            if (hop >= MAX_REDIRECTS)
            {
                throw failure(target, "too many redirects (" + MAX_REDIRECTS + ")");
            }
            drain(response);
            target = uri.resolve(location).toString();
        }
    }

    private static HttpResponse<InputStream> exchange(HttpAccess access, URI uri, String method,
                                                      String body, Map<String, String> headers)
    {
        HttpRequest.Builder request = HttpRequest.newBuilder(uri)
                .timeout(Duration.ofMillis(access.timeoutMillis()))
                .method(method, body == null
                        ? HttpRequest.BodyPublishers.noBody()
                        : HttpRequest.BodyPublishers.ofString(body, StandardCharsets.UTF_8));
        headers.forEach((name, value) ->
        {
            try
            {
                request.header(name, value);
            }
            catch (IllegalArgumentException e)
            {
                // i nomi che la JVM si riserva (host, connection, ...) non si possono impostare:
                // dirlo e' meglio che spedire una richiesta diversa da quella scritta
                throw new BlossomRuntimeError(SourcePosition.UNKNOWN, "http",
                        "Header '" + name + "' cannot be set: " + e.getMessage());
            }
        });
        try
        {
            return client(access).send(request.build(), HttpResponse.BodyHandlers.ofInputStream());
        }
        catch (IOException e)
        {
            throw failure(uri.toString(), e.getMessage() == null ? e.toString() : e.getMessage());
        }
        catch (InterruptedException e)
        {
            Thread.currentThread().interrupt();
            throw failure(uri.toString(), "interrupted");
        }
    }

    /** La risposta come mappa, con il corpo letto fino al limite dichiarato e non oltre. */
    private static Value toValue(HttpAccess access, URI uri, HttpResponse<InputStream> response)
    {
        String body = read(access, uri, response);
        List<Value> headers = new ArrayList<>();
        response.headers().map().forEach((name, values) -> headers.add(
                ListValue.of(StringValue.of(name.toLowerCase(Locale.ROOT)), StringValue.of(String.join(", ", values)))));
        return new MapValue(List.of(
                ListValue.of(StringValue.of("status"), NumericValue.of(response.statusCode())),
                ListValue.of(StringValue.of("body"), StringValue.of(body)),
                ListValue.of(StringValue.of("headers"), new MapValue(headers)),
                ListValue.of(StringValue.of("url"), StringValue.of(uri.toString()))));
    }

    /**
     * Legge il corpo fermandosi un byte oltre il limite.
     *
     * <p>Il limite si controlla leggendo e non sul {@code content-length}: quell'intestazione la
     * scrive il server dall'altra parte, e un server che mente riempirebbe la memoria del nostro.
     */
    private static String read(HttpAccess access, URI uri, HttpResponse<InputStream> response)
    {
        long limit = access.maxResponseBytes();
        try (InputStream stream = response.body())
        {
            byte[] content = stream.readNBytes((int) Math.min(limit + 1, Integer.MAX_VALUE));
            if (content.length > limit)
            {
                throw failure(uri.toString(), "response is larger than the configured limit of " + limit + " bytes");
            }
            return new String(content, StandardCharsets.UTF_8);
        }
        catch (IOException e)
        {
            throw failure(uri.toString(), e.getMessage() == null ? e.toString() : e.getMessage());
        }
    }

    /** Il corpo di una risposta di redirect non serve a nessuno, ma va chiuso o la connessione resta appesa. */
    private static void drain(HttpResponse<InputStream> response)
    {
        try (InputStream stream = response.body())
        {
            stream.readAllBytes();
        }
        catch (IOException e)
        {
            // la richiesta successiva aprira' una connessione nuova: non c'e' niente da riportare
        }
    }

    /** Il corpo della risposta interpretato come json, con lo stato che resta accessibile. */
    private static Value json(Value response)
    {
        Value body = ((MapValue) response).get(StringValue.of("body"));
        try
        {
            return blossom.utils.JsonSupport.GSON.fromJson(body.getString(), Value.class);
        }
        catch (com.google.gson.JsonParseException e)
        {
            throw new ThrowStatement(StringValue.of(e.getMessage()), Throwables.JSON_ERROR);
        }
    }

    private static boolean isRedirect(int status)
    {
        return status == 301 || status == 302 || status == 303 || status == 307 || status == 308;
    }

    private static HttpClient client(HttpAccess access)
    {
        return CLIENTS.computeIfAbsent(access.timeoutMillis(), timeout -> HttpClient.newBuilder()
                .connectTimeout(Duration.ofMillis(timeout))
                .followRedirects(HttpClient.Redirect.NEVER)
                .build());
    }

    /**
     * Una richiesta di rete sul thread del server bloccherebbe il tick per tutto il timeout, e il
     * watchdog non può interromperla: meglio un errore che dice dove spostarla.
     */
    private static void requireOffMainThread(String method, String url)
    {
        if (ScriptHost.mainThread != null && ScriptHost.mainThread == Thread.currentThread())
        {
            throw new BlossomRuntimeError(SourcePosition.UNKNOWN, "http",
                    "Cannot run " + method + " " + url + " on the server thread: an http request would"
                            + " block the tick until it answers. Wrap the call in task(...) and read the"
                            + " result with task_join.");
        }
    }

    private static Map<String, String> headers(List<Value> lv, int index)
    {
        if (lv.size() <= index || lv.get(index).isNull())
        {
            return Map.of();
        }
        if (!(lv.get(index) instanceof final MapValue map))
        {
            throw new BlossomRuntimeError(SourcePosition.UNKNOWN, "http",
                    "Headers want a map, e.g. {'authorization' -> 'Bearer ...'}.");
        }
        Map<String, String> headers = new java.util.LinkedHashMap<>();
        map.unpack().forEach(pair ->
        {
            List<Value> entry = ((ListValue) pair).getItems();
            headers.put(entry.get(0).getString(), entry.get(1).getString());
        });
        return headers;
    }

    /** Le intestazioni con un default aggiunto solo se chi chiama non l'ha già deciso lui. */
    private static Map<String, String> with(Map<String, String> headers, String name, String value)
    {
        if (headers.keySet().stream().anyMatch(existing -> existing.equalsIgnoreCase(name)))
        {
            return headers;
        }
        Map<String, String> merged = new java.util.LinkedHashMap<>(headers);
        merged.put(name, value);
        return merged;
    }

    private static void arity(String name, List<Value> lv, int min, int max)
    {
        if (lv.size() < min || lv.size() > max)
        {
            throw new blossom.exception.ArityError(SourcePosition.UNKNOWN,
                    "'" + name + "' wants between " + min + " and " + max + " arguments, got " + lv.size() + ".");
        }
    }

    private static HttpAccess access(Context c)
    {
        return c.host.scriptServer().httpAccess();
    }

    private static ThrowStatement failure(String url, String cause)
    {
        return new ThrowStatement(StringValue.of("cannot reach '" + url + "': " + cause),
                Throwables.HTTP_EXCEPTION);
    }
}
