package me.srrapero720.watermedia.api.network.pornhub;

public class VideoQuality {

    private final String resolution;
    private final String uri;

    public VideoQuality(String resolution, String uri) {
        this.resolution = resolution;
        this.uri = uri;
    }

    public String getResolution() {
        return resolution;
    }

    public String getUri() {
        return uri;
    }
}
