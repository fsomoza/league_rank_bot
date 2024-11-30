package org.kiko.dev;

public class CompletedGameInfoParticipant {
    private String champion;
    private String playerName;
    private String kda;

    public CompletedGameInfoParticipant() {
        this.champion = "";
        this.playerName = "";
        this.kda = "";
    }

    // Getters
    public String getChampion() { return champion; }
    public String getPlayerName() { return playerName; }
    public String getKda() { return kda; }

    //Setters
    public void setChampion(String champion) { this.champion = champion; }
    public void setPlayerName(String playerName) { this.playerName = playerName; }
    public void setKda(String kda) { this.kda = kda; }
}
