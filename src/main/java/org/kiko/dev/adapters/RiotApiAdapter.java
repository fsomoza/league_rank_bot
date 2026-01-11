package org.kiko.dev.adapters;

import com.google.gson.Gson;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.reflect.TypeToken;
import org.kiko.dev.*;
import org.kiko.dev.dtos.*;
import org.kiko.dev.dtos.timeline.TimeLineDto;

import java.lang.reflect.Type;
import java.net.HttpURLConnection;
import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpHeaders;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.*;

public class RiotApiAdapter {

  private static final String BASE_URL = "https://europe.api.riotgames.com";
  private static final String ACCOUNT_BASE_URL = "https://euw1.api.riotgames.com";

  private static final String APP_LIMIT = "RIOT_APP";
  private final String RIOT_API_KEY;

  private final HttpClient client;
  private final Gson gson;

  // private final RateLimiter rateLimiter;
  private final SimpleRateLimiter simpleRateLimiter;

  // Private constructor to prevent instantiation
  private RiotApiAdapter() {
    this.client = HttpClient.newBuilder()
        .connectTimeout(Duration.ofSeconds(10))
        .build();
    this.gson = new Gson();
    RIOT_API_KEY = ConfigurationHolder.getProperty("riot.api.key");

    this.simpleRateLimiter = new SimpleRateLimiter();
  }

  // Holder class for lazy-loaded singleton instance
  private static class Holder {
    private static final RiotApiAdapter INSTANCE = new RiotApiAdapter();
  }

  // Public method to provide access to the singleton instance
  public static RiotApiAdapter getInstance() {
    return Holder.INSTANCE;
  }

  public AccountInfo getByPuuid(String puuid) throws Exception {
    String endpoint = String.format("/riot/account/v1/accounts/by-puuid/%s",
        URLEncoder.encode(puuid, StandardCharsets.UTF_8));

    while (true) {
      if (!simpleRateLimiter.canProceed(APP_LIMIT)) {
        simpleRateLimiter.awaitRateLimit(APP_LIMIT);
        continue;
      }

      HttpRequest request = HttpRequest.newBuilder()
          .uri(URI.create(BASE_URL + endpoint))
          .header("X-Riot-Token", RIOT_API_KEY)
          .GET()
          .build();

      HttpResponse<String> response = client.send(request,
          HttpResponse.BodyHandlers.ofString());

      // Update rate limit based on response
      simpleRateLimiter.updateRateLimit(APP_LIMIT, response);

      if (response.statusCode() == HttpURLConnection.HTTP_OK) {
        return gson.fromJson(response.body(), AccountInfo.class);
      } else if (response.statusCode() == 429) { // Rate limit hit
        HttpHeaders headers = response.headers();
        System.out.println("Rate limit exceeded in getByPuuid");
        System.out.println("Retry after " + headers.map().get("retry-after"));
        continue; // Will retry after waiting
      } else {
        handleErrorResponse(response);
        return null;
      }
    }
  }

  public TimeLineDto getTimeLine(String gameId) throws Exception {

    String endpoint = String.format("/lol/match/v5/matches/%s/timeline",
        URLEncoder.encode(gameId, StandardCharsets.UTF_8));

    while (true) {
      if (!simpleRateLimiter.canProceed(APP_LIMIT)) {
        simpleRateLimiter.awaitRateLimit(APP_LIMIT);
        continue;
      }

      HttpRequest request = HttpRequest.newBuilder()
          .uri(URI.create(BASE_URL + endpoint))
          .header("X-Riot-Token", RIOT_API_KEY)
          .GET()
          .build();

      HttpResponse<String> response = client.send(request,
          HttpResponse.BodyHandlers.ofString());

      // Update rate limit based on response
      simpleRateLimiter.updateRateLimit(APP_LIMIT, response);

      if (response.statusCode() == HttpURLConnection.HTTP_OK) {
        return gson.fromJson(response.body(), TimeLineDto.class);
      } else if (response.statusCode() == 429) { // Rate limit hit
        HttpHeaders headers = response.headers();
        System.out.println("Rate limit exceeded in getTimeLine");
        System.out.println("Retry after " + headers.map().get("retry-after"));
        continue; // Will retry after waiting
      } else {
        handleErrorResponse(response);
        return null;
      }
    }
  }

