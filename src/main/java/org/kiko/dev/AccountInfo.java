package org.kiko.dev;

public class AccountInfo {
    private String name;
    private String tagLine;
    private String puuid;

    //constructor
    public AccountInfo(String name){
        this.name = name;
    }

    public AccountInfo() {
    }
    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
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
