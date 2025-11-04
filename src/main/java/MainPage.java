import javafx.application.Application;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.Scene;
import javafx.scene.control.*;
import javafx.scene.image.Image;
import javafx.scene.image.ImageView;
import javafx.stage.Stage;
import javafx.scene.layout.*;
import javafx.scene.paint.Color;
import javafx.scene.text.Font;  
import javafx.scene.Node;
import javafx.util.Duration;
import javafx.animation.ScaleTransition;
import javafx.animation.TranslateTransition;
import javafx.application.Platform;

import org.json.JSONObject;

import searchapp.BlueskyUtil;
import searchapp.DPoPUtil;
import searchapp.LocalCallbackServer;
import searchapp.PkceUtil;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;

public class MainPage extends Application {

    private BorderPane root;
    private VBox loginFormContainer;
    private Label statusLabel;
    private LocalCallbackServer server;
    private String mastodonAccessToken;
    private String mastodonInstance;
    private String mastodonAcct;
    private String mastodonDisplayName;
    private String blueskyAccessToken;
    private String blueskyAcct = "";
    private HomePage currentHomePage;

    static String nz(String s, String fallback) {
        return (s == null || s.isBlank()) ? fallback : s;
    }
    // remove .bsky.social suffix from handle
    static String stripBskySuffix(String h) {
        if (h == null) return "";
        String suf = ".bsky.social";
        return h.endsWith(suf) ? h.substring(0, h.length() - suf.length()) : h;
    }

    // Try to extract DID from JWT-like token (safe if not JWT)
    static String didFromJwtIfAny(String token) {
        try {
            if (token == null || token.isBlank()) return "";
            String[] parts = token.split("\\.");
            if (parts.length != 3) return "";
            String payload = new String(
                java.util.Base64.getUrlDecoder().decode(parts[1]),
                java.nio.charset.StandardCharsets.UTF_8
            );
            org.json.JSONObject claims = new org.json.JSONObject(payload);
            return claims.optString("sub", "");
        } catch (Exception e) {
            System.out.println("[BLSKY][WARN] didFromJwtIfAny failed: " + e);
            return "";
        }
    }

    // Public: app.bsky.actor.getProfile -> handle or ""
    static java.util.concurrent.CompletableFuture<String> getProfileHandle(String actor) {
        try {
            String url = "https://public.api.bsky.app/xrpc/app.bsky.actor.getProfile?actor="
                       + java.net.URLEncoder.encode(actor, java.nio.charset.StandardCharsets.UTF_8);
            var req = java.net.http.HttpRequest.newBuilder()
                .uri(java.net.URI.create(url))
                .header("Accept", "application/json")
                .GET().build();
            return java.net.http.HttpClient.newHttpClient()
                .sendAsync(req, java.net.http.HttpResponse.BodyHandlers.ofString())
                .thenApply(resp -> {
                    System.out.println("[BLSKY] getProfile(" + actor + ") HTTP " + resp.statusCode());
                    if (resp.statusCode() == 200) {
                        var obj = new org.json.JSONObject(resp.body());
                        return obj.optString("handle", "");
                    }
                    System.out.println("[BLSKY][ERR] getProfile body=" + resp.body());
                    return "";
                });
        } catch (Exception e) {
            var f = new java.util.concurrent.CompletableFuture<String>();
            f.completeExceptionally(e);
            return f;
        }
    }

    // Public: app.bsky.actor.searchActors -> first handle or ""
    static java.util.concurrent.CompletableFuture<String> searchActorsFirstHandle(String query) {
        try {
            String url = "https://public.api.bsky.app/xrpc/app.bsky.actor.searchActors?q="
                       + java.net.URLEncoder.encode(query, java.nio.charset.StandardCharsets.UTF_8) + "&limit=1";
            var req = java.net.http.HttpRequest.newBuilder()
                .uri(java.net.URI.create(url))
                .header("Accept", "application/json")
                .GET().build();
            return java.net.http.HttpClient.newHttpClient()
                .sendAsync(req, java.net.http.HttpResponse.BodyHandlers.ofString())
                .thenApply(resp -> {
                    System.out.println("[BLSKY] searchActors(" + query + ") HTTP " + resp.statusCode());
                    if (resp.statusCode() == 200) {
                        var obj = new org.json.JSONObject(resp.body());
                        var arr = obj.optJSONArray("actors");
                        if (arr != null && arr.length() > 0) {
                            String handle = arr.getJSONObject(0).optString("handle", "");
                            System.out.println("[BLSKY] searchActors -> " + handle);
                            return handle;
                        }
                    }
                    return "";
                });
        } catch (Exception e) {
            var f = new java.util.concurrent.CompletableFuture<String>();
            f.completeExceptionally(e);
            return f;
        }
    }

