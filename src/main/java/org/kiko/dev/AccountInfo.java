package org.kiko.dev;

public class AccountInfo {
    private String gameName;
    private String tagLine;
    private String puuid;

    //constructor
    public AccountInfo(String gameName){
        this.gameName = gameName;
    }

    public AccountInfo() {
    }
    public String getGameName() {
        return gameName;
    }

    public void setGameName(String gameName) {
        this.gameName = gameName;
    }

    public String getTagLine() {
        return tagLine;
    }

    public void setTagLine(String tagLine) {
        this.tagLine = tagLine;
    }

    public String getPuuid() {
        return puuid;
    }

    public void setPuuid(String puuid) {
        this.puuid = puuid;
    }
}
