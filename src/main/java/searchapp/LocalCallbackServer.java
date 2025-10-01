package searchapp;

import com.sun.net.httpserver.HttpServer;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;
import java.util.Arrays;

public class LocalCallbackServer {

    private HttpServer server;
    private final CompletableFuture<CallbackResult> callbackFuture = new CompletableFuture<>();

    /**
     * Starts the HTTP server on 127.0.0.1:8080 and listens for /callback requests.
     */
    public void start() throws IOException {
        server = HttpServer.create(new InetSocketAddress("127.0.0.1", 8080), 0);
        server.createContext("/callback", exchange -> {
            if ("GET".equals(exchange.getRequestMethod())) {
                String query = exchange.getRequestURI().getQuery();
                Map<String, String> params = parseQueryParams(query);

                String code = params.get("code");
                String state = params.get("state");
                String iss = params.get("iss");

                // Save the callback result
                callbackFuture.complete(new CallbackResult(code, state, iss));

                // Respond to the browser
                String response = "<html><body><h1>Login successful!</h1><p>You can close this window.</p></body></html>";
                exchange.sendResponseHeaders(200, response.getBytes().length);
                exchange.getResponseBody().write(response.getBytes());
                exchange.getResponseBody().close();

                // Stop the server
                stop();
            } else {
                exchange.sendResponseHeaders(405, 0); // Method Not Allowed
                exchange.getResponseBody().close();
            }
        });
        server.start();
    }

    /**
     * Stops the HTTP server.
     */
    public void stop() {
        if (server != null) {
            server.stop(0);
        }
    }

    /**
     * Blocks until the authorization code and state are received or the timeout expires.
     *
     * @param timeoutSeconds The maximum time to wait for the callback.
     * @return The callback result containing code, state, and iss, or null if timeout occurs.
     */
    public CallbackResult awaitAuthorizationCode(int timeoutSeconds) {
        try {
            return callbackFuture.get(timeoutSeconds, TimeUnit.SECONDS);
        } catch (Exception e) {
            return null; // Timeout or error
        }
    }

    /**
     * Parses query parameters from a URL query string.
     *
     * @param query The query string.
     * @return A map of query parameter names to values.
     */
    private Map<String, String> parseQueryParams(String query) {
        return Arrays.stream(query.split("&"))
                .map(param -> param.split("=", 2))
                .collect(Collectors.toMap(
                        pair -> URLDecoder.decode(pair[0], StandardCharsets.UTF_8),
                        pair -> URLDecoder.decode(pair[1], StandardCharsets.UTF_8)
                ));
    }

    /**
     * Represents the result of the callback.
     */
    public static class CallbackResult {
        private final String code;
        private final String state;
        private final String iss;

        public CallbackResult(String code, String state, String iss) {
            this.code = code;
            this.state = state;
            this.iss = iss;
        }

        public String code() {
            return code;
        }

        public String state() {
            return state;
        }

        public String iss() {
            return iss;
        }
    }
}