    // Resolve handle: DID from token -> getProfile(DID) -> getProfile(seed) -> seed+".bsky.social" -> search
    static java.util.concurrent.CompletableFuture<String> resolveBlueskyHandle(String seed, String accessToken) {
        String did = didFromJwtIfAny(accessToken);
        System.out.println("[BLSKY] resolve: seed=" + seed + " didFromToken=" + did);

        java.util.concurrent.CompletableFuture<String> start =
            did.isBlank() ? java.util.concurrent.CompletableFuture.completedFuture("") : getProfileHandle(did);

        return start.thenCompose(h -> {
            if (!h.isBlank()) return java.util.concurrent.CompletableFuture.completedFuture(h);
            return seed.isBlank() ? java.util.concurrent.CompletableFuture.completedFuture("") : getProfileHandle(seed);
        }).thenCompose(h -> {
            if (!h.isBlank()) return java.util.concurrent.CompletableFuture.completedFuture(h);
            if (!seed.isBlank() && !seed.contains(".") && !seed.startsWith("did:plc:")) {
                return getProfileHandle(seed + ".bsky.social");
            }
            return java.util.concurrent.CompletableFuture.completedFuture("");
        }).thenCompose(h -> {
            if (!h.isBlank()) return java.util.concurrent.CompletableFuture.completedFuture(h);
            return seed.isBlank() ? java.util.concurrent.CompletableFuture.completedFuture("") : searchActorsFirstHandle(seed);
        }).exceptionally(e -> {
            System.out.println("[BLSKY][ERR] resolveBlueskyHandle: " + e);
            return "";
        });
    }
    
    private void loadSFProFonts() {
        try {
            // Load different weights of SF Pro Display
            Font.loadFont(getClass().getResourceAsStream("/fonts/SF-Pro-Display-Regular.otf"), 14);
            Font.loadFont(getClass().getResourceAsStream("/fonts/SF-Pro-Display-Medium.otf"), 14);
            Font.loadFont(getClass().getResourceAsStream("/fonts/SF-Pro-Display-Bold.otf"), 14);
            Font.loadFont(getClass().getResourceAsStream("/fonts/SF-Pro-Display-Semibold.otf"), 14);
            Font.loadFont(getClass().getResourceAsStream("/fonts/SF-Pro-Display-Light.otf"), 14);
            Font.loadFont(getClass().getResourceAsStream("/fonts/SF-Pro-Display-Heavy.otf"), 14);
        } catch (Exception e) {
            System.err.println("Error loading SF Pro Display fonts: " + e.getMessage());
        }
    }
    
    @Override
    public void start(Stage stage) {
        loadSFProFonts();
        
        try {
        Image appIcon = new Image(getClass().getResourceAsStream("/images/app-icon.png"));
        stage.getIcons().add(appIcon);
        } catch (Exception e) {
            System.err.println("Failed to load application icon: " + e.getMessage());
        }
        stage.setTitle("Social Search: Bluesky + Mastodon");

        statusLabel = new Label("Select a platform to log in.");
        statusLabel.setStyle("-fx-text-fill: #007acc; -fx-font-weight: bold; -fx-font-family: 'SF Pro Display';");

        loginFormContainer = new VBox();
        loginFormContainer.setAlignment(Pos.CENTER);
        loginFormContainer.setPadding(new Insets(40));
        loginFormContainer.setSpacing(20);


        root = new BorderPane();
        root.setCenter(loginFormContainer);
        root.setBottom(statusLabel);
        BorderPane.setMargin(statusLabel, new Insets(10));
        root.setStyle("-fx-background-color: #f0f8ff;");

        // Show Platform Selector (Buttons)
        showPlatformSelector();


        Scene scene = new Scene(root, 800, 500);
        stage.setScene(scene);
        stage.show();
    }

    public void showPlatformSelector() {
        loginFormContainer.getChildren().clear();

        Label titleLabel = new Label("Choose Platform to Log In");
        titleLabel.setStyle("-fx-font-size: 24px; -fx-font-weight: bold; -fx-text-fill: #005fa3; -fx-font-family: 'SF Pro Display Bold';");
        titleLabel.setAlignment(Pos.CENTER);

        // Load Images
        Image blueskyImage = new Image(getClass().getResourceAsStream("/images/Bluesky_Logo.png"));
        Image mastodonImage = new Image(getClass().getResourceAsStream("/images/mastodon.svg.png"));

        ImageView blueskyView = new ImageView(blueskyImage);
        blueskyView.setFitHeight(40);
        blueskyView.setFitWidth(40);
        blueskyView.setPreserveRatio(true);

        ImageView mastodonView = new ImageView(mastodonImage);
        mastodonView.setFitHeight(40);
        mastodonView.setFitWidth(40);
        mastodonView.setPreserveRatio(true);

        // Bluesky Button with Image
        Button blueskyButton = new Button("Bluesky", blueskyView);
        styleBigButton(blueskyButton);
        blueskyButton.setOnAction(e -> showBlueskyLoginForm());
        blueskyButton.setAlignment(Pos.CENTER_LEFT);
        blueskyButton.setContentDisplay(ContentDisplay.LEFT);

        // Mastodon Button with Image
        Button mastodonButton = new Button("Mastodon", mastodonView);
        mastodonButton.setGraphic(mastodonView);
        styleBigButton(mastodonButton);
        mastodonButton.setOnAction(e -> showMastodonLoginForm());
        mastodonButton.setAlignment(Pos.CENTER_RIGHT);
        mastodonButton.setContentDisplay(ContentDisplay.LEFT);

        HBox buttonBox = new HBox(20, blueskyButton, mastodonButton);
        buttonBox.setAlignment(Pos.CENTER);

        VBox selector = new VBox(40, titleLabel, buttonBox);
        selector.setAlignment(Pos.CENTER);
        loginFormContainer.getChildren().addAll(selector);
        root.setCenter(loginFormContainer);
    }


