package org.kiko.dev.dtos.timeline;

import java.util.List;

public class MetadataTimeLineDto {
    private String dataVersion;
    private String matchId;
    private List<String> participants;

    // Getters and Setters
    public String getDataVersion() {
        return dataVersion;
    }

    public void setDataVersion(String dataVersion) {
        this.dataVersion = dataVersion;
    }

    public String getMatchId() {
        return matchId;
    }

    public void setMatchId(String matchId) {
        this.matchId = matchId;
    }

    public List<String> getParticipants() {
        return participants;
    }

    public void setParticipants(List<String> participants) {
        this.participants = participants;
    }
}

