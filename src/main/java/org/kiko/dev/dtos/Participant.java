package org.kiko.dev.dtos;

public class Participant {
    private String puuid;
    private String championId;

    private String rank;

    private int wins;
    private int losses;

    private double winrate;

    private String playerName;

    private boolean registeredPlayer;

    private String summonerId;

    private long teamId;

    public Participant() {
        this.puuid = "";
        this.championId = "";
        this.playerName = "";
        this.registeredPlayer = false;
        this.wins = 0;
        this.losses = 0;
        this.winrate = 0.0;
        this.summonerId = "";
        this.rank = "";
        this.teamId = 0;
    }

    // Getters
    public String getPuuid() { return puuid; }
    public String getChampionId() { return championId; }
    public String getPlayerName() { return playerName; }

    public boolean isRegisteredPlayer() { return registeredPlayer; }

    public String getSummonerId() { return summonerId; }

    public String getRank() { return rank; }

    public int getWins() { return wins; }
    public int getLosses() { return losses; }

    public double getWinrate() { return winrate; }

    public long getTeamId() { return teamId; }

    // Setters
    public void setPuuid(String puuid) { this.puuid = puuid; }
    public void setChampionId(String championId) { this.championId = championId; }
    public void setPlayerName(String playerName) { this.playerName = playerName; }
    public void setRegisteredPlayer(boolean registeredPlayer) { this.registeredPlayer = registeredPlayer; }

    public void setRank(String rank) { this.rank = rank; }

    public void setWins(int wins) { this.wins = wins; }
    public void setLosses(int losses) { this.losses = losses; }

    public void setWinrate(double winrate) { this.winrate = winrate; }

    public void setSummonerId(String summonerId) { this.summonerId = summonerId; }

    public void setTeamId(long teamId) { this.teamId = teamId; }
}
