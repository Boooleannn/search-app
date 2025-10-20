package auth.mastodon;

import com.sun.net.httpserver.HttpServer;
import com.sun.net.httpserver.HttpExchange;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.net.URI;
import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
import java.util.Map;
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.BlockingQueue;

public class LocalCallbackServer implements AutoCloseable {
    public static class CallbackResult {
        public final String code;
        public final String state;
        public CallbackResult(String code, String state) { this.code = code; this.state = state; }
    }

    private HttpServer server;
    private final BlockingQueue<CallbackResult> queue = new ArrayBlockingQueue<>(1);

    public void start() throws Exception {
       server = HttpServer.create(new InetSocketAddress(8765), 0); // bind all local addresses (IPv4+IPv6)
       server.createContext("/callback", exchange -> {
        try {
            System.out.println("[mastodon-cb] incoming " + exchange.getRequestMethod() + " " + exchange.getRequestURI());
            handleCallback(exchange);
        } catch (Exception e) { e.printStackTrace(); }
        });
        server.setExecutor(java.util.concurrent.Executors.newFixedThreadPool(2, r -> new Thread(r, "mastodon-callback")));
        server.start();
        System.out.println("[mastodon-cb] started on port 8765");
    }

    private void handleCallback(HttpExchange ex) {
        try {
            URI r = ex.getRequestURI();
            String query = r.getRawQuery() == null ? "" : r.getRawQuery();
            System.out.println("[mastodon-cb] query: " + query);
            Map<String, String> params = parseQuery(query);
            String code = params.getOrDefault("code", "");
            String state = params.getOrDefault("state", "");
            String html = "<html><body><h3>You can close this window and return to the app.</h3></body></html>";
            ex.getResponseHeaders().add("Content-Type", "text/html; charset=utf-8");
            ex.sendResponseHeaders(200, html.getBytes(StandardCharsets.UTF_8).length);
            try (OutputStream os = ex.getResponseBody()) { os.write(html.getBytes(StandardCharsets.UTF_8)); }
            boolean offered = queue.offer(new CallbackResult(code, state));
            System.out.println("[mastodon-cb] queued result offered=" + offered + " code=" + (code.isEmpty() ? "<empty>" : "<redacted>") + " state=" + state);
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    private Map<String, String> parseQuery(String q) {
        return java.util.Arrays.stream(q.split("&"))
                .map(p -> p.split("=", 2))
                .filter(arr -> arr.length == 2)
                .collect(java.util.stream.Collectors.toMap(
                        a -> urlDecode(a[0]),
                        a -> urlDecode(a[1])
                ));
    }

    private String urlDecode(String s) { return URLDecoder.decode(s, StandardCharsets.UTF_8); }

    /**
     * Wait for an authorization callback. Returns null on timeout.
     */
    public CallbackResult awaitCallbackSeconds(int seconds) {
        try {
            return queue.poll(seconds, java.util.concurrent.TimeUnit.SECONDS);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            return null;
        }
    }

    public void stop() {
        if (server != null) server.stop(0);
    }

    @Override
    public void close() { stop(); }
}