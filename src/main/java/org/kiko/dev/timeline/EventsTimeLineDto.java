package org.kiko.dev.timeline;

public class EventsTimeLineDto {
    private long timestamp;
    private long realTimestamp;
    private String type;

    // Getters and Setters
    public long getTimestamp() {
        return timestamp;
    }

    public void setTimestamp(long timestamp) {
        this.timestamp = timestamp;
    }

    public long getRealTimestamp() {
        return realTimestamp;
    }

    public void setRealTimestamp(long realTimestamp) {
        this.realTimestamp = realTimestamp;
    }

    public String getType() {
        return type;
    }

    public void setType(String type) {
        this.type = type;
    }
}