    public void showBlueskyLoginForm() {
        loginFormContainer.getChildren().clear();

        Label header = new Label("Log in to Bluesky");
        header.setStyle("-fx-font-size: 20px; -fx-font-weight: bold; -fx-text-fill: #005fa3; -fx-font-family: 'SF Pro Display Bold';");

        Button openBrowserButton = new Button("Open Bluesky Login in Browser");
        styleButton(openBrowserButton);

        Button backButton = new Button("← Back to Platforms");
        styleButton(backButton);
        backButton.setOnAction(e -> showPlatformSelector());

        openBrowserButton.setOnAction(e -> {
            try {
                String codeVerifier = PkceUtil.generateCodeVerifier();
                String codeChallenge = PkceUtil.generateCodeChallenge(codeVerifier);

                final String parUrl = "https://bsky.social/oauth/par";
                String clientId = "https://grjimenez.github.io/bluesky-oauth-client/client-metadata.json";
                String redirectUri = "http://127.0.0.1:8080/callback";
                String state = "random-state-value"; // Generate a random state value for CSRF protection

                // Build the request body
                String parBody = String.format(
                    "client_id=%s&redirect_uri=%s&response_type=code&scope=atproto&state=%s&code_challenge=%s&code_challenge_method=S256",
                    clientId, redirectUri, state, codeChallenge
                );

                HttpClient client = HttpClient.newHttpClient();

                // First attempt
                String dpop1 = DPoPUtil.buildDPoP("POST", parUrl, null);
                HttpRequest firstRequest = HttpRequest.newBuilder()
                        .uri(URI.create(parUrl))
                        .header("Content-Type", "application/x-www-form-urlencoded")
                        .header("DPoP", dpop1)
                        .POST(HttpRequest.BodyPublishers.ofString(parBody))
                        .build();

                client.sendAsync(firstRequest, HttpResponse.BodyHandlers.ofString())
                        .thenCompose(resp -> {
                            System.out.println("[PAR#1] " + resp.statusCode());
                            System.out.println("[PAR#1] headers=" + resp.headers().map());
                            System.out.println("[PAR#1] body=" + resp.body());

                            boolean is401 = resp.statusCode() == 401;
                            boolean isNonce400 = resp.statusCode() == 400 && resp.body() != null && resp.body().contains("\"use_dpop_nonce\"");
                            if (is401 || isNonce400) {
                                String nonce = resp.headers().firstValue("DPoP-Nonce")
                                        .orElse(resp.headers().firstValue("dpop-nonce").orElse(""));
                                if (!nonce.isEmpty()) {
                                    String dpop2 = DPoPUtil.buildDPoP("POST", parUrl, nonce);
                                    HttpRequest retryRequest = HttpRequest.newBuilder()
                                            .uri(URI.create(parUrl))
                                            .header("Content-Type", "application/x-www-form-urlencoded")
                                            .header("DPoP", dpop2)
                                            .POST(HttpRequest.BodyPublishers.ofString(parBody))
                                            .build();
                                    return client.sendAsync(retryRequest, HttpResponse.BodyHandlers.ofString())
                                            .thenApply(r -> {
                                                System.out.println("[PAR#2] " + r.statusCode());
                                                System.out.println("[PAR#2] headers=" + r.headers().map());
                                                System.out.println("[PAR#2] body=" + r.body());
                                                return r;
                                            });
                                }
                            }
                            return java.util.concurrent.CompletableFuture.completedFuture(resp);
                        })
                        .thenAccept(response -> {
                            int sc = response.statusCode();
                            String body = response.body();
                            System.out.println("HTTP Status: " + sc);
                            System.out.println("HTTP Body: " + body);

                            if (sc == 200 || sc == 201) {
                                try {
                                    // Parse the request_uri from the JSON response
                                    String requestUri = BlueskyUtil.parseRequestUri(body); // Ensure parseRequestUri is robust
                                    if (requestUri == null || requestUri.isBlank()) {
                                        Platform.runLater(() -> statusLabel.setText("❌ PAR ok but missing request_uri"));
                                        return;
                                    }

                                    // Start the LocalCallbackServer
                                    server = new LocalCallbackServer(); // Initialize the server
                                    server.start();

                                    // Build the authorization URL
                                    String authorize = "https://bsky.social/oauth/authorize"
                                            + "?client_id=" + java.net.URLEncoder.encode(clientId, java.nio.charset.StandardCharsets.UTF_8)
                                            + "&request_uri=" + java.net.URLEncoder.encode(requestUri, java.nio.charset.StandardCharsets.UTF_8);

                                    // Open the browser
                                    try {
                                        getHostServices().showDocument(authorize);
                                    } catch (Exception ex) {
                                        try {
                                            java.awt.Desktop.getDesktop().browse(java.net.URI.create(authorize));
                                        } catch (Exception ignored) {
                                        }
                                    }

                                    Platform.runLater(() -> statusLabel.setText("✅ Opening Bluesky login..."));

                                    // Wait for the callback
                                    LocalCallbackServer.CallbackResult cb = server.awaitAuthorizationCode(120); // Wait up to 120 seconds

                                    if (cb == null) {
                                        Platform.runLater(() -> statusLabel.setText("❌ No callback received (timeout)."));
                                        server.stop(); // Stop the server after timeout
                                        return;
                                    }

                                    // Verify the state
                                    if (!state.equals(cb.state())) {
                                        Platform.runLater(() -> statusLabel.setText("❌ State mismatch; aborting."));
                                        server.stop(); // Stop the server after state mismatch
                                        return;
                                    }

                                    // Exchange the code for tokens
                                    BlueskyUtil.exchangeCodeForTokens(cb.code(), codeVerifier, clientId, redirectUri, new BlueskyUtil.BlueskyCallback() {
                                        @Override
                                        public void onSuccess(BlueskyUtil.TokenSet tokenSet) {
                                            System.out.println("[BLSKY] tokenSet.accessToken present? " + (tokenSet != null && tokenSet.accessToken != null && !tokenSet.accessToken.isBlank()));
                                            String idToken = getIdTokenFromTokenSet(tokenSet);
                                            System.out.println("[BLSKY] tokenSet.id_token present? " + (idToken != null && !idToken.isBlank()));
                                            // declare DID holder so it's available for later logic
                                            String didFromIdToken = "";
                                            if (idToken != null && !idToken.isBlank()) {
                                                try {
                                                    String[] parts = idToken.split("\\.");
                                                    if (parts.length == 3) {
                                                        String payload = new String(java.util.Base64.getUrlDecoder().decode(parts[1]), java.nio.charset.StandardCharsets.UTF_8);
                                                        System.out.println("[BLSKY] id_token payload (trim): " + payload.substring(0, Math.min(500, payload.length())));
                                                    } else {
                                                        System.out.println("[BLSKY][WARN] id_token format unexpected");
                                                    }
                                                } catch (Exception ex) {
                                                    System.out.println("[BLSKY][WARN] id_token decode failed: " + ex);
                                                }
                                            }
                                            

                                            // try decode id_token to get sub (DID)
                                            try {
                                                if (idToken != null && !idToken.isBlank()) {
                                                    String[] parts = idToken.split("\\.");
                                                    if (parts.length == 3) {
                                                        String payloadJson = new String(java.util.Base64.getUrlDecoder().decode(parts[1]), java.nio.charset.StandardCharsets.UTF_8);
                                                        org.json.JSONObject claims = new org.json.JSONObject(payloadJson);
                                                        didFromIdToken = claims.optString("sub", "");
                                                        System.out.println("[BLSKY] id_token sub (DID)=" + didFromIdToken);
                                                    } else {
                                                        System.out.println("[BLSKY][WARN] id_token format unexpected");
                                                    }
                                                } else {
                                                    System.out.println("[BLSKY][WARN] id_token missing (did you request 'openid' scope?)");
                                                }
                                            } catch (Exception ex) {
                                                System.out.println("[BLSKY][ERR] id_token decode failed: " + ex);
                                            }

                                            // choose actor to query: prefer DID, fallback to any already-known blueskyAcct
                                            // Resolve a seed and canonical handle asynchronously before creating HomePage
                                            String seed = nz(blueskyAcct, nz(didFromIdToken, "bluesky"));
                                            System.out.println("[BLSKY] seed for handle resolution = " + seed);
                                           resolveBlueskyHandle(seed, tokenSet.accessToken).thenAccept(resolved -> {
                                                String finalHandle = stripBskySuffix(nz(resolved, seed));
                                                System.out.println("[BLSKY] final handle = " + finalHandle);
                                                
                                                // Persist Bluesky token/handle to this MainPage instance so other flows see it
                                                MainPage.this.blueskyAccessToken = tokenSet.accessToken;
                                                MainPage.this.blueskyAcct = finalHandle;
                                                System.out.println("[STATE] Saved Bluesky (OAuth): token? " + (MainPage.this.blueskyAccessToken != null) + " acct=" + MainPage.this.blueskyAcct);

                                                Platform.runLater(() -> {
                                                    statusLabel.setText("✅ Bluesky authorized");
                                                    System.out.println("[BLSKY] Constructing HomePage with acct=" + finalHandle);
                                                   try {
                                                       currentHomePage = new HomePage(
                                                           "bluesky",
                                                          MainPage.this.blueskyAccessToken,
                                                           mastodonAccessToken,
                                                           mastodonInstance,
                                                           MainPage.this.blueskyAcct,
                                                           mastodonAcct,
                                                           MainPage.this::showPlatformSelector,
                                                           MainPage.this::showBlueskyLoginForm,
                                                           MainPage.this::showMastodonLoginForm
                                                       );                                                     root.setCenter(currentHomePage);
                                                      System.out.println("[STATE] HomePage setCenter OK");
                                                   } catch (Throwable t) {
                                                       System.out.println("[STATE][ERR] HomePage creation failed: " + t);
                                                       t.printStackTrace();
                                                       statusLabel.setText("❌ Error opening HomePage: " + (t.getMessage() == null ? t.getClass().getSimpleName() : t.getMessage()));
                                                   }
                                                   // stop callback server if still running (avoid lingering listener)
                                                   try { if (server != null) server.stop(); } catch (Exception ignored) {}
                                                    currentHomePage = new HomePage(
                                                        "bluesky",
                                                        MainPage.this.blueskyAccessToken,
                                                        mastodonAccessToken,
                                                        mastodonInstance,
                                                        MainPage.this.blueskyAcct,
                                                        mastodonAcct,
                                                        MainPage.this::showPlatformSelector,
                                                        MainPage.this::showBlueskyLoginForm,
                                                        MainPage.this::showMastodonLoginForm
                                                    );
                                                    root.setCenter(currentHomePage);
                                                });
                                            });
                                        }

                                        @Override
                                        public void onError(String errorMessage) {
                                            Platform.runLater(() -> statusLabel.setText("❌ Token exchange failed: " + errorMessage));
                                            server.stop(); // Stop the server after token exchange failure
                                        }
                                    });
                                } catch (Exception ex) {
                                    Platform.runLater(() -> statusLabel.setText("❌ Error parsing PAR response: " + ex.getMessage()));
                                    server.stop(); // Stop the server after parsing error
                                }
                            } else {
                                Platform.runLater(() -> statusLabel.setText("❌ PAR failed: " + body));
                            }
                        })
                        .exceptionally(ex -> {
                            Platform.runLater(() -> statusLabel.setText("❌ PAR error: " + ex.getMessage()));
                            return null;
                        });
            } catch (Exception ex) {
                statusLabel.setText("❌ Error: " + ex.getMessage());
            }
        });

        Label infoLbl = new Label("You are on the Bluesky login page. You can return to the Home page at any time.");
        infoLbl.setStyle("-fx-font-size: 12px; -fx-text-fill: #333;");

        Button returnHomeBtn = new Button("Return to Home");
        styleButton(returnHomeBtn);
        returnHomeBtn.setOnAction(ev -> {
            try {
                if (currentHomePage == null) {
                    currentHomePage = new HomePage(
                        "all",
                        blueskyAccessToken,
                        mastodonAccessToken,
                        mastodonInstance,
                        blueskyAcct,
                        mastodonAcct,
                        MainPage.this::showPlatformSelector,
                        MainPage.this::showBlueskyLoginForm,
                        MainPage.this::showMastodonLoginForm
                    );
                }
                root.setCenter(currentHomePage);
                statusLabel.setText("Returned to Home");
            } catch (Throwable t) {
                System.out.println("[NAV][ERR] Return to Home failed: " + t);
            }
        });

        VBox form = new VBox(12, header, infoLbl, openBrowserButton, backButton, returnHomeBtn);        form.setAlignment(Pos.CENTER);

        loginFormContainer.getChildren().add(form);
        root.setCenter(loginFormContainer);
    }
    private String extractNonce(String wwwAuthenticate) {
        try {
            int start = wwwAuthenticate.indexOf("nonce=\"") + 7;
            int end = wwwAuthenticate.indexOf("\"", start);
            return wwwAuthenticate.substring(start, end);
        } catch (Exception e) {
            throw new RuntimeException("Failed to extract DPoP nonce", e);
        }
    }
    
    

