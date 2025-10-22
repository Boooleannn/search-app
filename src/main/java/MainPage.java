import javafx.application.Application;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.Scene;
import javafx.scene.control.*;
import javafx.scene.layout.*;
import javafx.scene.paint.Color;
import javafx.scene.image.Image;
import javafx.scene.image.ImageView;
import javafx.stage.Stage;
import org.json.JSONObject;
import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import javafx.application.Platform;
import javafx.concurrent.Task;
import searchapp.PkceUtil;
import searchapp.DPoPUtil;
import searchapp.LocalCallbackServer;
import searchapp.BlueskyUtil;
import javafx.scene.Node;

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
    private HomePage currentHomePage;

    @Override
    public void start(Stage stage) {
        stage.setTitle("Social Search: Bluesky + Mastodon");

        statusLabel = new Label("Select a platform to log in.");
        statusLabel.setStyle("-fx-text-fill: #007acc; -fx-font-weight: bold;");

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
        titleLabel.setStyle("-fx-font-size: 24px; -fx-font-weight: bold; -fx-text-fill: #005fa3;");
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
        header.setStyle("-fx-font-size: 20px; -fx-font-weight: bold; -fx-text-fill: #005fa3;");

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
                                            String accessToken = tokenSet.accessToken; // Extract the access token
                                            Platform.runLater(() -> {
                                                statusLabel.setText("✅ Login successful!");
                                                
                                                currentHomePage = new HomePage(
                                                    "bluesky", 
                                                    blueskyAccessToken,
                                                    mastodonAccessToken,
                                                    MainPage.this::showPlatformSelector, 
                                                    MainPage.this::showBlueskyLoginForm, 
                                                    MainPage.this::showMastodonLoginForm
                                                    );
                                                root.setCenter(currentHomePage);
                                            });
                                            server.stop(); // Stop the server after successful token exchange
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

        VBox form = new VBox(15, header, openBrowserButton, backButton);
        form.setAlignment(Pos.CENTER);

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
        header.setStyle("-fx-font-size: 20px; -fx-font-weight: bold; -fx-text-fill: #005fa3;");

        TextField handleField = new TextField();
        handleField.setPromptText("e.g. @user@mastodon.social or mastodon.social");
        handleField.setPrefWidth(400);

        Button signInBtn = new Button("Sign in with Mastodon");
        styleButton(signInBtn);

        Button backButton = new Button("← Back to Platforms");
        styleButton(backButton);
        backButton.setOnAction(e -> showPlatformSelector());

        VBox v = new VBox(12, header, handleField, signInBtn, backButton);
        v.setAlignment(Pos.CENTER);

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
                        // store token + account info for later use
                        mastodonAccessToken = session.accessToken;
                        mastodonInstance = session.instance;
                        mastodonAcct = session.account.acct != null ? session.account.acct : session.account.username;
                        mastodonDisplayName = (session.account.displayName != null && !session.account.displayName.isBlank())
                                ? session.account.displayName
                               : session.account.username;

                        // show a masked token and the user info so we can confirm login succeeded
                        String acctShown = mastodonAcct.startsWith("@") ? mastodonAcct : "@" + mastodonAcct;
                        String masked = maskToken(mastodonAccessToken);
                        statusLabel.setText(String.format("✅ Logged in: %s (%s) on %s — token: %s",
                                mastodonDisplayName, acctShown, mastodonInstance, masked));
                        // choose display name (prefer displayName, fallback to username)
                        String display = (session.account.displayName != null && !session.account.displayName.isBlank())
                                ? session.account.displayName
                                : session.account.username;

                        // ensure acct is shown like @user or @user@host
                        String acct = session.account.acct != null ? session.account.acct : session.account.username;
                        if (!acct.startsWith("@")) acct = "@" + acct;

                        String msg = String.format("✅ Logged in as %s (%s) on %s", display, acct, session.instance);
                        statusLabel.setText(msg);

                        // re-enable UI
                        signInBtn.setDisable(false);

                        // non-sensitive debug (do NOT print tokens)
                        System.out.println("[mastodon] account id=" + session.account.id
                                + " username=" + session.account.username
                                + " acct=" + session.account.acct
                                + " displayName=" + session.account.displayName
                                + " instance=" + session.instance);

                        Alert a = new Alert(Alert.AlertType.INFORMATION);
                        a.setTitle("Mastodon Login");
                        a.setHeaderText("Login successful");
                        a.setContentText("Logged in as " + display + " (" + acct + ") on " + session.instance);
                        a.showAndWait();

                        currentHomePage = new HomePage(
                                "mastodon", 
                                blueskyAccessToken,
                                mastodonAccessToken,
                                MainPage.this::showPlatformSelector, 
                                MainPage.this::showBlueskyLoginForm, 
                                MainPage.this::showMastodonLoginForm
                                );
                        root.setCenter(currentHomePage);
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
    public static void styleButton(Button btn) {
        btn.setStyle("""
            -fx-background-color: #ffffffff;
            -fx-text-fill: #005fa3;
            -fx-font-weight: bold;
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
                    // Check for success (look for "accessJwt" in response)
                    if (responseBody.contains("accessJwt")) {
                        Platform.runLater(() -> {
                            statusLabel.setText("✅ Bluesky login successful!");
                                currentHomePage = new HomePage(
                                    "bluesky", 
                                    blueskyAccessToken,
                                    mastodonAccessToken,
                                    MainPage.this::showPlatformSelector, 
                                    MainPage.this::showBlueskyLoginForm, 
                                    MainPage.this::showMastodonLoginForm
                                );
                                root.setCenter(currentHomePage); 
                        });
                    } else {
                        Platform.runLater(() -> statusLabel.setText("❌ Bluesky login failed: " + responseBody));
                    }
                })
                .exceptionally(e -> {
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

    public static void main(String[] args) {
        launch(args);
    }
}