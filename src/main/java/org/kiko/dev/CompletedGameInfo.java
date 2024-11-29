package org.kiko.dev;

public class CompletedGameInfo {
    private String queueType;
    private String champion;
    private String playerName;
    private String gameId;
    private String kda;

    private boolean win;

    public CompletedGameInfo() {
        this.queueType = "";
        this.champion = "";
        this.playerName = "";
        this.gameId = "";
        this.kda = "";
        this.win = false;
    }

    // Getters
    public String getQueueType() { return queueType; }
    public String getChampion() { return champion; }
    public String getPlayerName() { return playerName; }
    public String getGameId() { return gameId; }
    public String getKda() { return kda; }
    public boolean getWin() { return win; }

    //Setters
    public void setQueueType(String queueType) { this.queueType = queueType; }
    public void setChampion(String champion) { this.champion = champion; }
    public void setPlayerName(String playerName) { this.playerName = playerName; }
    public void setGameId(String gameId) { this.gameId = gameId; }
    public void setKda(String kda) { this.kda = kda; }
    public void setWin(boolean win) { this.win = win; }
}
