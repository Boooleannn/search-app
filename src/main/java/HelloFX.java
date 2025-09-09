import javafx.application.Application;
import javafx.geometry.Insets;
import javafx.scene.Scene;
import javafx.scene.control.*;
import javafx.scene.layout.*;
import javafx.stage.Stage;

public class HelloFX extends Application {

    private TabPane tabPane;
    private Label statusLabel;

    @Override
    public void start(Stage stage) {
        // Create Tabs
        tabPane = new TabPane();
        tabPane.getTabs().addAll(createBlueskyTab(), createMastodonTab());

        // Status Label
        statusLabel = new Label("Enter credentials and login.");
        statusLabel.setStyle("-fx-text-fill: #007acc;");

        // Layout
        VBox root = new VBox(10, tabPane, statusLabel);
        root.setPadding(new Insets(20));
        VBox.setVgrow(tabPane, Priority.ALWAYS);

        // Scene
        Scene scene = new Scene(root, 800, 500);
        stage.setScene(scene);
        stage.setTitle("Social Search: Bluesky + Mastodon");
        stage.show();
    }

    private Tab createBlueskyTab() {
        Tab tab = new Tab("Bluesky");

        TextField usernameField = new TextField();
        usernameField.setPromptText("Username (e.g., user.bsky.social)");

        PasswordField appPasswordField = new PasswordField();
        appPasswordField.setPromptText("App Password (created in Bluesky settings)");

        Button loginButton = new Button("Login to Bluesky");  
        loginButton.setOnAction(e -> handleBlueskyLogin(usernameField.getText(), appPasswordField.getText()));

        VBox form = new VBox(10, new Label("Username:"), usernameField,
                             new Label("App Password:"), appPasswordField, loginButton);
        form.setPadding(new Insets(20));

        tab.setContent(form);
        return tab;
    }

    private Tab createMastodonTab() {
        Tab tab = new Tab("Mastodon");

        TextField instanceUrlField = new TextField();
        instanceUrlField.setPromptText("Instance URL (e.g., https://mastodon.social)");

        TextField clientIdField = new TextField();
        clientIdField.setPromptText("Client ID (register app on your instance)");

        PasswordField clientSecretField = new PasswordField();
        clientSecretField.setPromptText("Client Secret");

        Button loginButton = new Button("Login to Mastodon");
        loginButton.setOnAction(e -> handleMastodonLogin(
            instanceUrlField.getText(),
            clientIdField.getText(),
            clientSecretField.getText()
        ));

        VBox form = new VBox(10,
            new Label("Instance URL:"), instanceUrlField,
            new Label("Client ID:"), clientIdField,
            new Label("Client Secret:"), clientSecretField,
            loginButton
        );
        form.setPadding(new Insets(20));

        tab.setContent(form);
        return tab;
    }

    private void handleBlueskyLogin(String username, String appPassword) {
        if (username.isEmpty() || appPassword.isEmpty()) {
            statusLabel.setText("❌ Bluesky: Please fill in all fields.");
            return;
        }

        // TODO: Call Bluesky API to authenticate
        statusLabel.setText("✅ Bluesky login placeholder — to be implemented.");

        // Later: Use HttpClient to POST to https://bsky.social/xrpc/com.atproto.server.createSession
        System.out.println("Bluesky Login Attempt: " + username);
    }

    private void handleMastodonLogin(String instanceUrl, String clientId, String clientSecret) {
        if (instanceUrl.isEmpty() || clientId.isEmpty() || clientSecret.isEmpty()) {
            statusLabel.setText("❌ Mastodon: Please fill in all fields.");
            return;
        }

        // TODO: Start OAuth flow or use client credentials
        statusLabel.setText("✅ Mastodon login placeholder — to be implemented.");

        // Later: Redirect to instanceUrl + "/oauth/authorize?client_id=..."
        System.out.println("Mastodon Login Attempt: " + instanceUrl);
    }

    public static void main(String[] args) {
        launch(args);
    }
}