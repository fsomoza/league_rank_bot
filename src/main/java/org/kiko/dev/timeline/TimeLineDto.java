package org.kiko.dev.timeline;

import java.util.List;

public class TimeLineDto {
    private MetadataTimeLineDto metadata;
    private InfoTimeLineDto info;

    // Getters and Setters
    public MetadataTimeLineDto getMetadata() {
        return metadata;
    }

    public void setMetadata(MetadataTimeLineDto metadata) {
        this.metadata = metadata;
    }

    public InfoTimeLineDto getInfo() {
        return info;
    }

    public void setInfo(InfoTimeLineDto info) {
        this.info = info;
    }
}
