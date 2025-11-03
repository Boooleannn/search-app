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
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.time.Instant;
import javafx.scene.Parent;

public class HomePage extends BorderPane {
    private VBox sidebarContent;
    private boolean sidebarExpanded = true;
    private VBox resultsArea;
    private final String blueskyHandle;
    private final String mastodonHandle;

    public HomePage(String platform,
                    String blueskyAccessToken,
                   String mastodonAccessToken,
                    String mastodonInstance,
                    String blueskyHandle,
                    String mastodonHandle,
                    Runnable onGoBack,
                    Runnable onBlueskyLogin,
                    Runnable onMastodonLogin){
        this.blueskyHandle = blueskyHandle == null ? "" : blueskyHandle;
        this.mastodonHandle = mastodonHandle == null ? "" : mastodonHandle;
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

        // Determine which logos to show based on logged-in status
        boolean blueskyLoggedIn = blueskyAccessToken != null && !blueskyAccessToken.isBlank();
        boolean mastodonLoggedIn = mastodonAccessToken != null && !mastodonAccessToken.isBlank();

        HBox logoContainer = new HBox(8);
        logoContainer.setAlignment(Pos.CENTER_LEFT);

        // Add Bluesky logo if logged in
        if (blueskyLoggedIn) {
            try {
                ImageView blueskyLogo = new ImageView(new Image(getClass().getResourceAsStream("/images/Bluesky_Logo.png")));
                blueskyLogo.setFitHeight(32);
                blueskyLogo.setFitWidth(32);
                logoContainer.getChildren().add(blueskyLogo);
            } catch (Exception e) {
                System.err.println("Could not load Bluesky logo: " + e.getMessage());
            }
        }

        // Add Mastodon logo if logged in
        if (mastodonLoggedIn) {
            try {
                ImageView mastodonLogo = new ImageView(new Image(getClass().getResourceAsStream("/images/mastodon.svg.png")));
                mastodonLogo.setFitHeight(32);
                mastodonLogo.setFitWidth(32);
                logoContainer.getChildren().add(mastodonLogo);
            } catch (Exception e) {
                System.err.println("Could not load Mastodon logo: " + e.getMessage());
            }
        }

        // If no accounts logged in, show a default placeholder
        if (!blueskyLoggedIn && !mastodonLoggedIn) {
            Label placeholder = new Label("No accounts");
            placeholder.setStyle("-fx-font-size: 14; -fx-text-fill: #666;");
            logoContainer.getChildren().add(placeholder);
        }

        TextField searchField = new TextField();
        searchField.setPromptText("Search for posts, accounts, trends");
        // Make search field expand to fill available space
        HBox.setHgrow(searchField, Priority.ALWAYS);
        searchField.setStyle("-fx-background-radius: 20; -fx-font-size: 16;");

        CheckBox cbBluesky = new CheckBox("Bluesky");
        CheckBox cbMastodon = new CheckBox("Mastodon");
        
        // Set checkboxes based on login status and select by default if logged in
        cbBluesky.setSelected(blueskyLoggedIn);
        cbBluesky.setDisable(!blueskyLoggedIn);
        cbMastodon.setSelected(mastodonLoggedIn);
        cbMastodon.setDisable(!mastodonLoggedIn);

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

        // Sort UI: choice box for Mixed / Latest / Most liked
        ComboBox<String> sortBox = new ComboBox<>();
        sortBox.getItems().addAll("Mixed", "Latest", "Most liked");
        sortBox.setValue("Mixed");
        Label sortLabel = new Label("Sort:");
        HBox sortBoxWrap = new HBox(6, sortLabel, sortBox);
        sortBoxWrap.setAlignment(Pos.CENTER_LEFT);

        // Main content layout
        HBox leftContent = new HBox(10);
        leftContent.getChildren().addAll(logoContainer, searchField, checkBoxGroup, sortBoxWrap, buttonGroup, toggleSidebarBtn);
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
                    box.setSpacing(12);

                    java.util.List<Node> blueskyCards = new java.util.ArrayList<>();
                    java.util.List<Node> mastodonCards = new java.util.ArrayList<>();
                    java.util.List<Node> merged = new java.util.ArrayList<>();

                    // collect Bluesky cards
                    if (cbBluesky.isSelected()) {
                        if (blueskyAccessToken == null || blueskyAccessToken.isBlank()) {
                            blueskyCards.add(new Label("❌ Not logged into Bluesky."));
                        } else {
                            try {
                                String body = searchBlueskyRaw(q, blueskyAccessToken);
                                blueskyCards.addAll(PostCards.buildBlueskyCardsFromBody(body));
                            } catch (Exception ex) {
                                Label err = new Label("❌ Bluesky error: " + ex.getMessage());
                                blueskyCards.add(err);
                                System.err.println("[Bluesky] search exception: " + ex.getMessage());
                            }
                        }
                    }

                    // collect Mastodon cards
                    if (cbMastodon.isSelected()) {
                        if (mastodonAccessToken == null || mastodonAccessToken.isBlank()) {
                            mastodonCards.add(new Label("❌ Not logged into Mastodon."));
                        } else {
                            try {
                                String instanceHost = (mastodonInstance == null) ? "" : mastodonInstance;
                                String body = searchMastodonRaw(q, instanceHost, mastodonAccessToken);
                                mastodonCards.addAll(PostCards.buildMastodonCardsFromBody(body));
                            } catch (Exception ex) {
                                Label err = new Label("❌ Mastodon error: " + ex.getMessage());
                                mastodonCards.add(err);
                                System.err.println("[Mastodon] search exception: " + ex.getMessage());
                            }
                        }
                    }

                    // Interleave the two lists (round-robin)
                    int bi = 0, mi = 0;
                    while (bi < blueskyCards.size() || mi < mastodonCards.size()) {
                        if (bi < blueskyCards.size()) {
                            merged.add(blueskyCards.get(bi++));
                        }
                        if (mi < mastodonCards.size()) {
                            merged.add(mastodonCards.get(mi++));
                        }
                    }

                    // If merged empty, show appropriate message
                    if (merged.isEmpty()) {
                        String msg = "No results.";
                        if (cbBluesky.isSelected() && !cbMastodon.isSelected()) msg = "🔵 Bluesky: No results.";
                        if (cbMastodon.isSelected() && !cbBluesky.isSelected()) msg = "🐘 Mastodon: No results.";
                        box.getChildren().add(new Label(msg));
                    } else {
                        // Add merged cards to box with consistent styling
                        for (Node n : merged) {
                            VBox.setMargin(n, new Insets(8));
                            n.setStyle("-fx-background-color: white; -fx-padding: 12; -fx-background-radius: 8;");
                            box.getChildren().add(n);
                        }
                    }
                    return box;
                }
            };

            task.setOnSucceeded(event -> showSearchResults(task.getValue()));
            task.setOnSucceeded(event -> {
                showSearchResults(task.getValue());
                // apply selected sort after results are displayed
                Platform.runLater(() -> applySort(sortBox.getValue()));
            });
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
            String url = host + "/xrpc/app.bsky.feed.searchPosts?q=" + q + "&limit=50";
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
                System.err.println("[Bluesky] AppView 403 on " + host + " – trying fallback");
                continue;
            }
            String shortBody = resp.body() == null ? "" :
                (resp.body().length() > 300 ? resp.body().substring(0, 300) + "…" : resp.body());
            throw new RuntimeException("Bluesky search failed: " + code + " " + shortBody);
        }

        if (accessJwt != null && !accessJwt.isBlank()) {
            String pdsHost = "https://bsky.social";
            String url = pdsHost.replaceAll("/+$", "") + "/xrpc/app.bsky.feed.searchPosts?q=" + q + "&limit=50";
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
        String url = "https://" + inst + "/api/v2/search?type=statuses&q=" + q + "&limit=50&resolve=true";

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

    // Helper to summarize posts array safely
    private String prettyPrintSearch(String body) {
        if (body == null || body.isBlank()) return "🔵 Bluesky results:\n• Empty response.";
        org.json.JSONObject json;
        try {
            json = new org.json.JSONObject(body);
        } catch (Exception e) {
            // not a JSON object – try array
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

            // Bluesky row: icon + handle label
            HBox blueskyRow = new HBox(8);
            blueskyRow.setAlignment(Pos.CENTER_LEFT);
            Button blueskyButton = new Button("", blueskyView);
            MainPage.styleBigButton(blueskyButton);
            blueskyButton.setOnAction(e -> { if (onBlueskyLogin != null) onBlueskyLogin.run(); });
            Label blueskyHandleLbl = new Label(this.blueskyHandle);
            blueskyHandleLbl.setStyle("-fx-font-size: 13px; -fx-text-fill: #333;");
            blueskyRow.getChildren().addAll(blueskyButton, blueskyHandleLbl);

            // Mastodon row: icon + handle label
            HBox mastodonRow = new HBox(8);
            mastodonRow.setAlignment(Pos.CENTER_LEFT);
            Button mastodonButton = new Button("", mastodonView);
            MainPage.styleBigButton(mastodonButton);
            mastodonButton.setOnAction(e -> { if (onMastodonLogin != null) onMastodonLogin.run(); });
            Label mastodonHandleLbl = new Label(this.mastodonHandle);
            mastodonHandleLbl.setStyle("-fx-font-size: 13px; -fx-text-fill: #333;");
            mastodonRow.getChildren().addAll(mastodonButton, mastodonHandleLbl);



            sidebarContent.getChildren().addAll(mastodonRow, blueskyRow);
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
                    results.setStyle("-fx-alignment: center; -fx-font-size: 14px; -fx-text-fill: #000000;");
                }
                
                // If the results are text-based, ensure they're displayed properly
                if (results instanceof Label) {
                    Label label = (Label) results;
                    label.setWrapText(true);
                    label.setTextAlignment(javafx.scene.text.TextAlignment.CENTER);  // Center text
                    label.setAlignment(Pos.CENTER);  // Center the label itself
                    label.setMaxWidth(Double.MAX_VALUE);
                    label.setStyle("-fx-text-fill: #000000; -fx-font-size: 14px; -fx-padding: 10;");
                }
                
                if (results instanceof VBox) {
                    VBox box = (VBox) results;
                    box.setAlignment(Pos.CENTER);  // Center the VBox contents
                    
                    // Style each child node to show full text
                    for (Node child : box.getChildren()) {
                        if (child instanceof Label) {
                            Label label = (Label) child;
                            label.setWrapText(true);
                            label.setTextAlignment(javafx.scene.text.TextAlignment.CENTER);  // Center text
                            label.setAlignment(Pos.CENTER);  // Center the label itself
                            
                            // Different styling for different message types
                            String text = label.getText();
                            if (text.contains("⏳")) {  // Loading message
                                label.setStyle("-fx-padding: 10; -fx-background-color: #e8f5e9; -fx-background-radius: 5; -fx-text-fill: #000000;");
                            } else if (text.contains("❌")) {  // Error messages
                                label.setStyle("-fx-padding: 10; -fx-background-color: #ffebee; -fx-text-fill: #c62828; -fx-background-radius: 5;");
                            } else if (text.contains("🔵") && text.contains("No results")) {        // Bluesky no results
                                label.setStyle("-fx-padding: 10; -fx-background-color: #e3f2fd; -fx-text-fill: #1565c0; -fx-background-radius: 5;");
                            } else if (text.contains("🔵") && text.contains("Bluesky Results")) {  // Bluesky results
                                label.setStyle("-fx-padding:    ; -fx-background-color: #e3f2fd; -fx-text-fill: #1565c0; -fx-background-radius: 5;");
                            } else if (text.contains("🐘") && text.contains("No results")) {        // Mastodon no results
                                label.setStyle("-fx-padding: 10; -fx-background-color: #ede7f6; -fx-text-fill: #4527a0; -fx-background-radius: 5;");
                            } else if (text.contains("🐘") && text.contains("Mastodon Results")) {  // Mastodon  results
                                label.setStyle("-fx-padding: 10; -fx-background-color: #ede7f6; -fx-text-fill: #4527a0; -fx-background-radius: 5;");
                            } else {  // Other messages
                                label.setStyle("-fx-padding: 10; -fx-background-color: white; -fx-background-radius: 5; -fx-text-fill: #0034ddff;");
                            }
                            
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
    // --- helpers for extracting text and sorting ---
    private static void collectText(Node node, StringBuilder sb) {
        if (node == null) return;
        if (node instanceof Labeled) {
            String t = ((Labeled) node).getText();
            if (t != null && !t.isBlank()) {
                if (sb.length() > 0) sb.append(" ");
                sb.append(t);
            }
        } else if (node instanceof javafx.scene.text.Text) {
            String t = ((javafx.scene.text.Text) node).getText();
            if (t != null && !t.isBlank()) {
                if (sb.length() > 0) sb.append(" ");
                sb.append(t);
            }
        } else if (node instanceof Parent) {
            for (Node child : ((Parent) node).getChildrenUnmodifiable()) {
                collectText(child, sb);
            }
        }
    }

    private static String nodeText(Node node) {
        StringBuilder sb = new StringBuilder();
        collectText(node, sb);
        return sb.toString().trim();
    }

    // Apply client-side sorting to currently displayed results
    private void applySort(String mode) {
        Platform.runLater(() -> {
            if (mode == null || mode.equals("Mixed")) return; // no-op for mixed

            if (resultsArea.getChildren().isEmpty()) return;
            Node first = resultsArea.getChildren().get(0);
            if (!(first instanceof StackPane)) return;
            StackPane sp = (StackPane) first;
            if (sp.getChildren().isEmpty()) return;
            Node scrollNode = sp.getChildren().get(0);
            if (!(scrollNode instanceof ScrollPane)) return;
            ScrollPane spane = (ScrollPane) scrollNode;
            Node content = spane.getContent();
            if (!(content instanceof VBox)) return;
            VBox resultsVBox = (VBox) content;

            java.util.List<Node> children = new java.util.ArrayList<>(resultsVBox.getChildren());

            if (mode.equals("Latest")) {
                // try to find ISO timestamps in node text and sort descending
                Pattern iso = Pattern.compile("(\\d{4}-\\d{2}-\\d{2}T\\d{2}:\\d{2}:\\d{2})(?:Z|[+-]\\d{2}:?\\d{2})?");
                java.util.Map<Node, Long> tsMap = new java.util.HashMap<>();
                for (Node n : children) {
                    String text = nodeText(n);
                    Matcher m = iso.matcher(text);
                    long t = 0;
                    if (m.find()) {
                        try {
                            Instant it = Instant.parse(m.group(1) + "Z");
                            t = it.toEpochMilli();
                        } catch (Exception ignored) {}
                    }
                    tsMap.put(n, t);
                }
                children.sort((a, b) -> Long.compare(tsMap.getOrDefault(b, 0L), tsMap.getOrDefault(a, 0L)));
            } else if (mode.equals("Most liked")) {
                // heuristic: find numeric likes in node text using common patterns
                Pattern likesPat = Pattern.compile("(?:\\b|\\D)(?:likes|like|faves|favourites|reposts|reblogs|replies|★|❤️|💙|👍)[:\\s]*([0-9,]+)", Pattern.CASE_INSENSITIVE);
                Pattern emojiNum = Pattern.compile("(?:👍|💙|❤️)\\s*([0-9,]+)");
                java.util.Map<Node, Integer> likesMap = new java.util.HashMap<>();
                for (Node n : children) {
                    String text = nodeText(n);
                    int val = 0;
                    Matcher m = likesPat.matcher(text);
                    if (m.find()) {
                        String num = m.group(1).replaceAll(",", "");
                        try { val = Integer.parseInt(num); } catch (Exception ignored) {}
                    } else {
                        Matcher me = emojiNum.matcher(text);
                        if (me.find()) {
                            String num = me.group(1).replaceAll(",", "");
                            try { val = Integer.parseInt(num); } catch (Exception ignored) {}
                        }
                    }
                    likesMap.put(n, val);
                }
                children.sort((a, b) -> Integer.compare(likesMap.getOrDefault(b, 0), likesMap.getOrDefault(a, 0)));
            }

            resultsVBox.getChildren().setAll(children);
        });
    }}