    public void showMastodonLoginForm() {
        loginFormContainer.getChildren().clear();

        Label header = new Label("Sign in with Mastodon");
        header.setStyle(
            "-fx-font-size: 20px; " +
            "-fx-text-fill: #005fa3; " +
            "-fx-font-family: 'SF Pro Display Bold';"
        );

        FloatingLabelField handleFieldWrapper = new FloatingLabelField("Enter your Mastodon instance (e.g. mastodon.social or fosstodon.org)");
        TextField handleField = handleFieldWrapper.getTextField();

        Button signInBtn = new Button("Sign in with Mastodon");
        styleButton(signInBtn);

        Button backButton = new Button("← Back to Platforms");
        styleButton(backButton);
        backButton.setOnAction(e -> showPlatformSelector());

        Label infoLbl = new Label("You are on the Mastodon login page. You can return to the Home page at any time.");
        infoLbl.setStyle("-fx-font-size: 12px; -fx-text-fill: #333;");

        Button returnHomeBtn = new Button("Return to Home");
        styleButton(returnHomeBtn);
        returnHomeBtn.setOnAction(ev -> {
            try {
                if (currentHomePage == null) {
                    currentHomePage = new HomePage(
                        "all",
                        blueskyAccessToken,
                        mastodonAccessToken,
                        mastodonInstance,
                        blueskyAcct,
                        mastodonAcct,
                        MainPage.this::showPlatformSelector,
                        MainPage.this::showBlueskyLoginForm,
                        MainPage.this::showMastodonLoginForm
                    );
                }
                root.setCenter(currentHomePage);
                statusLabel.setText("Returned to Home");
            } catch (Throwable t) {
                System.out.println("[NAV][ERR] Return to Home failed: " + t);
            }
        });

        VBox v = new VBox(12, header, infoLbl, handleFieldWrapper, signInBtn, backButton, returnHomeBtn);        v.setAlignment(Pos.CENTER);

        signInBtn.setOnAction(e -> {
            String input = handleField.getText();
            if (input == null || input.isBlank()) {
                statusLabel.setText("❌ Enter a Mastodon handle or instance.");
                return;
            }
            signInBtn.setDisable(true);
            statusLabel.setText("⏳ Starting Mastodon login...");
            // ensure statusLabel is visible at bottom
            root.setBottom(statusLabel);

            auth.mastodon.MastodonAuth.startLoginWithHandle(input,
                session -> { // onSuccess
                    Platform.runLater(() -> {
                        // persist mastodon session into instance fields
                        MainPage.this.mastodonAccessToken = session.accessToken;
                        MainPage.this.mastodonInstance = session.instance;
                        MainPage.this.mastodonAcct = session.account.acct != null ? session.account.acct : session.account.username;
                        MainPage.this.mastodonDisplayName = (session.account.displayName != null && !session.account.displayName.isBlank())
                                ? session.account.displayName
                                : session.account.username;

                        // UI feedback
                        String acctShown = MainPage.this.mastodonAcct.startsWith("@") ? MainPage.this.mastodonAcct : "@" + MainPage.this.mastodonAcct;
                        String masked = maskToken(MainPage.this.mastodonAccessToken);
                        String msg = String.format("✅ Logged in as %s (%s) on %s", MainPage.this.mastodonDisplayName, acctShown, MainPage.this.mastodonInstance);
                        statusLabel.setText(msg);
                        signInBtn.setDisable(false);

                        System.out.println("[mastodon] account id=" + session.account.id
                                + " username=" + session.account.username
                                + " acct=" + session.account.acct
                                + " displayName=" + session.account.displayName
                                + " instance=" + session.instance);

                        Alert a = new Alert(Alert.AlertType.INFORMATION);
                        a.setTitle("Mastodon Login");
                        a.setHeaderText("Login successful");
                        a.setContentText("Logged in as " + MainPage.this.mastodonDisplayName + " (" + acctShown + ") on " + session.instance);
                        a.showAndWait();

                        // DO NOT clear Bluesky here; preserve current blueskyAccessToken/blueskyAcct
                        System.out.println("[STATE] After Mastodon login: KEEPING Bluesky -> token? " 
                            + (MainPage.this.blueskyAccessToken != null) + " handle=" + MainPage.this.blueskyAcct);

                        // If HomePage already exists, update it; otherwise create it passing the preserved Bluesky values.
                        if (currentHomePage != null) {
                            // Try to call updateAccounts(...) on HomePage if implemented
                            try {
                                java.lang.reflect.Method m = currentHomePage.getClass()
                                        .getMethod("updateAccounts", String.class, String.class, String.class, String.class, String.class);
                                m.invoke(currentHomePage,
                                        MainPage.this.blueskyAccessToken,
                                        MainPage.this.mastodonAccessToken,
                                        MainPage.this.mastodonInstance,
                                        MainPage.this.blueskyAcct,
                                        MainPage.this.mastodonAcct);
                                // bring HomePage to front in case login form was showing
                                root.setCenter(currentHomePage);
                                System.out.println("[STATE] Updated HomePage and navigated to it.");
                            } catch (NoSuchMethodException nsme) {
                                // Fallback: try setMastodonHandle setter if available
                                try {
                                    java.lang.reflect.Method m2 = currentHomePage.getClass().getMethod("setMastodonHandle", String.class);
                                    m2.invoke(currentHomePage, MainPage.this.mastodonAcct);
                                    root.setCenter(currentHomePage);
                                    System.out.println("[STATE] Invoked setMastodonHandle and navigated to HomePage.");
                                } catch (Exception ignored) {
                                    System.out.println("[STATE][ERR] fallback setMastodonHandle failed: " + ignored);
                                }
                            } catch (Exception ex) {
                                System.out.println("[STATE][ERR] failed to call updateAccounts: " + ex);
                            }
                        } else {
                            // create HomePage but pass the current blueskyAccessToken (do not pass null to intentionally drop it)
                            currentHomePage = new HomePage(
                                    "mastodon",
                                    MainPage.this.blueskyAccessToken,
                                    MainPage.this.mastodonAccessToken,
                                    MainPage.this.mastodonInstance,
                                    MainPage.this.blueskyAcct,
                                    MainPage.this.mastodonAcct,
                                    MainPage.this::showPlatformSelector,
                                    MainPage.this::showBlueskyLoginForm,
                                    MainPage.this::showMastodonLoginForm
                            );
                            root.setCenter(currentHomePage);
                        }
                        root.setCenter(currentHomePage);

                        System.out.println("[STATE] Final after Mastodon success -> BSKY: token? "
                                + (MainPage.this.blueskyAccessToken != null) + " acct=" + MainPage.this.blueskyAcct
                                + " | MASTO: token? " + (MainPage.this.mastodonAccessToken != null) + " acct=" + MainPage.this.mastodonAcct);
                    });
                },
                err -> { // onError
                    Platform.runLater(() -> {
                        statusLabel.setText("❌ Mastodon login failed: " + err);
                        signInBtn.setDisable(false);
                        Alert a = new Alert(Alert.AlertType.ERROR);
                        a.setTitle("Mastodon Login Error");
                        a.setHeaderText("Login failed");
                        a.setContentText(err);
                        a.showAndWait();
                    });
                }
            );
        });

        loginFormContainer.getChildren().add(v);
        root.setCenter(loginFormContainer);
    }
    public static void styleTextField(TextField field) {
        field.setPrefWidth(300);
        field.setStyle("""
            -fx-background-color: white;
            -fx-border-color: #007acc;
            -fx-border-width: 2;
            -fx-border-radius: 5;
            -fx-background-radius: 5;
            -fx-padding: 8;
        """);
    }
    private static String maskToken(String t) {
        if (t == null || t.isBlank()) return "(no-token)";
        if (t.length() <= 12) return t;
        return t.substring(0, 8) + "…" + t.substring(t.length() - 4);
    }

