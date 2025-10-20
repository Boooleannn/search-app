package auth.mastodon;

import org.json.JSONObject;
import org.json.JSONArray;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.file.*;
import java.util.Optional;

public class ClientRegistry {
    private static final Path STORE = Paths.get("mastodon_clients.json");
    private static final HttpClient CLIENT = HttpClient.newHttpClient();

    public static class ClientInfo {
        public final String clientId;
        public final String clientSecret;
        public final String redirectUri;
        public final String scope;
        public ClientInfo(String clientId, String clientSecret, String redirectUri, String scope) {
            this.clientId = clientId; this.clientSecret = clientSecret; this.redirectUri = redirectUri; this.scope = scope;
        }
    }

    private static JSONObject loadStore() {
        try {
            if (Files.exists(STORE)) {
                String s = Files.readString(STORE);
                return new JSONObject(s);
            }
        } catch (Exception ignored) {}
        return new JSONObject();
    }

    private static void saveStore(JSONObject obj) {
        try {
            Files.writeString(STORE, obj.toString(2), StandardOpenOption.CREATE, StandardOpenOption.TRUNCATE_EXISTING);
        } catch (Exception ignored) {}
    }

    public static Optional<ClientInfo> get(String instance) {
        JSONObject store = loadStore();
        if (!store.has(instance)) return Optional.empty();
        JSONObject entry = store.getJSONObject(instance);
        return Optional.of(new ClientInfo(
                entry.optString("client_id"),
                entry.optString("client_secret"),
                entry.optString("redirect_uri"),
                entry.optString("scope", "read")
        ));
    }

    public static ClientInfo register(String instance) throws Exception {
        // register app per prompt
        String url = "https://" + instance + "/api/v1/apps";
        JSONObject body = new JSONObject();
        body.put("client_name", "MyApp");
        body.put("redirect_uris", "http://127.0.0.1:8765/callback");
        body.put("scopes", "read");
        body.put("website", "https://example.com");

        HttpRequest req = HttpRequest.newBuilder()
                .uri(URI.create(url))
                .header("Content-Type", "application/json")
                .POST(HttpRequest.BodyPublishers.ofString(body.toString()))
                .build();

        HttpResponse<String> resp = CLIENT.send(req, HttpResponse.BodyHandlers.ofString());
        if (resp.statusCode() != 200 && resp.statusCode() != 201) {
            throw new RuntimeException("App registration failed: " + resp.statusCode() + " " + resp.body());
        }

        JSONObject json = new JSONObject(resp.body());
        String clientId = json.getString("client_id");
        String clientSecret = json.getString("client_secret");
        String redirectUri = json.optString("redirect_uri", "http://127.0.0.1:8765/callback");
        String scope = json.optString("scope", "read");

        JSONObject store = loadStore();
        JSONObject entry = new JSONObject();
        entry.put("client_id", clientId);
        entry.put("client_secret", clientSecret);
        entry.put("redirect_uri", redirectUri);
        entry.put("scope", scope);
        store.put(instance, entry);
        saveStore(store);

        return new ClientInfo(clientId, clientSecret, redirectUri, scope);
    }
}