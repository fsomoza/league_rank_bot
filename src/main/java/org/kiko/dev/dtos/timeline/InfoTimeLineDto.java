package org.kiko.dev.dtos.timeline;

import java.util.List;

public class InfoTimeLineDto {
    private String endOfGameResult;
    private long frameInterval;
    private long gameId;
    private List<ParticipantTimeLineDto> participants;
    private List<FramesTimeLineDto> frames;

    // Getters and Setters
    public String getEndOfGameResult() {
        return endOfGameResult;
    }

    public void setEndOfGameResult(String endOfGameResult) {
        this.endOfGameResult = endOfGameResult;
    }

    public long getFrameInterval() {
        return frameInterval;
    }

    public void setFrameInterval(long frameInterval) {
        this.frameInterval = frameInterval;
    }

    public long getGameId() {
        return gameId;
    }

    public void setGameId(long gameId) {
        this.gameId = gameId;
    }

    public List<ParticipantTimeLineDto> getParticipants() {
        return participants;
    }

    public void setParticipants(List<ParticipantTimeLineDto> participants) {
        this.participants = participants;
    }

    public List<FramesTimeLineDto> getFrames() {
        return frames;
    }

    public void setFrames(List<FramesTimeLineDto> frames) {
        this.frames = frames;
    }
}
