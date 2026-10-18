package blossom;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Set;

import com.sun.net.httpserver.HttpServer;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import blossom.exception.BlossomRuntimeError;
import blossom.interpreter.BlossomServer;
import blossom.interpreter.Interpreter;
import blossom.language.HttpAccess;

/**
 * {@code core.http}: requests to declared hosts.
 *
 * <p>As with {@code core.io}, half the tests cover what must not work: an undeclared host, a
 * disallowed scheme, a redirect that leads outside the allowlist.
 */
class HttpTest
{
    private static final String IMPORT = "import \"core.http\" as http; ";

    private HttpServer server;
    private String base;

    @BeforeEach
    void startServer() throws IOException
    {
        server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        server.createContext("/hello", exchange -> respond(exchange, 200, "hello"));
        server.createContext("/json", exchange -> respond(exchange, 200, "{\"a\": 1}"));
        server.createContext("/missing", exchange -> respond(exchange, 404, "not here"));
        server.createContext("/echo", exchange ->
        {
            String body = new String(exchange.getRequestBody().readAllBytes(), StandardCharsets.UTF_8);
            respond(exchange, 200, exchange.getRequestMethod() + ":" + body
                    + ":" + exchange.getRequestHeaders().getFirst("x-token"));
        });
        server.createContext("/away", exchange ->
        {
            exchange.getResponseHeaders().add("location", "https://altrove.example/preso");
            respond(exchange, 302, "");
        });
        server.createContext("/big", exchange -> respond(exchange, 200, "x".repeat(4096)));
        server.start();
        base = "http://127.0.0.1:" + server.getAddress().getPort();
    }

    @AfterEach
    void stopServer()
    {
        server.stop(0);
    }

    private static void respond(com.sun.net.httpserver.HttpExchange exchange, int status, String body)
            throws IOException
    {
        byte[] content = body.getBytes(StandardCharsets.UTF_8);
        exchange.sendResponseHeaders(status, content.length);
        try (OutputStream out = exchange.getResponseBody())
        {
            out.write(content);
        }
    }

    private Interpreter withAccess(HttpAccess access)
    {
        Interpreter interpreter = Interpreter.standalone();
        ((BlossomServer) interpreter.host().scriptServer()).setHttpAccess(access);
        return interpreter;
    }

    private Interpreter localhost()
    {
        return withAccess(new HttpAccess(List.of("127.0.0.1"), Set.of("http"), 5_000, 1 << 20, false));
    }

    @Test
    void withoutHostsNothingIsReachable()
    {
        Interpreter interpreter = Interpreter.standalone();
        BlossomRuntimeError error = assertThrows(BlossomRuntimeError.class,
                () -> interpreter.eval(IMPORT + "http.get('https://esempio.org/')"));
        assertTrue(error.getMessage().contains("No http host"), error.getMessage());
        assertEquals("[]", interpreter.eval(IMPORT + "http.hosts()").getString());
    }

    @Test
    void getReturnsStatusBodyAndHeaders()
    {
        Interpreter interpreter = localhost();
        assertEquals("200", interpreter.eval(IMPORT + "http.get('" + base + "/hello'):'status'").getString());
        assertEquals("hello", interpreter.eval(IMPORT + "http.get('" + base + "/hello'):'body'").getString());
        assertEquals("true", interpreter.eval(IMPORT + "import \"core.map\" as map; "
                + "map.has(http.get('" + base + "/hello'):'headers', 'content-length')").getString());
    }

    @Test
    void aStatusOutsideTwoHundredIsAnAnswerAndNotAnError()
    {
        // a 404 is information, not a failure: the caller decides what to do with it
        assertEquals("404", localhost().eval(IMPORT + "http.get('" + base + "/missing'):'status'").getString());
    }

    @Test
    void postSendsBodyAndHeaders()
    {
        assertEquals("POST:dati:abc", localhost().eval(
                IMPORT + "http.post('" + base + "/echo', 'dati', {'x-token' -> 'abc'}):'body'").getString());
    }