  public AccountInfo getPuuid(String name, String tagLine) throws Exception {

    String encodedName = URLEncoder.encode(name, StandardCharsets.UTF_8).replace("+", "%20");

    String endpoint = String.format("/riot/account/v1/accounts/by-riot-id/%s/%s",
        encodedName,
        URLEncoder.encode(tagLine, StandardCharsets.UTF_8));

    while (true) {
      if (!simpleRateLimiter.canProceed(APP_LIMIT)) {
        simpleRateLimiter.awaitRateLimit(APP_LIMIT);
        continue;
      }

      HttpRequest request = HttpRequest.newBuilder()
          .uri(URI.create(BASE_URL + endpoint))
          .header("X-Riot-Token", RIOT_API_KEY)
          .GET()
          .build();

      String apiKey = RIOT_API_KEY;

      HttpResponse<String> response = client.send(request,
          HttpResponse.BodyHandlers.ofString());

      // Update rate limit based on response
      simpleRateLimiter.updateRateLimit(APP_LIMIT, response);

      if (response.statusCode() == HttpURLConnection.HTTP_OK) {
        return gson.fromJson(response.body(), AccountInfo.class);
      } else if (response.statusCode() == 429) { // Rate limit hit
        continue; // Will retry after waiting
      } else {
        System.out.println("peta aqui");
        handleErrorResponse(response);
        return null;
      }
    }
  }

  public CurrentGameInfo checkIfPlayerIsInGame(String puuid, Map<String, String> playersMap) throws Exception {

    CurrentGameInfo currentGameInfo = new CurrentGameInfo();

    String endpoint = String.format("/lol/spectator/v5/active-games/by-summoner/%s",
        URLEncoder.encode(puuid, StandardCharsets.UTF_8));

    while (true) {
      if (!simpleRateLimiter.canProceed(APP_LIMIT)) {
        simpleRateLimiter.awaitRateLimit(APP_LIMIT);
        continue;
      }

      HttpRequest request = HttpRequest.newBuilder()
          .uri(URI.create(ACCOUNT_BASE_URL + endpoint))
          .header("X-Riot-Token", RIOT_API_KEY).timeout(Duration.ofSeconds(15))
          .GET()
          .build();

      HttpResponse<String> response = client.send(request,
          HttpResponse.BodyHandlers.ofString());

      // Update rate limit based on response
      simpleRateLimiter.updateRateLimit(APP_LIMIT, response);

      if (response.statusCode() == HttpURLConnection.HTTP_OK) {

        JsonObject jsonObject = gson.fromJson(response.body(), JsonObject.class);
        // Ensure you retrieve the string value for comparison

        currentGameInfo.setGameId(jsonObject.get("gameId").getAsString());
        currentGameInfo.setQueueType(jsonObject.get("gameQueueConfigId").getAsString());
        List<Participant> participants = new ArrayList<>();
        currentGameInfo.setParticipants(participants);
        int counter = 0;
        for (var participantElement : jsonObject.get("participants").getAsJsonArray()) {
          counter++;
          System.out.println(counter);

          JsonObject participant = participantElement.getAsJsonObject();
          System.out.println(participant.toString());
          
          Participant participantObj = new Participant();
          
          // Check if puuid is null (streamer mode / privacy enabled or bot)
          if (participant.get("puuid").isJsonNull()) {
            participantObj.setStreamerMode(true);
            participantObj.setPuuid(null);
            String riotId = participant.get("riotId").getAsString();
            boolean isBot = participant.get("bot").getAsBoolean();
            // If bot: true -> actual bot, if bot: false -> streamer mode player
            if (isBot) {
              participantObj.setPlayerName(riotId + " (Bot)");
            } else {
              participantObj.setPlayerName(riotId + " (Streamer Mode)");
            }
          } else {
            String participantPuuid = participant.get("puuid").getAsString();
            participantObj.setPuuid(participantPuuid);
            participantObj.setPlayerName(playersMap.get(participantPuuid));
            if (playersMap.containsKey(participantPuuid)) {
              participantObj.setRegisteredPlayer(true);
              // when we find a player in the game, we remove him from the map so we dont
              // iterate over him again
              playersMap.remove(participantPuuid);
            }
          }
          
          participantObj.setChampionId(participant.get("championId").getAsString());
          participantObj.setTeamId(participant.get("teamId").getAsLong());
          participants.add(participantObj);
          currentGameInfo.setParticipants(participants);

        }
        return currentGameInfo;
      } else if (response.statusCode() == 429) {
        HttpHeaders headers = response.headers();
        System.out.println("Rate limit exceeded");
        System.out.println("AAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAA");
        System.out.println("Retry after " + headers.map().get("retry-after"));
      } else {
        // handleErrorResponse(response);
        return null;
      }

    }
  }

