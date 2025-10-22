public class SearchRequest {
    private final String query;
    private final boolean bluesky;
    private final boolean mastodon;

    public SearchRequest(String query, boolean bluesky, boolean mastodon) {
        this.query = query;
        this.bluesky = bluesky;
        this.mastodon = mastodon;
    }

    public String getQuery() { return query; }
    public boolean isBluesky() { return bluesky; }
    public boolean isMastodon() { return mastodon; }
}