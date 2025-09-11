
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.control.*;
import javafx.scene.image.Image;
import javafx.scene.image.ImageView;
import javafx.scene.layout.*;



public class HomePage extends BorderPane {
    private VBox sidebarContent;
    private boolean sidebarExpanded = true;

    public HomePage(String platform, 
                    Runnable onGoBack,
                    Runnable onBlueskyLogin,
                    Runnable onMastodonLogin
                    ) {
        // Top search bar (same as before)
        HBox searchBar = new HBox(10);
        searchBar.setPadding(new Insets(20, 20, 10, 20));
        searchBar.setAlignment(Pos.CENTER_LEFT);

        String iconPath = platform.equalsIgnoreCase("mastodon") ?
            "/images/mastodon.svg.png" : "/images/Bluesky_Logo.png";
        ImageView logo = new ImageView(new Image(getClass().getResourceAsStream(iconPath)));
        logo.setFitHeight(32);
        logo.setFitWidth(32);

        TextField searchField = new TextField();
        searchField.setPromptText("Search for posts, accounts, trends");
        searchField.setPrefWidth(600);
        searchField.setStyle("-fx-background-radius: 20; -fx-font-size: 16;");
        searchBar.getChildren().addAll(logo, searchField);

        Button goBackButton = new Button("Go Back");
        goBackButton.setStyle("-fx-background-radius: 20;");
        goBackButton.setOnAction(e -> {
            System.out.println("Bluesky sidebar button clicked");
            if (onGoBack != null) onGoBack.run();
        });
        searchBar.getChildren().add(goBackButton);

        Button toggleSidebarBtn = new Button("☰");
        toggleSidebarBtn.setStyle(
            "-fx-font-size: 24;" +
            "-fx-background-color: transparent;" +
            "-fx-padding: 0;" +
            "-fx-border-width: 0;"
        );
        toggleSidebarBtn.setOnAction(e -> {
            sidebarExpanded = !sidebarExpanded;
            sidebarContent.setVisible(sidebarExpanded);
            sidebarContent.setManaged(sidebarExpanded);
        });

        Region spacer = new Region();
        HBox.setHgrow(spacer, Priority.ALWAYS);
        searchBar.getChildren().addAll(spacer, toggleSidebarBtn);

        // Tabs (same as before)
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

        VBox centerBox = new VBox(tabs);
        centerBox.setStyle("-fx-background-color: #a3a3ff;");
        centerBox.setPrefHeight(400);
        centerBox.setPadding(new Insets(10, 20, 20, 20));

        // Sidebar content (put all sidebar elements here)
        sidebarContent = new VBox(15);
        sidebarContent.setPadding(new Insets(30, 20, 20, 0));
        sidebarContent.setAlignment(Pos.TOP_CENTER);
        sidebarContent.setPrefWidth(220);


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
        Button blueskyButton = new Button("", blueskyView);
        MainPage.styleBigButton(blueskyButton);
        blueskyButton.setOnAction(e -> {
            if (onBlueskyLogin != null) onBlueskyLogin.run();
        });

        blueskyButton.setAlignment(Pos.CENTER_LEFT);
        blueskyButton.setContentDisplay(ContentDisplay.LEFT);

        // Mastodon Button with Image
        Button mastodonButton = new Button("", mastodonView);
        mastodonButton.setGraphic(mastodonView);
        MainPage.styleBigButton(mastodonButton);
        mastodonButton.setOnAction(e -> {
            if (onMastodonLogin != null) onMastodonLogin.run();
        });
        mastodonButton.setAlignment(Pos.CENTER_RIGHT);
        mastodonButton.setContentDisplay(ContentDisplay.LEFT);



        Label trendingLabel = new Label("Trending");
        trendingLabel.setStyle("-fx-font-size: 24; -fx-font-weight: bold;");
        HBox trendingBox = new HBox(10, new Label("📈"), trendingLabel);

        Button uvleButton = new Button("UVLE");
        uvleButton.setStyle("-fx-background-radius: 20;");

        sidebarContent.getChildren().addAll(mastodonButton, blueskyButton, trendingBox, uvleButton);

        // Sidebar wrapper with toggle button
        VBox sidebarWrapper = new VBox(10);
        sidebarWrapper.setAlignment(Pos.TOP_RIGHT);
        sidebarWrapper.setPadding(new Insets(10, 10, 10, 0));

    sidebarWrapper.getChildren().add(sidebarContent);        
        this.setTop(searchBar);
        this.setCenter(centerBox);
        this.setRight(sidebarWrapper);

        // Background color
        this.setStyle("-fx-background-color: #6ec6ff;");
    }
}