  public CompletedGameInfo checkCompletedGame(String gameId) throws Exception {

    // TODO: need to cover casuistic where players are in opposite teams

    while (true) {
      if (!simpleRateLimiter.canProceed(APP_LIMIT)) {
        simpleRateLimiter.awaitRateLimit(APP_LIMIT);
        continue;
      }

      CompletedGameInfo completedGameInfo = new CompletedGameInfo();
      List<CompletedGameInfoParticipant> completedGameInfoParticipants = new ArrayList<>();
      completedGameInfo.setParticipants(completedGameInfoParticipants);

      // Encode the gameId to ensure it's safe for use in a URL
      String encodedGameId = URLEncoder.encode(gameId, StandardCharsets.UTF_8);

      // Complete the endpoint by adding '/ids' and the query parameters
      String endpoint = String.format("/lol/match/v5/matches/%s", encodedGameId);

      HttpRequest request = HttpRequest.newBuilder()
          .uri(URI.create(BASE_URL + endpoint))
          .header("X-Riot-Token", RIOT_API_KEY)
          .GET()
          .build();

      HttpResponse<String> response = client.send(request,
          HttpResponse.BodyHandlers.ofString());

      simpleRateLimiter.updateRateLimit(APP_LIMIT, response);

      if (response.statusCode() == HttpURLConnection.HTTP_OK) {
        JsonObject jsonObject = gson.fromJson(response.body(), JsonObject.class);
        JsonObject info = jsonObject.get("info").getAsJsonObject();
        completedGameInfo.setGameDuration(info.get("gameDuration").getAsLong());
        JsonArray participants = info.get("participants").getAsJsonArray();
        for (JsonElement participantElement : participants) {
          JsonObject participant = participantElement.getAsJsonObject();

          JsonObject player = participant.getAsJsonObject();
          CompletedGameInfoParticipant completedGameInfoParticipant = new CompletedGameInfoParticipant();
          completedGameInfoParticipant.setPuuid(participant.get("puuid").getAsString());
          completedGameInfoParticipant.setTeamId(participant.get("teamId").getAsInt());
          completedGameInfoParticipant.setPlayerName(player.get("riotIdGameName").getAsString());
          completedGameInfoParticipant.setChampion(player.get("championName").getAsString());
          ;
          completedGameInfoParticipant.setChampionId(player.get("championId").getAsString());
          completedGameInfoParticipant
              .setTotalDmgDealtToChampions(player.get("totalDamageDealtToChampions").getAsInt());
          String kills = player.get("kills").getAsString();
          String deaths = player.get("deaths").getAsString();
          String assists = player.get("assists").getAsString();
          completedGameInfoParticipant.setKda(kills + "/" + deaths + "/" + assists);
          completedGameInfoParticipant.setWin(player.get("win").getAsBoolean());
          completedGameInfoParticipant.setTeamPosition(player.get("teamPosition").getAsString());
          completedGameInfoParticipants.add(completedGameInfoParticipant);

        }
        return completedGameInfo;
      } else if (response.statusCode() == 429) {
        HttpHeaders headers = response.headers();
        System.out.println("Retry after " + headers.map().get("retry-after"));
      } else {
        // handleErrorResponse(response);
        System.out.println(response.body());
        return null;
      }
    }
  }

  public String searchGameId(String puuid, String gameId) throws Exception {

    while (true) {
      if (!simpleRateLimiter.canProceed(APP_LIMIT)) {
        simpleRateLimiter.awaitRateLimit(APP_LIMIT);
        continue;
      }

      // Encode the PUUID to ensure it's safe for use in a URL
      String encodedPuuid = URLEncoder.encode(puuid, StandardCharsets.UTF_8);

      // Complete the endpoint by adding '/ids' and the query parameters
      String endpoint = String.format("/lol/match/v5/matches/by-puuid/%s/ids?start=0&count=30", encodedPuuid);

      // Build the full URI
      HttpRequest request = HttpRequest.newBuilder()
          .uri(URI.create(BASE_URL + endpoint))
          .header("X-Riot-Token", RIOT_API_KEY)
          .GET()
          .build();

      HttpResponse<String> response = client.send(request,
          HttpResponse.BodyHandlers.ofString());

      simpleRateLimiter.updateRateLimit(APP_LIMIT, response);

      if (response.statusCode() == HttpURLConnection.HTTP_OK) {
        // Parse the response body as a JSON array
        JsonArray matches = gson.fromJson(response.body(), JsonArray.class);

        // Iterate through each match ID in the array
        for (JsonElement matchElement : matches) {

          String matchId = matchElement.getAsString();
          if (matchId.equalsIgnoreCase("EUW1_" + gameId)) {
            return matchId;
          }
        }
        return null;
      } else if (response.statusCode() == 429) { // Rate limit hit
        continue; // Will retry after waiting

      } else {
        // Handle non-200 responses appropriately
        System.err.println("Failed to fetch matches. HTTP Status Code: " + response.statusCode());
        // Optionally, you can throw an exception or handle it as per your application's
        // requirement
        return null;
      }

    }
  }

