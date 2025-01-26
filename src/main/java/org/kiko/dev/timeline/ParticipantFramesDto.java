package org.kiko.dev.timeline;

import java.util.Map;


import java.util.Map;

public class ParticipantFramesDto {
    private Map<String, ParticipantFrameDto> participantFrames; // Matches JSON key

    // Corrected setter name to match JSON property
    public void setParticipantFrames(Map<String, ParticipantFrameDto> participantFrames) {
        this.participantFrames = participantFrames;
    }

    public Map<String, ParticipantFrameDto> getParticipantFrames() {
        return participantFrames;
    }
}
