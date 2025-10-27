import javafx.application.Platform;
import javafx.concurrent.Task;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.Node;
import javafx.scene.control.*;
import javafx.scene.image.Image;
import javafx.scene.image.ImageView;
import javafx.scene.layout.*;
import java.net.http.*;
import java.net.URI;
import app.ui.PostCards;

public class HomePage extends BorderPane {
    private VBox sidebarContent;
    private boolean sidebarExpanded = true;
    private VBox resultsArea;

    public HomePage(String platform,
                    String blueskyAccessToken,
                    String mastodonAccessToken,
                    String mastodonInstance,
                    Runnable onGoBack,
                    Runnable onBlueskyLogin,
                    Runnable onMastodonLogin){
        // === Top: Search Bar ===
    HBox searchBar = createSearchBar(platform, onGoBack, onBlueskyLogin, blueskyAccessToken, mastodonAccessToken, mastodonInstance);
        this.setTop(searchBar);

        // === Center: Tabs + Results Area ===
        HBox tabs = new HBox(40);
        tabs.setAlignment(Pos.CENTER_LEFT);
        tabs.setPadding(new Insets(0, 0, 0, 20));
        tabs.setStyle("-fx-background-color: #a3a3ff; -fx-border-color: black; -fx-border-width: 1 0 1 0;");
        String[] tabNames = {"All", "Profiles", "Hashtags", "Posts"};
        for (String name : tabNames) {
            Label tab = new Label(name);
            tab.setStyle("-fx-font-size: 18; -fx-font-weight: bold;");
            tabs.getChildren().add(tab);
        }

        resultsArea = new VBox();
        resultsArea.setPadding(new Insets(20));
        resultsArea.setFillWidth(true);
        resultsArea.setAlignment(Pos.CENTER);
        
        ScrollPane resultsScroll = new ScrollPane(resultsArea);
        resultsScroll.setFitToWidth(true);
        resultsScroll.setFitToHeight(true);
        resultsScroll.setHbarPolicy(ScrollPane.ScrollBarPolicy.NEVER);
        resultsScroll.setVbarPolicy(ScrollPane.ScrollBarPolicy.AS_NEEDED);
        resultsScroll.setPannable(true);
        resultsScroll.setStyle("-fx-background: transparent; -fx-background-color: transparent;");

        // Make scroll pane expand to fill available space
        VBox.setVgrow(resultsScroll, Priority.ALWAYS);
        HBox.setHgrow(resultsScroll, Priority.ALWAYS);

        VBox centerBox = new VBox(tabs, resultsScroll);
        VBox.setVgrow(resultsScroll, Priority.ALWAYS);
        centerBox.setStyle("-fx-background-color: #f0f8ff;");
        this.setCenter(centerBox);

        // === Right: Sidebar ===
        VBox sidebarWrapper = createSidebar(onBlueskyLogin, onMastodonLogin);
        this.setRight(sidebarWrapper);

        this.setStyle("-fx-background-color: #e6f2ff;");
    }