  public String getEncryptedSummonerId(String puuid) throws Exception {

    while (true) {
      if (!simpleRateLimiter.canProceed(APP_LIMIT)) {
        simpleRateLimiter.awaitRateLimit(APP_LIMIT);
        continue;
      }

      String endpoint = String.format("/lol/summoner/v4/summoners/by-puuid/%s",
          URLEncoder.encode(puuid, StandardCharsets.UTF_8));

      HttpRequest request = HttpRequest.newBuilder()
          .uri(URI.create(ACCOUNT_BASE_URL + endpoint))
          .header("X-Riot-Token", RIOT_API_KEY)
          .GET()
          .build();

      HttpResponse<String> response = client.send(request,
          HttpResponse.BodyHandlers.ofString());

      simpleRateLimiter.updateRateLimit(APP_LIMIT, response);

      if (response.statusCode() == HttpURLConnection.HTTP_OK) {
        JsonObject jsonObject = gson.fromJson(response.body(), JsonObject.class);
        return jsonObject.get("puuid").getAsString();
      } else if (response.statusCode() == 429) { // Rate limit hit
        continue; // Will retry after waiting

      } else {
        handleErrorResponse(response);
        return null;
      }
    }
  }

  public Optional<LeagueEntry> getSoloQueueRank(String puuid) throws Exception {

    while (true) {
      if (!simpleRateLimiter.canProceed(APP_LIMIT)) {
        simpleRateLimiter.awaitRateLimit(APP_LIMIT);
        continue;
      }

      String endpoint = String.format("/lol/league/v4/entries/by-puuid/%s",
          URLEncoder.encode(puuid, StandardCharsets.UTF_8));

      HttpRequest request = HttpRequest.newBuilder()
          .uri(URI.create(ACCOUNT_BASE_URL + endpoint))
          .header("X-Riot-Token", RIOT_API_KEY)
          .GET()
          .build();

      HttpResponse<String> response = client.send(request,
          HttpResponse.BodyHandlers.ofString());

      simpleRateLimiter.updateRateLimit(APP_LIMIT, response);

      if (response.statusCode() == HttpURLConnection.HTTP_OK) {
        Type listType = new TypeToken<List<LeagueEntry>>() {
        }.getType();
        List<LeagueEntry> entries = gson.fromJson(response.body(), listType);

        Optional<LeagueEntry> soloQueue = entries.stream()
            .filter(entry -> "RANKED_SOLO_5x5".equals(entry.getQueueType()))
            .findFirst();

        return soloQueue;
      } else if (response.statusCode() == 429) { // Rate limit hit
        HttpHeaders headers = response.headers();
        System.out.println("Rate limit exceeded in getSoloQueueRank");
        System.out.println("Retry after " + headers.map().get("retry-after"));
        continue; // Will retry after waiting
      } else {
        handleErrorResponse(response);
        return null;
      }
    }
  }

  public Optional<LeagueEntry> getFlexQueueRank(String puuid) throws Exception {

    while (true) {
      if (!simpleRateLimiter.canProceed(APP_LIMIT)) {
        simpleRateLimiter.awaitRateLimit(APP_LIMIT);
        continue;
      }

      String endpoint = String.format("/lol/league/v4/entries/by-puuid/%s",
          URLEncoder.encode(puuid, StandardCharsets.UTF_8));

      HttpRequest request = HttpRequest.newBuilder()
          .uri(URI.create(ACCOUNT_BASE_URL + endpoint))
          .header("X-Riot-Token", RIOT_API_KEY)
          .GET()
          .build();

      HttpResponse<String> response = client.send(request,
          HttpResponse.BodyHandlers.ofString());

      simpleRateLimiter.updateRateLimit(APP_LIMIT, response);

      if (response.statusCode() == HttpURLConnection.HTTP_OK) {
        Type listType = new TypeToken<List<LeagueEntry>>() {
        }.getType();
        List<LeagueEntry> entries = gson.fromJson(response.body(), listType);

        Optional<LeagueEntry> flexQueue = entries.stream()
            .filter(entry -> "RANKED_FLEX_SR".equals(entry.getQueueType()))
            .findFirst();

        return flexQueue;
      } else if (response.statusCode() == 429) { // Rate limit hit
        HttpHeaders headers = response.headers();
        System.out.println("Rate limit exceeded in getFlexQueueRank");
        System.out.println("Retry after " + headers.map().get("retry-after"));
        continue; // Will retry after waiting
      } else {
        handleErrorResponse(response);
        return null;
      }
    }
  }

