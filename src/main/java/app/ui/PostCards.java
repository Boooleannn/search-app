package app.ui;

import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.Node;
import javafx.scene.control.Label;
import javafx.scene.image.Image;
import javafx.scene.image.ImageView;
import javafx.scene.layout.*;
import javafx.scene.shape.Circle;
import javafx.scene.control.OverrunStyle;
import org.json.JSONArray;
import org.json.JSONObject;

import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;


public class PostCards {

    // ========== Public API ==========
    public static List<Node> buildBlueskyCardsFromBody(String body) {
        List<Node> nodes = new ArrayList<>();
        JSONObject json = new JSONObject(body);
        JSONArray posts = json.optJSONArray("posts");
        if (posts == null) return nodes;

        for (int i = 0; i < posts.length(); i++) {
            JSONObject p = posts.getJSONObject(i);

            JSONObject author = p.optJSONObject("author");
            String displayName = author == null ? "" : author.optString("displayName", "");
            String handle = author == null ? "" : "@" + author.optString("handle", "");
            String avatar = author == null ? null : author.optString("avatar", null);

            JSONObject record = p.optJSONObject("record");
            String text = record == null ? "" : record.optString("text", "");
            String createdAt = record == null ? p.optString("indexedAt", null)
                                              : record.optString("createdAt", p.optString("indexedAt", null));

            Integer likeCount = p.has("likeCount") ? p.optInt("likeCount") : null;
            Integer repostCount = p.has("repostCount") ? p.optInt("repostCount") : null;

            String imageUrl = null;
            JSONObject embed = p.optJSONObject("embed");
            if (embed != null) {
                JSONArray imgs = embed.optJSONArray("images");
                if (imgs != null && imgs.length() > 0) {
                    JSONObject im = imgs.getJSONObject(0);
                    imageUrl = im.optString("fullsize", im.optString("thumb", null));
                }
                if (imageUrl == null) {
                    JSONObject media = embed.optJSONObject("media");
                    if (media != null) {
                        JSONArray mimgs = media.optJSONArray("images");
                        if (mimgs != null && mimgs.length() > 0) {
                            JSONObject im = mimgs.getJSONObject(0);
                            imageUrl = im.optString("fullsize", im.optString("thumb", null));
                        }
                    }
                }
            }

            nodes.add(buildPostCard(
                    "bluesky", displayName, handle, avatar, createdAt,
                    text, imageUrl, likeCount, repostCount
            ));
        }
        return nodes;
    }

    public static List<Node> buildMastodonCardsFromBody(String body) {
        List<Node> nodes = new ArrayList<>();
        JSONObject json = new JSONObject(body);
        JSONArray statuses = json.optJSONArray("statuses");
        if (statuses == null) return nodes;

        for (int i = 0; i < statuses.length(); i++) {
            JSONObject st = statuses.getJSONObject(i);

            JSONObject acct = st.optJSONObject("account");
            String displayName = acct == null ? "" : acct.optString("display_name", "");
            String handle = acct == null ? "" : "@" + acct.optString("acct", "");
            String avatar = acct == null ? null : acct.optString("avatar", null);

            String text = stripHtml(st.optString("content", ""));
            String createdAt = st.optString("created_at", null);

            Integer likeCount = st.has("favourites_count") ? st.optInt("favourites_count") : null;
            Integer repostCount = st.has("reblogs_count") ? st.optInt("reblogs_count") : null;

            String imageUrl = null;
            JSONArray media = st.optJSONArray("media_attachments");
            if (media != null && media.length() > 0) {
                for (int j = 0; j < media.length(); j++) {
                    JSONObject m = media.getJSONObject(j);
                    if ("image".equalsIgnoreCase(m.optString("type", ""))) {
                        imageUrl = m.optString("url", m.optString("preview_url", null));
                        if (imageUrl != null && !imageUrl.isBlank()) break;
                    }
                }
            }

            nodes.add(buildPostCard(
                    "mastodon", displayName, handle, avatar, createdAt,
                    text, imageUrl, likeCount, repostCount
            ));
        }
        return nodes;
    }