    private static String getIdTokenFromTokenSet(searchapp.BlueskyUtil.TokenSet tokenSet) {
        if (tokenSet == null) return "";
        try {
            Class<?> cls = tokenSet.getClass();

            // try common getter names first
            try {
                java.lang.reflect.Method m = cls.getMethod("getIdToken");
                Object v = m.invoke(tokenSet);
                if (v instanceof String) return (String) v;
            } catch (NoSuchMethodException ignored) {}

            try {
                java.lang.reflect.Method m2 = cls.getMethod("getId_token");
                Object v2 = m2.invoke(tokenSet);
                if (v2 instanceof String) return (String) v2;
            } catch (NoSuchMethodException ignored) {}

            // try common field names
            try {
                java.lang.reflect.Field f = cls.getField("idToken");
                Object v = f.get(tokenSet);
                if (v instanceof String) return (String) v;
            } catch (NoSuchFieldException ignored) {}

            try {
                java.lang.reflect.Field f2 = cls.getField("id_token");
                Object v2 = f2.get(tokenSet);
                if (v2 instanceof String) return (String) v2;
            } catch (NoSuchFieldException ignored) {}
        } catch (Exception e) {
            System.out.println("[BLSKY][WARN] unable to extract id_token reflectively: " + e);
        }
        return "";
    }
    public static void styleButton(Button btn) {
        btn.setStyle("""
            -fx-background-color: #ffffffff;
            -fx-text-fill: #005fa3;
            -fx-font-weight: bold;
            -fx-font-family: 'SF Pro Display Light';
            -fx-padding: 10 20;
            -fx-border-radius: 5;
            -fx-background-radius: 5;
        """);
        btn.setOnMouseEntered(e -> btn.setStyle(btn.getStyle() + " -fx-background-color: #c7e7fdff;"));
        btn.setOnMouseExited(e -> btn.setStyle(btn.getStyle().replace("-fx-background-color: #c7e7fdff;", "-fx-background-color: #ffffff;")));
    }