  public List<LeagueEntry> getQueueRanks(String puuid) throws Exception {

    while (true) {
      if (!simpleRateLimiter.canProceed(APP_LIMIT)) {
        simpleRateLimiter.awaitRateLimit(APP_LIMIT);
        continue;
      }

      String endpoint = String.format("/lol/league/v4/entries/by-puuid/%s",
          URLEncoder.encode(puuid, StandardCharsets.UTF_8));

      HttpRequest request = HttpRequest.newBuilder()
          .uri(URI.create(ACCOUNT_BASE_URL + endpoint))
          .header("X-Riot-Token", RIOT_API_KEY)
          .GET()
          .build();

      HttpResponse<String> response = client.send(request,
          HttpResponse.BodyHandlers.ofString());

      simpleRateLimiter.updateRateLimit(APP_LIMIT, response);

      Map<String, String> ranks = new HashMap<>();
      ranks.put("RANKED_SOLO_5x5", "JUEGA RANKEDS");
      ranks.put("RANKED_FLEX_SR", "JUEGA RANKEDS");

      if (response.statusCode() == HttpURLConnection.HTTP_OK) {
        Type listType = new TypeToken<List<LeagueEntry>>() {
        }.getType();
        List<LeagueEntry> entries = gson.fromJson(response.body(), listType);

        return entries;
      } else if (response.statusCode() == 429) { // Rate limit hit
        continue; // Will retry after waiting
      } else {
        handleErrorResponse(response);
        return null;
      }
    }
  }

  // Helper method to handle non-200 responses
  private void handleErrorResponse(HttpResponse<String> response) throws Exception {
    int statusCode = response.statusCode();
    String responseBody = response.body();
    switch (statusCode) {
      case 401:
        throw new Exception("Unauthorized: Check your API key.");
      case 404:
        throw new Exception("Not Found: The requested resource could not be found.");
      case 429:
        throw new Exception("Rate Limited: You have exceeded the API rate limit.");
      default:
        throw new Exception("Error: Received status code " + statusCode + " with message: " + responseBody);
    }
  }

  // Static nested class for deserializing league entries
  public static class LeagueEntry {
    private String leagueId;
    private String summonerId;
    private String queueType;
    private String tier;
    private String rank;
    private int leaguePoints;
    private int wins;
    private int losses;
    private boolean hotStreak;
    private boolean veteran;
    private boolean freshBlood;
    private boolean inactive;

    // Getters
    public String getLeagueId() {
      return leagueId;
    }

    public String getSummonerId() {
      return summonerId;
    }

    public String getQueueType() {
      return queueType;
    }

    public String getTier() {
      return tier;
    }

    public String getRank() {
      return rank;
    }

    public int getLeaguePoints() {
      return leaguePoints;
    }

    public int getWins() {
      return wins;
    }

    public int getLosses() {
      return losses;
    }

    public boolean isHotStreak() {
      return hotStreak;
    }

    public boolean isVeteran() {
      return veteran;
    }

    public boolean isFreshBlood() {
      return freshBlood;
    }

    public boolean isInactive() {
      return inactive;
    }

    // Setters (if needed)
    public void setLeagueId(String leagueId) {
      this.leagueId = leagueId;
    }

    public void setSummonerId(String summonerId) {
      this.summonerId = summonerId;
    }

    public void setQueueType(String queueType) {
      this.queueType = queueType;
    }

    public void setTier(String tier) {
      this.tier = tier;
    }

    public void setRank(String rank) {
      this.rank = rank;
    }

    public void setLeaguePoints(int leaguePoints) {
      this.leaguePoints = leaguePoints;
    }

    public void setWins(int wins) {
      this.wins = wins;
    }

    public void setLosses(int losses) {
      this.losses = losses;
    }

    public void setHotStreak(boolean hotStreak) {
      this.hotStreak = hotStreak;
    }

    public void setVeteran(boolean veteran) {
      this.veteran = veteran;
    }

    public void setFreshBlood(boolean freshBlood) {
      this.freshBlood = freshBlood;
    }

    public void setInactive(boolean inactive) {
      this.inactive = inactive;
    }
  }

}
