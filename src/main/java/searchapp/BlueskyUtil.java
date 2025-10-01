package searchapp;

import com.nimbusds.jose.util.JSONObjectUtils;
import org.json.JSONObject;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.net.URI;
import java.net.URLDecoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.Map;
import java.util.stream.Collectors;
import com.sun.net.httpserver.HttpServer;

public class BlueskyUtil {

    public static void startHttpServer(String codeVerifier, String originalState, BlueskyCallback callback) {
        new Thread(() -> {
            try {
                HttpServer server = HttpServer.create(new InetSocketAddress(8080), 0);
                server.createContext("/callback", exchange -> {
                    String query = exchange.getRequestURI().getQuery();
                    Map<String, String> params = parseQueryParams(query);

                    String code = params.get("code");
                    String state = params.get("state");
                    String iss = params.get("iss");

                    // Verify state matches the original state
                    if (!originalState.equals(state)) {
                        callback.onError("State mismatch! Possible CSRF detected.");
                        exchange.sendResponseHeaders(400, 0);
                        exchange.getResponseBody().close();
                        return;
                    }

                    // Respond to the browser
                    String response = "<html><body><h1>Login successful!</h1><p>You can close this window.</p></body></html>";
                    exchange.sendResponseHeaders(200, response.getBytes().length);
                    exchange.getResponseBody().write(response.getBytes());
                    exchange.getResponseBody().close();

                    server.stop(0);

                    // Exchange the code for tokens
                    exchangeCodeForTokens(code, codeVerifier, "https://grjimenez.github.io/bluesky-oauth-client/client-metadata.json", "http://127.0.0.1:8080/callback", callback);
                });
                server.start();
            } catch (IOException e) {
                callback.onError("Error starting HTTP server: " + e.getMessage());
            }
        }).start();
    }

    public static Map<String, String> parseQueryParams(String query) {
        return Arrays.stream(query.split("&"))
                .map(param -> param.split("=", 2))
                .collect(Collectors.toMap(
                        pair -> URLDecoder.decode(pair[0], StandardCharsets.UTF_8),
                        pair -> URLDecoder.decode(pair[1], StandardCharsets.UTF_8)
                ));
    }

    public static String parseRequestUri(String responseBody) throws Exception {
        JSONObject json = new JSONObject(responseBody);
        if (!json.has("request_uri")) {
            throw new Exception("PAR response does not contain 'request_uri'");
        }
        return json.getString("request_uri");
}

    public static void exchangeCodeForTokens(String code, String codeVerifier, String clientId, String redirectUri, BlueskyCallback callback) {
        try {
            final String tokenUrl = "https://bsky.social/oauth/token";
            String body = String.format(
                "grant_type=authorization_code&client_id=%s&redirect_uri=%s&code=%s&code_verifier=%s",
                clientId, redirectUri, code, codeVerifier
            );

            HttpClient client = HttpClient.newHttpClient();

            // First attempt
            String dpop1 = DPoPUtil.buildDPoP("POST", tokenUrl, null);
            HttpRequest firstRequest = HttpRequest.newBuilder()
                    .uri(URI.create(tokenUrl))
                    .header("Content-Type", "application/x-www-form-urlencoded")
                    .header("DPoP", dpop1)
                    .POST(HttpRequest.BodyPublishers.ofString(body))
                    .build();

            client.sendAsync(firstRequest, HttpResponse.BodyHandlers.ofString())
                    .thenCompose(resp -> {
                        System.out.println("[Token#1] " + resp.statusCode());
                        System.out.println("[Token#1] headers=" + resp.headers().map());
                        System.out.println("[Token#1] body=" + resp.body());

                        boolean is401 = resp.statusCode() == 401;
                        boolean isNonce400 = resp.statusCode() == 400 && resp.body() != null && resp.body().contains("\"use_dpop_nonce\"");
                        if (is401 || isNonce400) {
                            String nonce = resp.headers().firstValue("DPoP-Nonce")
                                    .orElse(resp.headers().firstValue("dpop-nonce").orElse(""));
                            if (!nonce.isEmpty()) {
                                String dpop2 = DPoPUtil.buildDPoP("POST", tokenUrl, nonce);
                                HttpRequest retryRequest = HttpRequest.newBuilder()
                                        .uri(URI.create(tokenUrl))
                                        .header("Content-Type", "application/x-www-form-urlencoded")
                                        .header("DPoP", dpop2)
                                        .POST(HttpRequest.BodyPublishers.ofString(body))
                                        .build();
                                return client.sendAsync(retryRequest, HttpResponse.BodyHandlers.ofString())
                                        .thenApply(r -> {
                                            System.out.println("[Token#2] " + r.statusCode());
                                            System.out.println("[Token#2] headers=" + r.headers().map());
                                            System.out.println("[Token#2] body=" + r.body());
                                            return r;
                                        });
                            }
                        }
                        return java.util.concurrent.CompletableFuture.completedFuture(resp);
                    })
                    .thenAccept(response -> {
                        if (response.statusCode() == 200) {
                            try {
                                TokenSet tokenSet = parseTokenResponse(response.body());
                                callback.onSuccess(tokenSet);
                            } catch (Exception ex) {
                                callback.onError("Error parsing token response: " + ex.getMessage());
                            }
                        } else {
                            callback.onError("Token exchange failed: " + response.body());
                        }
                    })
                    .exceptionally(e -> {
                        callback.onError("Token exchange error: " + e.getMessage());
                        return null;
                    });
        } catch (Exception e) {
            callback.onError(e.getMessage());
        }
    }