    public static void styleBigButton(Button btn) {
        btn.setStyle("""
            -fx-background-color: #ffffffff;
            -fx-text-fill: #005fa3;
            -fx-font-size: 18px;
            -fx-font-weight: bold;
            -fx-font-family: 'SF Pro Display';
            -fx-padding: 15 40;
            -fx-border-radius: 10;
            -fx-background-radius: 10;
            -fx-alignment: center-left;
""");
        btn.setOnMouseEntered(e -> btn.setStyle(btn.getStyle() + " -fx-background-color: #c7e7fdff;"));
        btn.setOnMouseExited(e -> btn.setStyle(btn.getStyle().replace("-fx-background-color: #c7e7fdff;", "-fx-background-color: #ffffff;")));
    }

    public void handleBlueskyLogin(String username, String appPassword) {
        if (username.isEmpty() || appPassword.isEmpty()) {
            statusLabel.setText("❌ Bluesky: Please fill in all fields.");
            return;
        }

        // Bluesky API endpoint
        String apiUrl = "https://bsky.social/xrpc/com.atproto.server.createSession";
        String jsonBody = String.format("{\"identifier\":\"%s\",\"password\":\"%s\"}", username, appPassword);

        HttpClient client = HttpClient.newHttpClient();
        HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create(apiUrl))
                .header("Content-Type", "application/json")
                .POST(HttpRequest.BodyPublishers.ofString(jsonBody))
                .build();

