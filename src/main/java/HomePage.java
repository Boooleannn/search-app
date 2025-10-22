import javafx.application.Platform;
import javafx.concurrent.Task;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.Node;
import javafx.scene.control.*;
import javafx.scene.image.Image;
import javafx.scene.image.ImageView;
import javafx.scene.layout.*;
import java.util.concurrent.Executor;
import java.util.concurrent.Executors;

public class HomePage extends BorderPane {
    private VBox sidebarContent;
    private boolean sidebarExpanded = true;
    private VBox resultsArea;

    public HomePage(String platform,
                    String blueskyAccessToken, 
                    String mastodonAccessToken,  
                    Runnable onGoBack,
                    Runnable onBlueskyLogin,
                    Runnable onMastodonLogin) {
        // === Top: Search Bar ===
        HBox searchBar = createSearchBar(platform, onGoBack, blueskyAccessToken, mastodonAccessToken);
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

        VBox centerBox = new VBox(tabs, resultsArea);
        centerBox.setStyle("-fx-background-color: #f0f8ff;");
        this.setCenter(centerBox);

        // === Right: Sidebar ===
        VBox sidebarWrapper = createSidebar(onBlueskyLogin, onMastodonLogin);
        this.setRight(sidebarWrapper);

        this.setStyle("-fx-background-color: #e6f2ff;");
    }

    private HBox createSearchBar(String platform, Runnable onGoBack, String blueskyAccessToken, String mastodonAccessToken) {
        HBox searchBar = new HBox(10);
        searchBar.setPadding(new Insets(20, 20, 10, 20));
        searchBar.setAlignment(Pos.CENTER_LEFT);

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
        searchField.setPrefWidth(480);
        searchField.setStyle("-fx-background-radius: 20; -fx-font-size: 16;");

        CheckBox cbBluesky = new CheckBox("Bluesky");
        CheckBox cbMastodon = new CheckBox("Mastodon");
        cbBluesky.setSelected(true);
        cbMastodon.setSelected(true);

        Button searchBtn = new Button("Search");
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
                    if (cbBluesky.isSelected()) {
                        if (blueskyAccessToken == null || blueskyAccessToken.isEmpty()) {
                            box.getChildren().add(new Label("❌ Not logged into Bluesky."));
                        } else {
                            try {
                                String blueskyResult = searchBlueskyAuth(q, blueskyAccessToken);
                                TextArea blueskyArea = new TextArea(blueskyResult);
                                blueskyArea.setEditable(false);
                                blueskyArea.setWrapText(true);
                                blueskyArea.setPrefHeight(100);
                                box.getChildren().add(blueskyArea);
                            } catch (Exception ex) {
                                box.getChildren().add(new Label("❌ Bluesky error: " + ex.getMessage() + blueskyAccessToken));
                            }
                        }
                    }
                    if (cbMastodon.isSelected()) {
                        box.getChildren().add(new Label("Mastodon results for: " + q));
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

        HBox searchLeft = new HBox(8);
        if (logo != null) searchLeft.getChildren().add(logo);
        searchLeft.getChildren().addAll(searchField, searchBtn);

        VBox leftSearch = new VBox(6, searchLeft, new HBox(8, cbBluesky, cbMastodon));
        leftSearch.setAlignment(Pos.CENTER_LEFT);

        Button goBackButton = new Button("Go Back");
        goBackButton.setStyle("-fx-background-radius: 20;");
        goBackButton.setOnAction(e -> {
            if (onGoBack != null) onGoBack.run();
        });

        Region spacer = new Region();
        HBox.setHgrow(spacer, Priority.ALWAYS);

        Button toggleSidebarBtn = new Button("☰");
        toggleSidebarBtn.setStyle("-fx-font-size: 24; -fx-background-color: transparent; -fx-padding: 0; -fx-border-width: 0;");
        toggleSidebarBtn.setOnAction(e -> {
            sidebarExpanded = !sidebarExpanded;
            if (sidebarContent != null) {
                sidebarContent.setVisible(sidebarExpanded);
                sidebarContent.setManaged(sidebarExpanded);
            }
        });

        searchBar.getChildren().addAll(leftSearch, goBackButton, spacer, toggleSidebarBtn);
        return searchBar;
    }
    private String searchBlueskyAuth(String query, String accessJwt) throws Exception {
        String url = "https://bsky.social/xrpc/app.bsky.feed.searchPosts";

        String jsonBody = new org.json.JSONObject()
            .put("q", query)
            .put("limit", 10)
            .toString();

        var request = java.net.http.HttpRequest.newBuilder()
            .uri(java.net.URI.create(url))
            .header("Content-Type", "application/json")
            .header("Authorization", "Bearer " + accessJwt)
            .timeout(java.time.Duration.ofSeconds(10))
            .POST(java.net.http.HttpRequest.BodyPublishers.ofString(jsonBody))
            .build();

        var client = java.net.http.HttpClient.newHttpClient();
        var response = client.send(request, java.net.http.HttpResponse.BodyHandlers.ofString());

        if (response.statusCode() != 200) {
            throw new RuntimeException("Bluesky auth search failed: " + response.statusCode());
        }

        // Parse posts (same as before)
        var json = new org.json.JSONObject(response.body());
        var posts = json.getJSONArray("posts");
        StringBuilder sb = new StringBuilder("🔵 (Auth) Bluesky results:\n");
        for (int i = 0; i < Math.min(posts.length(), 3); i++) {
            var post = posts.getJSONObject(i);
            String text = post.optString("text", "").replaceAll("\\s+", " ");
            sb.append("• ").append(text.length() > 80 ? text.substring(0, 80) + "…" : text).append("\n");
        }
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
                resultsArea.getChildren().add(results);
            }
        });
    }
}