    @Test
    void requestChoosesTheMethod()
    {
        assertEquals("PUT:x:null", localhost().eval(
                IMPORT + "http.request('put', '" + base + "/echo', 'x'):'body'").getString());
    }

    @Test
    void jsonHelpersDecodeAndEncode()
    {
        Interpreter interpreter = localhost();
        assertEquals("1", interpreter.eval(IMPORT + "http.get_json('" + base + "/json'):'a'").getString());
        // the body sent is json, and the response comes back already decoded
        assertEquals("POST:{\"a\":1}:null", interpreter.eval(IMPORT + "import \"core.json\" as json; "
                + "http.post('" + base + "/echo', json.encode({'a' -> 1})):'body'").getString());
        assertEquals("1", interpreter.eval(IMPORT + "http.post_json('" + base + "/json', {'a' -> 1}):'a'").getString());
    }

    @Test
    void anUndeclaredHostIsRefused()
    {
        BlossomRuntimeError error = assertThrows(BlossomRuntimeError.class,
                () -> localhost().eval(IMPORT + "http.get('http://altrove.example/')"));
        assertTrue(error.getMessage().contains("is not allowed"), error.getMessage());
    }

    @Test
    void anUndeclaredSchemeIsRefused()
    {
        BlossomRuntimeError error = assertThrows(BlossomRuntimeError.class,
                () -> localhost().eval(IMPORT + "http.get('ftp://127.0.0.1/x')"));
        assertTrue(error.getMessage().contains("Scheme"), error.getMessage());
    }

    @Test
    void aRedirectOutOfTheAllowlistIsRefused()
    {
        // following 3xx redirects without re-checking the host would make the allowlist
        // bypassable by the server on the other end, which is exactly who doesn't set the rules here
        Interpreter interpreter = withAccess(
                new HttpAccess(List.of("127.0.0.1"), Set.of("http", "https"), 5_000, 1 << 20, true));
        BlossomRuntimeError error = assertThrows(BlossomRuntimeError.class,
                () -> interpreter.eval(IMPORT + "http.get('" + base + "/away')"));
        assertTrue(error.getMessage().contains("altrove.example"), error.getMessage());
    }

    @Test
    void aRedirectIsNotFollowedUnlessConfigured()
    {
        assertEquals("302", localhost().eval(IMPORT + "http.get('" + base + "/away'):'status'").getString());
    }

    @Test
    void aBodyOverTheLimitIsRefused()
    {
        Interpreter interpreter = withAccess(new HttpAccess(List.of("127.0.0.1"), Set.of("http"), 5_000, 64, false));
        assertEquals("too large", interpreter.eval(
                IMPORT + "try(http.get('" + base + "/big'), 'http_exception', 'too large')").getString());
    }

    @Test
    void networkFailuresAreCatchable()
    {
        // a closed port is a network condition: it can be caught, unlike a forbidden host
        Interpreter interpreter = withAccess(
                new HttpAccess(List.of("127.0.0.1"), Set.of("http"), 500, 1 << 20, false));
        assertEquals("giu'", interpreter.eval(
                IMPORT + "try(http.get('http://127.0.0.1:1/x'), 'http_exception', 'giu\\'')").getString());
    }

    @Test
    void urlHelpersEncodeAndDecode()
    {
        Interpreter interpreter = localhost();
        assertEquals("a+b%2Fc", interpreter.eval(IMPORT + "http.encode_url('a b/c')").getString());
        assertEquals("a b/c", interpreter.eval(IMPORT + "http.decode_url('a+b%2Fc')").getString());
        assertEquals("q=two+words&t=1", interpreter.eval(
                IMPORT + "http.encode_query({'q' -> 'two words', 't' -> 1})").getString());
    }

    @Test
    void anUnsupportedMethodIsRefused()
    {
        BlossomRuntimeError error = assertThrows(BlossomRuntimeError.class,
                () -> localhost().eval(IMPORT + "http.request('trace', '" + base + "/hello')"));
        assertTrue(error.getMessage().contains("Unsupported HTTP method"), error.getMessage());
    }
}