    private HBox createSearchBar(String platform, Runnable onGoBack, Runnable onBlueskyLogin, String blueskyAccessToken, String mastodonAccessToken, String mastodonInstance) {
        HBox searchBar = new HBox(10);
        searchBar.setPadding(new Insets(20));
        searchBar.setAlignment(Pos.CENTER);
        searchBar.setFillHeight(true);
        
        // Make search bar fill width of window
        searchBar.prefWidthProperty().bind(this.widthProperty());

        String iconPath = platform != null && platform.equalsIgnoreCase("mastodon") ?
            "/images/mastodon.svg.png" : "/images/Bluesky_Logo.png";
        ImageView logo = null;
        try {
            logo = new ImageView(new Image(getClass().getResourceAsStream(iconPath)));
            logo.setFitHeight(32);
            logo.setFitWidth(32);
        } catch (Exception ignored) {}

        TextField searchField = new TextField();
        searchField.setPromptText("Search for posts, accounts, trends");
        // Make search field expand to fill available space
        HBox.setHgrow(searchField, Priority.ALWAYS);
        searchField.setStyle("-fx-background-radius: 20; -fx-font-size: 16;");

        CheckBox cbBluesky = new CheckBox("Bluesky");
        CheckBox cbMastodon = new CheckBox("Mastodon");
        cbBluesky.setSelected(true);
        cbMastodon.setSelected(true);

        // Create a HBox for buttons to keep them together
        HBox buttonGroup = new HBox(10);
        buttonGroup.setAlignment(Pos.CENTER_RIGHT);

        Button searchBtn = new Button("Search");
        Button goBackButton = new Button("Go Back");
        goBackButton.setStyle("-fx-background-radius: 20;");
        goBackButton.setOnAction(e -> {
            if (onGoBack != null) onGoBack.run();
        });

        buttonGroup.getChildren().addAll(searchBtn, goBackButton);

        Button toggleSidebarBtn = new Button("☰");
        toggleSidebarBtn.setStyle("-fx-font-size: 24; -fx-background-color: transparent; -fx-padding: 0; -fx-border-width: 0;");
        toggleSidebarBtn.setOnAction(e -> {
            sidebarExpanded = !sidebarExpanded;
            if (sidebarContent != null) {
                sidebarContent.setVisible(sidebarExpanded);
                sidebarContent.setManaged(sidebarExpanded);
            }
        });

        // Create VBox for checkboxes
        VBox checkBoxGroup = new VBox(5, cbBluesky, cbMastodon);
        checkBoxGroup.setAlignment(Pos.CENTER_LEFT);

        // Main content layout
        HBox leftContent = new HBox(10);
        if (logo != null) leftContent.getChildren().add(logo);
        leftContent.getChildren().addAll(searchField, checkBoxGroup, buttonGroup, toggleSidebarBtn);
        leftContent.setAlignment(Pos.CENTER_LEFT);
        HBox.setHgrow(leftContent, Priority.ALWAYS);

        searchBar.getChildren().add(leftContent);

        // Add search button action
        searchBtn.setOnAction(e -> {
            String q = searchField.getText().trim();
            if (q.isEmpty()) {
                showAlert("Please enter a search query.");
                return;
            }
            if (!cbBluesky.isSelected() && !cbMastodon.isSelected()) {
                showAlert("Please select at least one platform.");
                return;
            }

            showSearchResults(new Label("⏳ Searching..."));

            Task<Node> task = new Task<>() {
                @Override
                protected Node call() throws Exception {

                    VBox box = new VBox(10);
                    box.setSpacing(15);
                    if (cbBluesky.isSelected()) {
                        if (blueskyAccessToken == null || blueskyAccessToken.isEmpty()) {
                            box.getChildren().add(new Label("❌ Not logged into Bluesky."));
                        } else {
                            try {
                                String body = searchBlueskyRaw(q, blueskyAccessToken);
                                java.util.List<Node> cards = PostCards.buildBlueskyCardsFromBody(body);
                                if (cards.isEmpty()) {
                                    box.getChildren().add(new Label("🔵 Bluesky: No results."));
                                } else {
                                    Label blueskyHeader = new Label("🔵 Bluesky Results");
                                    blueskyHeader.setStyle("-fx-font-size: 16px; -fx-font-weight: bold;");
                                    box.getChildren().add(blueskyHeader);
                                    
                                    for (Node n : cards) {
                                        VBox.setMargin(n, new Insets(8));
                                        // Style the post card
                                        n.setStyle("-fx-background-color: white; -fx-padding: 15; -fx-background-radius: 8;");
                                        box.getChildren().add(n);
                                    }
                                }
                            } catch (Exception ex) {
                               box.getChildren().add(new Label("❌ Bluesky error: " + ex.getMessage()));
                                System.err.println("[Bluesky] search exception: " + ex.getMessage());
                                String m = ex.getMessage() == null ? "" : ex.getMessage().toLowerCase();
                                if (m.contains("bad token scope") || m.contains("unauthorized") || m.contains("invalidtoken")) {
                                    Button relogin = new Button("Re-login to Bluesky");
                                    relogin.setOnAction(evt -> {
                                        if (onBlueskyLogin != null) onBlueskyLogin.run();
                                    });
                                    box.getChildren().add(relogin);
                                }
                            }
                        }
                    }
                    
                    if (cbMastodon.isSelected()) {
                        if (mastodonAccessToken == null || mastodonAccessToken.isBlank()) {
                            box.getChildren().add(new Label("❌ Not logged into Mastodon."));
                        } else {
                            try {
                                String instanceHost = (mastodonInstance == null) ? "" : mastodonInstance;
                                String body = searchMastodonRaw(q, instanceHost, mastodonAccessToken);
                                java.util.List<Node> cards = PostCards.buildMastodonCardsFromBody(body);
                                if (cards.isEmpty()) {
                                    box.getChildren().add(new Label("🐘 Mastodon: No results."));
                                } else {
                                    Label mastodonHeader = new Label("🐘 Mastodon Results");
                                    mastodonHeader.setStyle("-fx-font-size: 16px; -fx-font-weight: bold;");
                                    box.getChildren().add(mastodonHeader);
                                    
                                    for (Node n : cards) {
                                        VBox.setMargin(n, new Insets(8));
                                        // Style the post card
                                        n.setStyle("-fx-background-color: white; -fx-padding: 15; -fx-background-radius: 8;");
                                        box.getChildren().add(n);
                                    }
                                }
                            } catch (Exception ex) {
                                box.getChildren().add(new Label("❌ Mastodon error: " + ex.getMessage()));
                                System.err.println("[Mastodon] search exception: " + ex.getMessage());
                            }
                        }
                 }
                return box;
            }
            };

            task.setOnSucceeded(event -> showSearchResults(task.getValue()));
            task.setOnFailed(event -> 
                showSearchResults(new Label("❌ Search failed: " + event.getSource().getException().getMessage()))
            );

            new Thread(task).start();
        });

        searchField.setOnAction(e -> searchBtn.fire());
        
        return searchBar;
    }
    private String searchBlueskyRaw(String query, String accessJwt) throws Exception {
        String q = java.net.URLEncoder.encode(query, java.nio.charset.StandardCharsets.UTF_8);
        String[] hosts = new String[] {
            "https://public.api.bsky.app",
            "https://api.bsky.app"
        };
        var client = java.net.http.HttpClient.newBuilder()
            .connectTimeout(java.time.Duration.ofSeconds(10))
            .build();

        for (String host : hosts) {
            String url = host + "/xrpc/app.bsky.feed.searchPosts?q=" + q + "&limit=10";
            var req = java.net.http.HttpRequest.newBuilder()
                .uri(java.net.URI.create(url))
                .GET()
                .header("User-Agent", "SearchApp/1.0")
                .header("Accept", "application/json")
                .timeout(java.time.Duration.ofSeconds(10))
                .build();
            var resp = client.send(req, java.net.http.HttpResponse.BodyHandlers.ofString());
            int code = resp.statusCode();
            if (code / 100 == 2) return resp.body();
            if (code == 403) {
                System.err.println("[Bluesky] AppView 403 on " + host + " — trying fallback");
                continue;
            }
            String shortBody = resp.body() == null ? "" :
                (resp.body().length() > 300 ? resp.body().substring(0, 300) + "…" : resp.body());
            throw new RuntimeException("Bluesky search failed: " + code + " " + shortBody);
        }

        if (accessJwt != null && !accessJwt.isBlank()) {
            String pdsHost = "https://bsky.social";
            String url = pdsHost.replaceAll("/+$", "") + "/xrpc/app.bsky.feed.searchPosts?q=" + q + "&limit=10";
            String dpopProof = null;
            try { dpopProof = searchapp.DPoPUtil.buildDPoP("GET", url, null); } catch (Exception ignored) {}

            var reqBuilder = java.net.http.HttpRequest.newBuilder()
                .uri(java.net.URI.create(url))
                .GET()
                .header("Authorization", "DPoP " + accessJwt)
                .header("DPoP", dpopProof == null ? "" : dpopProof)
                .header("User-Agent", "SearchApp/1.0")
                .header("Accept", "application/json")
                .timeout(java.time.Duration.ofSeconds(10));
            var req = reqBuilder.build();
            var resp = client.send(req, java.net.http.HttpResponse.BodyHandlers.ofString());
            if (resp.statusCode() == 401 && resp.headers().firstValue("dpop-nonce").isPresent()) {
                String nonce = resp.headers().firstValue("dpop-nonce").get();
                String proofWithNonce = searchapp.DPoPUtil.buildDPoP("GET", url, nonce);
                req = java.net.http.HttpRequest.newBuilder()
                    .uri(java.net.URI.create(url))
                    .GET()
                    .header("Authorization", "DPoP " + accessJwt)
                    .header("DPoP", proofWithNonce)
                    .header("User-Agent", "SearchApp/1.0")
                    .header("Accept", "application/json")
                    .timeout(java.time.Duration.ofSeconds(10))
                    .build();
                resp = client.send(req, java.net.http.HttpResponse.BodyHandlers.ofString());
            }
            int code = resp.statusCode();
            if (code / 100 == 2) return resp.body();
            String shortBody = resp.body() == null ? "" :
                (resp.body().length() > 300 ? resp.body().substring(0, 300) + "…" : resp.body());
            throw new RuntimeException("Bluesky (PDS) search failed: " + code + " " + shortBody);
        }
        throw new RuntimeException("Bluesky search blocked by AppView and no PDS fallback available.");
    }