    // ========== Internal UI helpers ==========
    private static Node buildPostCard(
            String platform,
            String displayName,
            String handle,
            String avatarUrl,
            String createdAtIso,
            String text,
            String imageUrl,
            Integer likeCount,
            Integer repostCount
    ) {
        VBox card = new VBox(6);
        card.setPadding(new Insets(10));
        card.setMaxWidth(720);
        card.setStyle("-fx-background-color: #ffffff; -fx-background-radius: 16; -fx-effect: dropshadow(gaussian, rgba(0,0,0,0.08), 10, 0, 0, 2);");

        HBox header = new HBox(10);
        header.setAlignment(Pos.CENTER_LEFT);

        ImageView avatar = new ImageView();
        avatar.setFitWidth(40);
        avatar.setFitHeight(40);
        avatar.setPreserveRatio(true);
        avatar.setSmooth(true);
        avatar.setClip(new Circle(20, 20, 20));

        if (avatarUrl != null && !avatarUrl.isBlank()) {
            try { avatar.setImage(new Image(avatarUrl, true)); } catch (Exception ignored) {}
        }

        VBox who = new VBox(2);
        Label nameLbl = new Label((displayName == null || displayName.isBlank()) ? handle : displayName);
        nameLbl.setStyle("-fx-font-weight: 700; -fx-font-size: 13px; -fx-text-fill: #000000;"); 

        Label metaLbl = new Label(
                (handle == null ? "" : handle) +
                (createdAtIso == null ? "" : " · " + formatRelativeTime(createdAtIso))
        );
        metaLbl.setStyle("-fx-text-fill: #667085; -fx-font-size: 12px;");

        who.getChildren().addAll(nameLbl, metaLbl);

        Region spacer = new Region();
        HBox.setHgrow(spacer, Priority.ALWAYS);
        Label platformPill = new Label(platform.equals("bluesky") ? "Bluesky" : "Mastodon");
        platformPill.setStyle("-fx-background-color: #eef2ff; -fx-text-fill: #3730a3; -fx-padding: 2 8; -fx-background-radius: 999; -fx-font-size: 11px;");

        header.getChildren().addAll(avatar, who, spacer, platformPill);

        Label textLbl = new Label(text == null ? "" : text);
        textLbl.setWrapText(true);
        textLbl.setStyle("-fx-font-size: 13px; -fx-text-fill: #000000;");  
        textLbl.setMaxWidth(680);
        textLbl.setPadding(new Insets(4, 0, 4, 0));
        textLbl.setTextOverrun(OverrunStyle.WORD_ELLIPSIS);

        ImageView media = null;
        if (imageUrl != null && !imageUrl.isBlank()) {
            media = new ImageView();
            media.setPreserveRatio(true);
            media.setFitWidth(680);
            media.setSmooth(true);
            media.setStyle("-fx-background-color:#f2f2f2; -fx-background-radius: 12;");
            try { media.setImage(new Image(imageUrl, true)); } catch (Exception ignored) {}
        }

        HBox footer = new HBox(16);
        footer.setAlignment(Pos.CENTER_LEFT);
        footer.setPadding(new Insets(2, 0, 0, 0));

        Label likes = new Label("❤ " + (likeCount == null ? 0 : likeCount));
        Label reposts = new Label((platform.equals("mastodon") ? "🔁 " : "↻ ") + (repostCount == null ? 0 : repostCount));
        likes.setStyle("-fx-text-fill:#475467; -fx-font-size: 12px;");
        reposts.setStyle("-fx-text-fill:#475467; -fx-font-size: 12px;");

        footer.getChildren().addAll(likes, reposts);

        card.getChildren().addAll(header, textLbl);
        if (media != null) card.getChildren().add(media);
        card.getChildren().add(footer);

        return card;
    }

    // ========== Small utils ==========
    private static String stripHtml(String html) {
        if (html == null) return "";
        return html.replaceAll("<[^>]+>", " ")
                   .replaceAll("&amp;", "&")
                   .replaceAll("&lt;", "<")
                   .replaceAll("&gt;", ">")
                   .replaceAll("\\s+", " ").trim();
    }

    private static String formatRelativeTime(String iso) {
        try {
            Instant then = Instant.parse(iso);
            long sec = Math.max(1, Math.abs(Duration.between(then, Instant.now()).getSeconds()));
            if (sec < 60) return sec + "s";
            long min = sec / 60; if (min < 60) return min + "m";
            long hr  = min / 60; if (hr  < 24) return hr + "h";
            long day = hr / 24;  if (day < 7) return day + "d";
            long wk  = day / 7;  if (wk  < 4) return wk + "w";
            long mo  = day / 30; if (mo  < 12) return mo + "mo";
            long yr  = day / 365; return yr + "y";
        } catch (Exception e) {
            return "";
        }
    }
}