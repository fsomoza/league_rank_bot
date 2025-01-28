package org.kiko.dev.dtos;

public class CompletedGameInfoParticipant {
    private String champion;

    private String puuid;

    private String championId;
    private String playerName;
    private String kda;
    private boolean win;
    private int teamId;

    private String teamPosition;

    private boolean registeredPlayer;

    public CompletedGameInfoParticipant() {
        this.champion = "";
        this.playerName = "";
        this.kda = "";
        this.teamId = 0;
        this.teamPosition = "";
        this.win = false;
        this.championId = "";
        this.registeredPlayer = false;
        this.puuid = "";
    }

    // Getters
    public String getChampion() { return champion; }
    public String getPlayerName() { return playerName; }
    public String getKda() { return kda; }
    public int getTeamId() { return teamId; }

    public boolean isWin() { return win; }
    public String getTeamPosition() { return teamPosition; }

    public String getChampionId() { return championId; }

    public boolean isRegisteredPlayer() { return registeredPlayer; }

    public String getPuuid() { return puuid; }

    //Setters
    public void setChampion(String champion) { this.champion = champion; }
    public void setPlayerName(String playerName) { this.playerName = playerName; }
    public void setKda(String kda) { this.kda = kda; }
    public void setTeamId(int teamId) { this.teamId = teamId; }
    public void setWin(boolean win) { this.win = win; }
    public void setTeamPosition(String teamPosition) { this.teamPosition = teamPosition; }

    public void setChampionId(String championId) { this.championId = championId; }

    public void setRegisteredPlayer(boolean registeredPlayer) { this.registeredPlayer = registeredPlayer; }
    public void setPuuid(String puuid) { this.puuid = puuid; }
}