    public static void refreshAccessToken(String refreshToken, String clientId, BlueskyCallback callback) {
        try {
            final String tokenUrl = "https://bsky.social/oauth/token";
            String body = String.format(
                "grant_type=refresh_token&client_id=%s&refresh_token=%s",
                clientId, refreshToken
            );

            HttpClient client = HttpClient.newHttpClient();

            // First attempt
            String dpop1 = DPoPUtil.buildDPoP("POST", tokenUrl, null);
            HttpRequest firstRequest = HttpRequest.newBuilder()
                    .uri(URI.create(tokenUrl))
                    .header("Content-Type", "application/x-www-form-urlencoded")
                    .header("DPoP", dpop1)
                    .POST(HttpRequest.BodyPublishers.ofString(body))
                    .build();

            client.sendAsync(firstRequest, HttpResponse.BodyHandlers.ofString())
                    .thenCompose(resp -> {
                        System.out.println("[Refresh#1] " + resp.statusCode());
                        System.out.println("[Refresh#1] headers=" + resp.headers().map());
                        System.out.println("[Refresh#1] body=" + resp.body());

                        boolean is401 = resp.statusCode() == 401;
                        boolean isNonce400 = resp.statusCode() == 400 && resp.body() != null && resp.body().contains("\"use_dpop_nonce\"");
                        if (is401 || isNonce400) {
                            String nonce = resp.headers().firstValue("DPoP-Nonce")
                                    .orElse(resp.headers().firstValue("dpop-nonce").orElse(""));
                            if (!nonce.isEmpty()) {
                                String dpop2 = DPoPUtil.buildDPoP("POST", tokenUrl, nonce);
                                HttpRequest retryRequest = HttpRequest.newBuilder()
                                        .uri(URI.create(tokenUrl))
                                        .header("Content-Type", "application/x-www-form-urlencoded")
                                        .header("DPoP", dpop2)
                                        .POST(HttpRequest.BodyPublishers.ofString(body))
                                        .build();
                                return client.sendAsync(retryRequest, HttpResponse.BodyHandlers.ofString())
                                        .thenApply(r -> {
                                            System.out.println("[Refresh#2] " + r.statusCode());
                                            System.out.println("[Refresh#2] headers=" + r.headers().map());
                                            System.out.println("[Refresh#2] body=" + r.body());
                                            return r;
                                        });
                            }
                        }
                        return java.util.concurrent.CompletableFuture.completedFuture(resp);
                    })
                    .thenAccept(response -> {
                        if (response.statusCode() == 200) {
                            try {
                                TokenSet tokenSet = parseTokenResponse(response.body());
                                callback.onSuccess(tokenSet);
                            } catch (Exception ex) {
                                callback.onError("Error parsing token response: " + ex.getMessage());
                            }
                        } else {
                            callback.onError("Token refresh failed: " + response.body());
                        }
                    })
                    .exceptionally(e -> {
                        callback.onError("Token refresh error: " + e.getMessage());
                        return null;
                    });
        } catch (Exception e) {
            callback.onError(e.getMessage());
        }
    }

    private static TokenSet parseTokenResponse(String responseBody) throws Exception {
        JSONObject json = new JSONObject(responseBody);
        String accessToken = json.getString("access_token");
        String refreshToken = json.optString("refresh_token", null);
        long expiresIn = json.getLong("expires_in");
        String subject = json.optString("sub", null);

        return new TokenSet(accessToken, refreshToken, expiresIn, subject);
    }

    public static class TokenSet {
        public final String accessToken;
        public final String refreshToken;
        public final long expiresIn;
        public final String subject;

        public TokenSet(String accessToken, String refreshToken, long expiresIn, String subject) {
            this.accessToken = accessToken;
            this.refreshToken = refreshToken;
            this.expiresIn = expiresIn;
            this.subject = subject;
        }
    }

    public interface BlueskyCallback {
        void onSuccess(TokenSet tokenSet);

        void onError(String errorMessage);
    }
}