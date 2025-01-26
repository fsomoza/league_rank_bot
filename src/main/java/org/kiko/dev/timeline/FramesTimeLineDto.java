package org.kiko.dev.timeline;

import java.util.List;
import java.util.Map;

public class FramesTimeLineDto {
    private List<EventsTimeLineDto> events;
    private Map<String, ParticipantFrameDto> participantFrames; // Direct map without wrapper
    private int timestamp;

    // Getters and Setters
    public List<EventsTimeLineDto> getEvents() {
        return events;
    }

    public void setEvents(List<EventsTimeLineDto> events) {
        this.events = events;
    }

    public Map<String, ParticipantFrameDto> getParticipantFrames() {
        return participantFrames;
    }

    public void setParticipantFrames(Map<String, ParticipantFrameDto> participantFrames) {
        this.participantFrames = participantFrames;
    }

    public int getTimestamp() {
        return timestamp;
    }

    public void setTimestamp(int timestamp) {
        this.timestamp = timestamp;
    }
}