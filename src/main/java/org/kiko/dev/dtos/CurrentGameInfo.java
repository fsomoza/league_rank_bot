package org.kiko.dev.dtos;

import java.util.List;

public class CurrentGameInfo {
    private String queueType;
    private String gameId;
    private List<Participant> participants;


    public CurrentGameInfo() {
        this.queueType = "";
        this.gameId = "";
        this.participants = null;
    }




    // Getters
    public String getQueueType() { return queueType; }

    public String getGameId() { return gameId; }


    public List<Participant> getParticipants() { return participants; }

    //Setters
    public void setQueueType(String queueType) { this.queueType = queueType; }
    public void setGameId(String gameId) { this.gameId = gameId; }

    public void setParticipants(List<Participant> participants) { this.participants = participants; }

}
