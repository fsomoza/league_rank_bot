package org.kiko.dev;

public class CurrentGameInfo {
    private String queueType;
    private String champion;

    private String playerName;

    private String puuid;
    private String gameId;


    public CurrentGameInfo() {
        this.queueType = "";
        this.champion = "";
        this.gameId = "";
    }




    // Getters
    public String getQueueType() { return queueType; }
    public String getChampion() { return champion; }
    public String getGameId() { return gameId; }

    public String getPlayerName() { return playerName; }
    public String getPuuid() { return puuid; }

    //Setters
    public void setQueueType(String queueType) { this.queueType = queueType; }
    public void setChampion(String champion) { this.champion = champion; }
    public void setGameId(String gameId) { this.gameId = gameId; }

    public void setPlayerName(String playerName) { this.playerName = playerName; }

    public void setPuuid(String puuid) { this.puuid = puuid; }

}
