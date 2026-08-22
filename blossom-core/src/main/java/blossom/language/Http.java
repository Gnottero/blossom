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
 * {@code core.http}: HTTP requests to declared hosts.
 *
 * <p>Every call returns a map {@code {'status', 'body', 'headers', 'url'}}. A status outside 2xx
 * is not an error: it is a response, and it is up to the caller to decide what to do with it.
 * Errors are things that prevent a response from being produced (unreachable host, timeout, body
 * too large), and they arrive as {@code http_exception}, so they are caught with
 * {@code try(..., 'http_exception', ...)}.
 *
 * <p>Two boundaries, both intentional and not bypassable by a script:
 *
 * <ul>
 *   <li>the reachable hosts are declared by an administrator (see {@link HttpAccess}); without any
 *       declarations these functions exist but open nothing;</li>
 *   <li>a request never starts from the server thread. A slow response there would block the tick,
 *       and the watchdog cannot interrupt a socket read: the code goes inside a
 *       {@code task(...)}, where the elapsed time is not paid for by any player.</li>
 * </ul>
 */
public final class Http
{
    /** The symbols exported by {@code core.http}: public name to implementation name. */
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

    /** Beyond this many hops a redirect chain is a loop, not a path. */
    private static final int MAX_REDIRECTS = 5;

    /**
     * One client per configuration: creating one opens a connection pool and a selector, and
     * doing that on every request would cost more than the request itself.
     */
    private static final Map<Integer, HttpClient> CLIENTS = new ConcurrentHashMap<>();

    private Http()
    {
    }

    public static void apply(Expression expression)
    {
        // which hosts are reachable: a script that wants to be polite can check this before
        // failing, the same way io.roots() does
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

        // a hand-written query string is the easiest way to send an unencoded value: here the
        // keys and values all go through the same encoder
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
     * Executes the request, following redirects manually if the configuration allows it.
     *
     * <p>Redirects are not followed by {@link HttpClient} itself because its policy does not
     * re-check the destination host: a {@code 302} to an undeclared domain would bypass the
     * entire allowlist.
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
                // names reserved by the JVM (host, connection, ...) cannot be set: reporting this
                // is better than silently sending a request different from the one that was written
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

    /** The response as a map, with the body read up to the declared limit and no further. */
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
     * Reads the body, stopping one byte past the limit.
     *
     * <p>The limit is enforced by reading rather than by trusting {@code content-length}: that
     * header is written by the server on the other end, and a lying server would fill up our
     * memory.
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

    /** The body of a redirect response is useless to anyone, but must be closed or the connection stays hung. */
    private static void drain(HttpResponse<InputStream> response)
    {
        try (InputStream stream = response.body())
        {
            stream.readAllBytes();
        }
        catch (IOException e)
        {
            // the next request will open a fresh connection: there is nothing to report
        }
    }

    /** The response body interpreted as JSON, with the status still accessible. */
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
     * A network request on the server thread would block the tick for the full timeout, and the
     * watchdog cannot interrupt it: better an error that says where to move it.
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

    /** The headers with a default added only if the caller has not already decided one. */
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