    // Raw Mastodon search: returns HTTP body on 2xx
    private String searchMastodonRaw(String query, String instance, String accessToken) throws Exception {
        String inst = instance == null ? "" : instance.trim();
        inst = inst.replaceFirst("^https?://", "");
        inst = inst.replaceAll("^@", "");
        if (inst.isEmpty()) throw new IllegalArgumentException("Missing Mastodon instance host");

        String q = java.net.URLEncoder.encode(query, java.nio.charset.StandardCharsets.UTF_8);
        String url = "https://" + inst + "/api/v2/search?type=statuses&q=" + q + "&limit=10&resolve=true";

        var req = java.net.http.HttpRequest.newBuilder()
            .uri(java.net.URI.create(url))
            .GET()
            .header("Authorization", "Bearer " + accessToken)
            .header("Accept", "application/json")
            .header("User-Agent", "GRClient/1.0 (+https://grjimenez.github.io)")
            .timeout(java.time.Duration.ofSeconds(10))
            .build();

        var client = java.net.http.HttpClient.newHttpClient();
        var resp = client.send(req, java.net.http.HttpResponse.BodyHandlers.ofString());
        if (resp.statusCode() / 100 != 2) {
            String shortBody = resp.body() == null ? "" :
                (resp.body().length() > 300 ? resp.body().substring(0, 300) + "…" : resp.body());
            System.err.println("[Mastodon] search failed HTTP " + resp.statusCode() + " body=" + shortBody);
            throw new RuntimeException("Mastodon search failed: " + resp.statusCode() + " " + shortBody);
        }
        return resp.body();
    }
    private String searchBlueskyAuth(String query, String accessJwt) throws Exception {
        String q = java.net.URLEncoder.encode(query, java.nio.charset.StandardCharsets.UTF_8);

        String[] hosts = new String[] {
            "https://public.api.bsky.app", // primary unauthenticated AppView
            "https://api.bsky.app"         // alternate AppView host
        };

        var client = java.net.http.HttpClient.newBuilder()
            .connectTimeout(java.time.Duration.ofSeconds(10))
            .build();

        // Try public AppView (no auth)
        for (String host : hosts) {
            String url = host + "/xrpc/app.bsky.feed.searchPosts?q=" + q + "&limit=10";
            var req = java.net.http.HttpRequest.newBuilder()
                .uri(java.net.URI.create(url))
                .GET()
                .header("User-Agent", "SearchApp/1.0")
                .header("Accept", "application/json")
                .timeout(java.time.Duration.ofSeconds(10))
                .build();

            var resp = client.send(req, java.net.http.HttpResponse.BodyHandlers.ofString());
            int code = resp.statusCode();

            if (code == 200) {
                System.out.println("[Bluesky] AppView OK on " + host);
                return prettyPrintSearch(resp.body());
            } else if (code == 403) {
                // try next host / fallback
                System.err.println("[Bluesky] AppView 403 on " + host + " — trying fallback");
            } else {
                String shortBody = resp.body() == null ? "" :
                    (resp.body().length() > 300 ? resp.body().substring(0, 300) + "…" : resp.body());
                throw new RuntimeException("Bluesky search failed: " + code + " " + shortBody);
            }
        }

        // Fallback: try user's PDS (authenticated) if we have a token.

        if (accessJwt != null && !accessJwt.isBlank()) {
            String pdsHost = "https://bsky.social"; // best-effort fallback; ideally use actual user's PDS
            String url = pdsHost.replaceAll("/+$", "") + "/xrpc/app.bsky.feed.searchPosts?q=" + q + "&limit=10";

            String dpopProof = null;
            try { dpopProof = searchapp.DPoPUtil.buildDPoP("GET", url, null); } catch (Exception ignored) {}

            var reqBuilder = java.net.http.HttpRequest.newBuilder()
                .uri(java.net.URI.create(url))
                .GET()
                .header("Authorization", "DPoP " + accessJwt)
                .header("DPoP", dpopProof == null ? "" : dpopProof)
                .header("User-Agent", "SearchApp/1.0")
                .header("Accept", "application/json")
                .timeout(java.time.Duration.ofSeconds(10));

            var req = reqBuilder.build();
            var resp = client.send(req, java.net.http.HttpResponse.BodyHandlers.ofString());

            // handle dpop-nonce if returned (retry once)
            if (resp.statusCode() == 401 && resp.headers().firstValue("dpop-nonce").isPresent()) {
                String nonce = resp.headers().firstValue("dpop-nonce").get();
                String proofWithNonce = searchapp.DPoPUtil.buildDPoP("GET", url, nonce);

                // Rebuild request explicitly (newBuilder(HttpRequest) is not available)
                req = java.net.http.HttpRequest.newBuilder()
                    .uri(java.net.URI.create(url))
                    .GET()
                    .header("Authorization", "DPoP " + accessJwt)
                    .header("DPoP", proofWithNonce)
                    .header("User-Agent", "SearchApp/1.0")
                    .header("Accept", "application/json")
                    .timeout(java.time.Duration.ofSeconds(10))
                    .build();
                resp = client.send(req, java.net.http.HttpResponse.BodyHandlers.ofString());
            }

            int code = resp.statusCode();
            System.out.println("[Bluesky] PDS response: " + code);

            if (code == 200) return prettyPrintSearch(resp.body());

            String shortBody = resp.body() == null ? "" :
                (resp.body().length() > 300 ? resp.body().substring(0, 300) + "…" : resp.body());
            throw new RuntimeException("Bluesky (PDS) search failed: " + code + " " + shortBody);
        }

        throw new RuntimeException("Bluesky search blocked by AppView and no PDS fallback available.");
    }

