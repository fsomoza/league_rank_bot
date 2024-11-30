package org.kiko.dev;

public class Participant {
    private String puuid;
    private String championId;

    private String playerName;

    public Participant() {
        this.puuid = "";
        this.championId = "";
        this.playerName = "";
    }

    // Getters
    public String getPuuid() { return puuid; }
    public String getChampionId() { return championId; }
    public String getPlayerName() { return playerName; }

    // Setters
    public void setPuuid(String puuid) { this.puuid = puuid; }
    public void setChampionId(String championId) { this.championId = championId; }
    public void setPlayerName(String playerName) { this.playerName = playerName; }
}