        client.sendAsync(request, HttpResponse.BodyHandlers.ofString())
                .thenApply(HttpResponse::body)
                .thenAccept(responseBody -> {
                    try {
                        System.out.println("[BLSKY] createSession OK");
                        System.out.println("[BLSKY] createSession body (trim): " + responseBody.substring(0, Math.min(500, responseBody.length())));
                        JSONObject obj = new JSONObject(responseBody);

                        blueskyAccessToken = obj.getString("accessJwt");
                        String did = obj.optString("did", "");
                        String handle = obj.optString("handle", "");
                        System.out.println("[BLSKY] tokens parsed. did=" + did + " handle=" + handle);

                        // Use canonical handle from server; fallback to input username if missing
                        blueskyAcct = (!handle.isBlank()) ? handle : username;
                        blueskyAcct = stripBskySuffix(blueskyAcct);
                        System.out.println("[BLSKY] resolved acct=" + blueskyAcct);

                        Platform.runLater(() -> {
                            statusLabel.setText("✅ Bluesky login successful!");
                            System.out.println("[BLSKY] Constructing HomePage with acct=" + blueskyAcct);
                            currentHomePage = new HomePage(
                                "bluesky",
                                blueskyAccessToken,
                                mastodonAccessToken,
                                mastodonInstance,
                                blueskyAcct,
                                mastodonAcct,
                                MainPage.this::showPlatformSelector,
                                MainPage.this::showBlueskyLoginForm,
                                MainPage.this::showMastodonLoginForm
                            );
                            root.setCenter(currentHomePage);
                        });
                    } catch (Exception ex) {
                        System.out.println("[BLSKY][ERR] createSession parse failed: " + ex);
                        Platform.runLater(() -> statusLabel.setText("❌ Bluesky parse error: " + ex.getMessage()));
                    }
                })
                .exceptionally(e -> {
                    System.out.println("[BLSKY][ERR] createSession exception: " + e);
                    Platform.runLater(() -> statusLabel.setText("❌ Bluesky login error: " + e.getMessage()));
                    return null;
                });
    }
        public void handleMastodonOAuthCode(String clientId, String clientSecret, String code) {
            if (clientId.isEmpty() || clientSecret.isEmpty() || code.isEmpty()) {
                statusLabel.setText("❌ Please fill in all fields.");
                return;
            }
            String tokenUrl = "https://mastodon.social/oauth/token";
            String redirectUri = "urn:ietf:wg:oauth:2.0:oob";
            String body = String.format(
                "grant_type=authorization_code&client_id=%s&client_secret=%s&redirect_uri=%s&code=%s",
                clientId, clientSecret, redirectUri, code
            );

            HttpClient client = HttpClient.newHttpClient();
            HttpRequest request = HttpRequest.newBuilder()
                    .uri(URI.create(tokenUrl))
                    .header("Content-Type", "application/x-www-form-urlencoded")
                    .POST(HttpRequest.BodyPublishers.ofString(body))
                    .build();

            client.sendAsync(request, HttpResponse.BodyHandlers.ofString())
                    .thenApply(HttpResponse::body)
                    .thenAccept(responseBody -> {
                        if (responseBody.contains("access_token")) {
                            Platform.runLater(() -> {
                                statusLabel.setText("✅ Mastodon login successful!");
                                currentHomePage = new HomePage(
                                    "mastodon", 
                                    blueskyAccessToken,
                                    mastodonAccessToken,
                                    mastodonInstance,
                                    blueskyAcct,
                                    mastodonAcct,
                                    MainPage.this::showPlatformSelector, 
                                    MainPage.this::showBlueskyLoginForm, 
                                    MainPage.this::showMastodonLoginForm
                                );
                                root.setCenter(currentHomePage); 
                            });
                        } else {
                            Platform.runLater(() -> statusLabel.setText("❌ Mastodon login failed: " + responseBody));
                        }
                    })
                    .exceptionally(e -> {
                        Platform.runLater(() -> statusLabel.setText("❌ Mastodon login error: " + e.getMessage()));
                        return null;
                    });
            }

    public void showLoginPage() {
        root.setCenter(loginFormContainer);
    }

    // MAKE MASTODON LOGIN FIELD LIKE GOOGLE    
    private static class FloatingLabelField extends StackPane {
        private final Label floatingLabel;
        private final TextField textField;
        private boolean isFloating = false;

        public FloatingLabelField(String labelText) {
            setPrefWidth(300);

            textField = new TextField();
            textField.setPromptText("");
            textField.setFont(Font.font("SF Pro Display Light", 14));
            textField.setPrefHeight(40);
            textField.setStyle(
                "-fx-background-color: #f0f8ff;" +
                "-fx-border-color: #9aa0a6;" +
                "-fx-border-radius: 6;" +
                "-fx-background-radius: 6;" +
                "-fx-padding: 15 8 8 8;"
            );

            floatingLabel = new Label(labelText);
            floatingLabel.setTextFill(Color.web("#80868b")); 
            floatingLabel.setFont(Font.font("SF Pro Display Light", 14));
            floatingLabel.setTranslateY(12); // Position in middle of field initially
            floatingLabel.setTranslateX(10);
            floatingLabel.setPadding(new Insets(0, 5, 0, 5));
            floatingLabel.setStyle("-fx-background-color: #f0f8ff;"); // Match parent background
            floatingLabel.setMouseTransparent(true);

            getChildren().addAll(textField, floatingLabel);

            // Animate label on focus or typing
            textField.focusedProperty().addListener((obs, oldVal, newVal) -> {
                if (newVal) floatLabelUp();
                else if (textField.getText().isEmpty()) floatLabelDown();
            });

            textField.textProperty().addListener((obs, oldVal, newVal) -> {
                if (!textField.getText().isEmpty()) floatLabelUp();
            });

            textField.setOnKeyPressed(event -> {
                if (event.getCode() == javafx.scene.input.KeyCode.ENTER) {
                    // Find the parent VBox that contains the sign in button
                    Node parent = getParent();
                    while (parent != null && !(parent instanceof VBox)) {
                        parent = parent.getParent();
                    }
                    
                    if (parent instanceof VBox) {
                        // Find the sign in button in the VBox children
                        VBox vbox = (VBox) parent;
                        vbox.getChildren().stream()
                            .filter(node -> node instanceof Button)
                            .map(node -> (Button) node)
                            .filter(button -> button.getText().contains("Sign in"))
                            .findFirst()
                            .ifPresent(Button::fire);
                    }
                }
            });
        }

        private void floatLabelUp() {
            if (isFloating) return;
            isFloating = true;

            TranslateTransition moveUp = new TranslateTransition(Duration.millis(150), floatingLabel);
            moveUp.setToY(-22); // Move to top border position

            ScaleTransition shrink = new ScaleTransition(Duration.millis(150), floatingLabel);
            shrink.setToX(0.85);
            shrink.setToY(0.85);

            floatingLabel.setTextFill(Color.web("#005fa3"));

            moveUp.play();
            shrink.play();
        }

        private void floatLabelDown() {
            if (!isFloating) return;
            isFloating = false;

            TranslateTransition moveDown = new TranslateTransition(Duration.millis(150), floatingLabel);
            moveDown.setToY(0); // Move back to middle position

            ScaleTransition grow = new ScaleTransition(Duration.millis(150), floatingLabel);
            grow.setToX(1);
            grow.setToY(1);

            floatingLabel.setTextFill(Color.web("#80868b"));

            moveDown.play();
            grow.play();
        }

        public TextField getTextField() {
            return textField;
        }
    }

    public static void main(String[] args) {
        launch(args);
    }
}