    // Helper to summarize posts array safely
    private String prettyPrintSearch(String body) {
        if (body == null || body.isBlank()) return "🔵 Bluesky results:\n• Empty response.";
        org.json.JSONObject json;
        try {
            json = new org.json.JSONObject(body);
        } catch (Exception e) {
            // not a JSON object — try array
            try {
                org.json.JSONArray arr = new org.json.JSONArray(body);
                return extractPostsFromArray(arr);
            } catch (Exception ex) {
                return "🔵 Bluesky results:\n• Unparseable response.";
            }
        }

        org.json.JSONArray posts = null;
        if (json.has("posts")) posts = json.optJSONArray("posts");
        else if (json.has("data") && json.optJSONObject("data") != null) {
            posts = json.optJSONObject("data").optJSONArray("posts");
        } else if (json.has("feed")) posts = json.optJSONArray("feed");

        if (posts == null) {
            // sometimes the structure nests differently; try to find first array value
            for (String key : json.keySet()) {
                if (json.opt(key) instanceof org.json.JSONArray) {
                    posts = json.optJSONArray(key);
                    break;
                }
            }
        }

        if (posts == null || posts.length() == 0) return "🔵 Bluesky results:\n• No results.";
        return extractPostsFromArray(posts);
     }

    private String extractPostsFromArray(org.json.JSONArray posts) {
        StringBuilder sb = new StringBuilder("🔵 Bluesky results:\n");
        for (int i = 0; i < Math.min(posts.length(), 10); i++) {
            org.json.JSONObject item = posts.optJSONObject(i);
            if (item == null) continue;
            String text = "";
            if (item.has("post") && item.opt("post") instanceof org.json.JSONObject) {
                org.json.JSONObject p = item.optJSONObject("post");
                text = p.optString("text", p.optJSONObject("record") == null ? "" : p.optJSONObject("record").optString("text", ""));
            } else if (item.has("record") && item.opt("record") instanceof org.json.JSONObject) {
                text = item.optJSONObject("record").optString("text", "");
            } else {
                text = item.optString("text", "");
            }
            text = text.replaceAll("\\s+", " ").trim();
            if (text.isEmpty()) continue;
            sb.append("• ").append(text.length() > 120 ? text.substring(0, 120) + "…" : text).append("\n");
        }
        if (sb.length() == "🔵 Bluesky results:\n".length()) sb.append("• No results.");
        return sb.toString();
    }

    

