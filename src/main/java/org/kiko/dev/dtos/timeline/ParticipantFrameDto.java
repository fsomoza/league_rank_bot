package org.kiko.dev.dtos.timeline;

public class ParticipantFrameDto {
    private ChampionStatsDto championStats;
    private int currentGold;
    private DamageStatsDto damageStats;
    private int goldPerSecond;
    private int jungleMinionsKilled;
    private int level;
    private int minionsKilled;
    private int participantId;
    private PositionDto position;
    private int timeEnemySpentControlled;
    private int totalGold;
    private int xp;

    // Getters and Setters
    public ChampionStatsDto getChampionStats() {
        return championStats;
    }

    public void setChampionStats(ChampionStatsDto championStats) {
        this.championStats = championStats;
    }

    public int getCurrentGold() {
        return currentGold;
    }

    public void setCurrentGold(int currentGold) {
        this.currentGold = currentGold;
    }

    public DamageStatsDto getDamageStats() {
        return damageStats;
    }

    public void setDamageStats(DamageStatsDto damageStats) {
        this.damageStats = damageStats;
    }

    public int getGoldPerSecond() {
        return goldPerSecond;
    }

    public void setGoldPerSecond(int goldPerSecond) {
        this.goldPerSecond = goldPerSecond;
    }

    public int getJungleMinionsKilled() {
        return jungleMinionsKilled;
    }

    public void setJungleMinionsKilled(int jungleMinionsKilled) {
        this.jungleMinionsKilled = jungleMinionsKilled;
    }

    public int getLevel() {
        return level;
    }

    public void setLevel(int level) {
        this.level = level;
    }

    public int getMinionsKilled() {
        return minionsKilled;
    }

    public void setMinionsKilled(int minionsKilled) {
        this.minionsKilled = minionsKilled;
    }

    public int getParticipantId() {
        return participantId;
    }

    public void setParticipantId(int participantId) {
        this.participantId = participantId;
    }

    public PositionDto getPosition() {
        return position;
    }

    public void setPosition(PositionDto position) {
        this.position = position;
    }

    public int getTimeEnemySpentControlled() {
        return timeEnemySpentControlled;
    }

    public void setTimeEnemySpentControlled(int timeEnemySpentControlled) {
        this.timeEnemySpentControlled = timeEnemySpentControlled;
    }

    public int getTotalGold() {
        return totalGold;
    }

    public void setTotalGold(int totalGold) {
        this.totalGold = totalGold;
    }

    public int getXp() {
        return xp;
    }

    public void setXp(int xp) {
        this.xp = xp;
    }
}
