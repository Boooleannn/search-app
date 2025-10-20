package auth.mastodon;

import org.json.JSONObject;
import auth.mastodon.ClientRegistry.ClientInfo;

import java.awt.Desktop;
import java.net.URI;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.net.http.*;
import java.util.UUID;
import java.util.function.Consumer;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;

public class MastodonAuth {
    public static class MastodonAccount {
        public final String id, username, acct, displayName, avatar;
        public MastodonAccount(String id, String username, String acct, String displayName, String avatar) {
            this.id = id; this.username = username; this.acct = acct; this.displayName = displayName; this.avatar = avatar;
        }
    }
    public static class MastodonSession {
        public final String instance;
        public final String accessToken;
        public final MastodonAccount account;
        public MastodonSession(String instance, String accessToken, MastodonAccount account) {
            this.instance = instance; this.accessToken = accessToken; this.account = account;
        }
    }

    private static final HttpClient HTTP = HttpClient.newHttpClient();

    public static void startLoginWithHandle(String input, Consumer<MastodonSession> onSuccess, Consumer<String> onError) {
        new Thread(() -> {
            try {
                String instance = parseInstance(input);
                if (instance == null || instance.isBlank()) { onError.accept("Could not parse instance from input"); return; }

                ClientInfo clientInfo = ClientRegistry.get(instance).orElseGet(() -> {
                    try { return ClientRegistry.register(instance); } catch (Exception e) { throw new RuntimeException(e); }
                });

                String state = UUID.randomUUID().toString();
                String authUrl = "https://" + instance + "/oauth/authorize"
                        + "?client_id=" + urlEnc(clientInfo.clientId)
                        + "&redirect_uri=" + urlEnc(clientInfo.redirectUri)
                        + "&response_type=code"
                        + "&scope=" + urlEnc(clientInfo.scope)
                        + "&state=" + urlEnc(state);

                // open browser
                try { Desktop.getDesktop().browse(URI.create(authUrl)); } catch (Exception ex) {
                    // fallback: nothing else in non-JFX thread
                }

                // start callback server
                try (LocalCallbackServer server = new LocalCallbackServer()) {
                    server.start();
                    LocalCallbackServer.CallbackResult cb = server.awaitCallbackSeconds(180);
                    if (cb == null) { onError.accept("No callback received (timeout)"); return; }
                    if (!state.equals(cb.state)) { onError.accept("State mismatch"); return; }

                    // exchange code for token
                    JSONObject tokenReq = new JSONObject();
                    tokenReq.put("client_id", clientInfo.clientId);
                    tokenReq.put("client_secret", clientInfo.clientSecret);
                    tokenReq.put("grant_type", "authorization_code");
                    tokenReq.put("code", cb.code);
                    tokenReq.put("redirect_uri", clientInfo.redirectUri);
                    tokenReq.put("scope", clientInfo.scope);

                    HttpRequest tokenRequest = HttpRequest.newBuilder()
                            .uri(URI.create("https://" + instance + "/oauth/token"))
                            .header("Content-Type", "application/json")
                            .POST(HttpRequest.BodyPublishers.ofString(tokenReq.toString()))
                            .build();

                    HttpResponse<String> tokenResp = HTTP.send(tokenRequest, HttpResponse.BodyHandlers.ofString());
                    if (tokenResp.statusCode() != 200) {
                        onError.accept("Token exchange failed: " + tokenResp.statusCode());
                        return;
                    }

                    JSONObject tokenJson = new JSONObject(tokenResp.body());
                    String accessToken = tokenJson.optString("access_token", null);
                    if (accessToken == null) { onError.accept("No access_token in token response"); return; }

                    // verify credentials
                    HttpRequest verify = HttpRequest.newBuilder()
                            .uri(URI.create("https://" + instance + "/api/v1/accounts/verify_credentials"))
                            .header("Authorization", "Bearer " + accessToken)
                            .GET()
                            .build();

                    HttpResponse<String> verifyResp = HTTP.send(verify, HttpResponse.BodyHandlers.ofString());
                    if (verifyResp.statusCode() != 200) {
                        onError.accept("verify_credentials failed: " + verifyResp.statusCode());
                        return;
                    }

                    JSONObject acc = new JSONObject(verifyResp.body());
                    MastodonAccount account = new MastodonAccount(
                            acc.optString("id", ""),
                            acc.optString("username", ""),
                            acc.optString("acct", ""),
                            acc.optString("display_name", ""),
                            acc.optString("avatar", "")
                    );

                    // success
                    onSuccess.accept(new MastodonSession(instance, accessToken, account));
                }

            } catch (Exception e) {
                onError.accept("Mastodon login error: " + e.getMessage());
            }
        }, "mastodon-oauth").start();
    }

    private static String parseInstance(String input) {
        if (input == null) return null;
        String s = input.trim();
        if (s.startsWith("https://")) s = s.substring(8);
        if (s.startsWith("http://")) s = s.substring(7);
        s = s.replaceAll("^@", "");
        // formats: user@host or @user@host or host or host:port or user
        if (s.contains("@")) {
            String[] parts = s.split("@");
            // last part is host
            s = parts[parts.length - 1];
        }
        // if contains '/', take before
        if (s.contains("/")) s = s.substring(0, s.indexOf("/"));
        return s.toLowerCase();
    }

    private static String urlEnc(String s) { return URLEncoder.encode(s, StandardCharsets.UTF_8); }
    
}