    private String searchMastodonAuth(String query, String instance, String accessToken) throws Exception {
        instance = instance.replaceAll("https?://", "").split("/")[0];
        if (instance.startsWith("@")) instance = instance.substring(1);

        String encodedQuery = java.net.URLEncoder.encode(query, "UTF-8");
        String url = String.format(
            "https://%s/api/v2/search?q=%s&type=statuses&limit=10&resolve=true",
            instance, encodedQuery
        );

        var request = java.net.http.HttpRequest.newBuilder()
            .uri(java.net.URI.create(url))
            .header("Authorization", "Bearer " + accessToken)
            .timeout(java.time.Duration.ofSeconds(10))
            .GET()
            .build();

        var client = java.net.http.HttpClient.newHttpClient();
        var response = client.send(request, java.net.http.HttpResponse.BodyHandlers.ofString());

        if (response.statusCode() != 200) {
            throw new RuntimeException("Mastodon auth search failed: " + response.statusCode());
        }

        var json = new org.json.JSONObject(response.body());
        var statuses = json.getJSONArray("statuses");
        StringBuilder sb = new StringBuilder("🐘 (Auth) Mastodon results:\n");
        for (int i = 0; i < Math.min(statuses.length(), 3); i++) {
            var status = statuses.getJSONObject(i);
            String content = status.optString("content", "").replaceAll("<[^>]*>", "").replaceAll("\\s+", " ");
            sb.append("• ").append(content.length() > 80 ? content.substring(0, 80) + "…" : content).append("\n");
        }
        return sb.toString();
    }
    private VBox createSidebar(Runnable onBlueskyLogin, Runnable onMastodonLogin) {
        sidebarContent = new VBox(15);
        sidebarContent.setPadding(new Insets(30, 20, 20, 0));
        sidebarContent.setAlignment(Pos.TOP_CENTER);
        sidebarContent.setPrefWidth(220);
        sidebarContent.setVisible(true);
        sidebarContent.setManaged(true);

        try {
            Image blueskyImage = new Image(getClass().getResourceAsStream("/images/Bluesky_Logo.png"));
            Image mastodonImage = new Image(getClass().getResourceAsStream("/images/mastodon.svg.png"));

            ImageView blueskyView = new ImageView(blueskyImage);
            blueskyView.setFitHeight(40); blueskyView.setFitWidth(40);
            ImageView mastodonView = new ImageView(mastodonImage);
            mastodonView.setFitHeight(40); mastodonView.setFitWidth(40);

            Button blueskyButton = new Button("", blueskyView);
            MainPage.styleBigButton(blueskyButton);
            blueskyButton.setOnAction(e -> {
                if (onBlueskyLogin != null) onBlueskyLogin.run();
            });

            Button mastodonButton = new Button("", mastodonView);
            MainPage.styleBigButton(mastodonButton);
            mastodonButton.setOnAction(e -> {
                if (onMastodonLogin != null) onMastodonLogin.run();
            });

            Label trendingLabel = new Label("Trending");
            trendingLabel.setStyle("-fx-font-size: 24; -fx-font-weight: bold;");
            HBox trendingBox = new HBox(10, new Label("📈"), trendingLabel);

            Button uvleButton = new Button("UVLE");
            uvleButton.setStyle("-fx-background-radius: 20;");

            sidebarContent.getChildren().addAll(mastodonButton, blueskyButton, trendingBox, uvleButton);
        } catch (Exception ex) {
            sidebarContent.getChildren().add(new Label("⚠️ Sidebar error"));
        }

        VBox sidebarWrapper = new VBox(10);
        sidebarWrapper.setAlignment(Pos.TOP_RIGHT);
        sidebarWrapper.setPadding(new Insets(10, 10, 10, 0));
        sidebarWrapper.getChildren().add(sidebarContent);
        return sidebarWrapper;
    }

    private void showAlert(String message) {
        Platform.runLater(() -> {
            Alert alert = new Alert(Alert.AlertType.WARNING);
            alert.setTitle("Search");
            alert.setHeaderText(null);
            alert.setContentText(message);
            alert.showAndWait();
        });
    }

    public void showSearchResults(Node results) {
        Platform.runLater(() -> {
            resultsArea.getChildren().clear();
            if (results != null) {
                // Make results expand to fill available width
                if (results instanceof Region) {
                    ((Region) results).setMaxWidth(Double.MAX_VALUE);
                    results.setStyle("-fx-alignment: center; -fx-font-size: 14px;");
                }
                
                // If the results are text-based, ensure they're displayed properly
                if (results instanceof Label) {
                    Label label = (Label) results;
                    label.setWrapText(true);
                    label.setTextAlignment(javafx.scene.text.TextAlignment.LEFT);
                    label.setMaxWidth(Double.MAX_VALUE);
                }
                
                if (results instanceof VBox) {
                    VBox box = (VBox) results;
                    // Style each child node to show full text
                    for (Node child : box.getChildren()) {
                        if (child instanceof Label) {
                            Label label = (Label) child;
                            label.setWrapText(true);
                            label.setStyle("-fx-padding: 10; -fx-background-color: white; -fx-background-radius: 5;");
                            label.setMaxWidth(Double.MAX_VALUE);
                        }
                    }
                }
                
                // Center the results in the available space
                ScrollPane scrollPane = new ScrollPane(results);
                scrollPane.setFitToWidth(true);
                scrollPane.setStyle("-fx-background: transparent; -fx-background-color: transparent;");
                
                StackPane centeringPane = new StackPane(scrollPane);
                centeringPane.setAlignment(Pos.TOP_CENTER);
                VBox.setVgrow(centeringPane, Priority.ALWAYS);
                
                resultsArea.getChildren().add(centeringPane);
            }
        });
    }
    public static void debugBlueskyToken(String token) throws Exception {
    HttpClient client = HttpClient.newHttpClient();
    HttpRequest req = HttpRequest.newBuilder()
        .uri(URI.create("https://bsky.social/xrpc/com.atproto.server.getSession"))
        .header("Authorization", "Bearer " + token)
        .POST(HttpRequest.BodyPublishers.noBody()) // xrpc endpoints are POST
        .build();

    HttpResponse<String> resp = client.send(req, HttpResponse.BodyHandlers.ofString());
    System.out.println("getSession HTTP " + resp.statusCode() + " body=" + resp.body());
}
}