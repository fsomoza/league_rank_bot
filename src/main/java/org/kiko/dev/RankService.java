package org.kiko.dev;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.mongodb.client.FindIterable;
import com.mongodb.client.MongoCollection;
import com.mongodb.client.MongoDatabase;
import com.mongodb.client.model.*;
import com.mongodb.client.result.UpdateResult;
import net.dv8tion.jda.api.EmbedBuilder;
import net.dv8tion.jda.api.JDA;
import net.dv8tion.jda.api.entities.Guild;
import net.dv8tion.jda.api.entities.Icon;
import net.dv8tion.jda.api.entities.MessageEmbed;
import net.dv8tion.jda.api.entities.emoji.RichCustomEmoji;
import net.dv8tion.jda.api.events.interaction.component.ButtonInteractionEvent;
import net.dv8tion.jda.api.requests.restaction.AuditableRestAction;
import net.dv8tion.jda.api.utils.FileUpload;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.Response;
import org.bson.Document;
import org.bson.conversions.Bson;
import org.kiko.dev.adapters.MongoDbAdapter;
import org.kiko.dev.adapters.RiotApiAdapter;
import org.kiko.dev.dtos.*;
import org.kiko.dev.dtos.timeline.FramesTimeLineDto;
import org.kiko.dev.dtos.timeline.TimeLineDto;
import org.kiko.dev.gold_graph.GraphGenerator;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.imageio.ImageIO;

import java.awt.*;
import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.net.URL;
import java.time.Instant;
import java.time.chrono.MinguoChronology;
import java.util.*;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;
import java.util.regex.Pattern;

public class RankService {

  private static final Map<String, Integer> TIER_MAP = Map.of(
      "IRON", 1,
      "BRONZE", 2,
      "SILVER", 3,
      "GOLD", 4,
      "PLATINUM", 5,
      "EMERALD", 6,
      "DIAMOND", 7,
      "MASTER", 8,
      "GRANDMASTER", 9,
      "CHALLENGER", 10);

  private static final Map<String, Integer> DIVISION_MAP = Map.of(
      "IV", 1,
      "III", 2,
      "II", 3,
      "I", 4);

  // Collection names
  private static final String SERVER_RANKS_COLLECTION = "serverRanks";
  private static final String GAMES_IN_PROGRESS_COLLECTION = "gamesInProgress";
  private static final String CHAMPIONS_COLLECTION = "champions";
  private static final String VERSION_COLLECTION = "dDragonVersion";

    // Guild and channel IDs – consider externalizing into configuration
  private final String GUILD_ID;
  private final String CHANNEL_ID;

  private final RiotApiAdapter riotApiAdapter;
  private final MongoDbAdapter mongoDbAdapter;
  private final JDA jda;

  public RankService(JDA jda) {
    this.riotApiAdapter = RiotApiAdapter.getInstance();
    this.mongoDbAdapter = MongoDbAdapter.getInstance();
    this.jda = jda;
    this.GUILD_ID = ConfigurationHolder.getGuildId();
    this.CHANNEL_ID = ConfigurationHolder.getChannelId();
  }

  protected String getVersionFromRiot() throws Exception {
    String urlString = "https://ddragon.leagueoflegends.com/api/versions.json";

    OkHttpClient client = new OkHttpClient();
    Response response = client.newCall(new Request.Builder().url(urlString).build()).execute();

    if (!response.isSuccessful()) {
      throw new IOException("error retrieving the vesion json");
    }

    String responseBody = response.body().string();
    ObjectMapper objectMapper = new ObjectMapper();
    JsonNode rootNode = objectMapper.readTree(responseBody);

    if (!rootNode.isArray()) {
      throw new Exception("response type of versions is not the expected one");
    }

    String version = rootNode.get(0).asText();

    return version;
  }

  public void doChecks() throws Exception {
    try {

      String dDragonVersionCollectionName = "dDragonVersion";

      MongoDatabase database = mongoDbAdapter.getDatabase();

      Document versionFilter = new Document("name", dDragonVersionCollectionName);
      // String version = rootNode.get(0).toString();

      Boolean versionCollectionExists = database.listCollections()
              .filter(versionFilter)
              .first() != null;

      String versionFromRiot = getVersionFromRiot();
      if (!versionCollectionExists) {
        database.createCollection(dDragonVersionCollectionName);
        MongoCollection<Document> collection = database.getCollection(dDragonVersionCollectionName);
        collection.insertOne(new Document().append("version", versionFromRiot));
      }else{
        MongoCollection<Document> versionCollection = database.getCollection(dDragonVersionCollectionName);
        Document versionDocument = versionCollection.find().first();
        String versionFromDb = versionDocument.get("version").toString();
        if(!versionFromRiot.equals(versionFromDb)){
          System.out.println("updating version number");
          Bson updates = Updates.combine(Updates.set("version", versionFromRiot));
          versionCollection.updateOne(versionDocument,updates);
        }
      }

      String championsCollectionName = "champions";
      Document championsCollectionFilter = new Document("name", championsCollectionName);

      Boolean championsCollectionExists = database.listCollections()
              .filter(championsCollectionFilter)
              .first() != null;
      // first check if the champions collection exists

      if (!championsCollectionExists) {
        System.out.println("Champions collection does not exist!");
        fetchAndStoreChampions();
        createCustomEmojiFromURL();
        updateChampionsWithEmojiIds();
      }else{

        MongoCollection<Document> versionCollection = database.getCollection(dDragonVersionCollectionName);
        Document versionDocument = versionCollection.find().first();
        String versionFromDb = versionDocument.get("version").toString();


        MongoCollection<Document> championsCollection = database.getCollection(championsCollectionName);
        String versionOnChampCollection = championsCollection.find().first().get("version").toString();

       if (!versionFromDb.equals(versionOnChampCollection)){

       }

        List<Champion> champions = fetchChampionsListFromRiotCdn();

      }
    }catch (Exception e){
      System.out.println(e);
    }

  }

  /**
   * Fetches and updates the player's rank based on their name and tagline.
   *
   * @param name    The player's name.
   * @param tagline The player's tagline.
   * @return A discord embed with the player's rank.
   * @throws Exception if invalid input or data retrieval fails.
   */
  public MessageEmbed getPlayerInformation(String name, String tagline) throws Exception {

    // TODO upgrade this funcionality to save more data from the player, like rank
    // on flex queue as well, wins/losses, most played champions,
    // winrates, kda, etc.

    if (name.isEmpty() || "#".equals(tagline)) {
      throw new IllegalArgumentException("Invalid format. Use: /rank <name> <tag>");
    }

    AccountInfo accountInfo = riotApiAdapter.getPuuid(name, tagline);

    List<RiotApiAdapter.LeagueEntry> entries = riotApiAdapter.getQueueRanks(accountInfo.getPuuid());

    savePlayerInformation(accountInfo.getPuuid(), accountInfo.getGameName(), accountInfo.getTagLine(), entries);
    return buildPlayerRankEmbed(accountInfo, entries);
  }

  public void updatePlayersInfo() throws Exception {
    MongoDatabase database = mongoDbAdapter.getDatabase();

    for (String collectionName : database.listCollectionNames()) {
      if (collectionName.contains(SERVER_RANKS_COLLECTION)) {
        ContextHolder.setGuildId(collectionName.split("-")[1]);
        MongoCollection<Document> collection = database.getCollection(collectionName);

        // Find all documents in the collection
        FindIterable<Document> documents = collection.find();

        // Iterate through each document
        for (Document doc : documents) {
          try {
            // Extract name and tagline from document
            String name = doc.getString("name");
            String tagline = doc.getString("tagline");

            // Update player information
            getPlayerInformation(name, tagline);

            // Optional: Add a small delay to avoid hitting rate limits
            Thread.sleep(1000);

          } catch (Exception e) {
            // Log the error but continue processing other documents
            System.err.println("Error updating player: " + doc.toJson() + "\nError: " + e.getMessage());
          }
        }
      }
    }
  }


  public List<Champion> fetchChampionsListFromRiotCdn(){

    MongoDatabase database = mongoDbAdapter.getDatabase();
    MongoCollection<Document> versionCollection = database.getCollection(VERSION_COLLECTION);
    Document versionDocument = versionCollection.find().first();
    String version = versionDocument.getString("version").toString();
    String url = "https://ddragon.leagueoflegends.com/cdn/" + version + "/data/en_US/champion.json";
    OkHttpClient client = new OkHttpClient();

    try (Response response = client.newCall(new Request.Builder().url(url).build()).execute()) {
      if (!response.isSuccessful()) {
        throw new IOException("Failed to fetch champion data. Response: " + response);
      }

      String responseBody = response.body().string();
      ObjectMapper objectMapper = new ObjectMapper();
      JsonNode rootNode = objectMapper.readTree(responseBody);
      JsonNode dataNode = rootNode.get("data");

      if (dataNode == null || !dataNode.isObject()) {
        throw new IOException("Invalid JSON: 'data' field is missing or not an object.");
      }
      Iterator<Map.Entry<String, JsonNode>> fields = dataNode.fields();
      List<Champion> champions =  new ArrayList<>();
      while (fields.hasNext()) {
        Map.Entry<String, JsonNode> entry = fields.next();
        Champion champion = new ObjectMapper().treeToValue(entry.getValue(), Champion.class);
        champions.add(champion);
      }

      return  champions;
    } catch (IOException e) {
        throw new RuntimeException(e);
    }
  }

  /**
   * Fetch champion data from Riot's CDN and store it in MongoDB.
   *
   * @throws IOException if network or parsing fails.
   */
  public void fetchAndStoreChampions() throws IOException {

    MongoDatabase database = mongoDbAdapter.getDatabase();
    MongoCollection<Document> versionCollection = database.getCollection(VERSION_COLLECTION);
    Document versionDocument = versionCollection.find().first();
    String version = versionDocument.getString("version").toString();

      MongoCollection<Document> collection = database.getCollection(CHAMPIONS_COLLECTION);
      collection.insertOne(new Document("version", version));

      List<Champion> champions = new ArrayList<>();
      champions = fetchChampionsListFromRiotCdn();
      for (Champion champion : champions) {
        // Create a filter that matches the document with the given id and where either
        // the 'name' or 'key' is different from the champion's current values.
        Document filter = new Document("id", champion.getId())
                .append("$or", Arrays.asList(
                        new Document("name", new Document("$ne", champion.getName())),
                        new Document("key", new Document("$ne", champion.getKey()))
                ));

        // Create an update document to set the specified fields
        Document update = new Document("$set", new Document("name", champion.getName())
                .append("key", champion.getKey()));

        // Set the upsert option to true to insert the document if it doesn't exist
        UpdateOptions options = new UpdateOptions().upsert(true);

        // Perform the update operation
        collection.updateOne(filter, update, options);
      }
  }

  /**
   * Builds an embed with a ranked player leaderboard.
   *
   * @return MessageEmbed for the leaderboard.
   */
  // Updated RankService methods
  public MessageEmbed getRankedPlayerListEmbed(String queueType) {
    MongoDatabase database = mongoDbAdapter.getDatabase();
    MongoCollection<Document> collection = database
        .getCollection(SERVER_RANKS_COLLECTION + "-" + ContextHolder.getGuildId());

    List<Document> players = collection.find().sort(Sorts.descending(queueType + "Elo")).into(new ArrayList<>());

    return buildRankedPlayerEmbed(players, queueType);
  }

  // ----------------------------------------------
  // PRIVATE HELPER METHODS
  // ----------------------------------------------

  /**
   * Saves or updates a player's rank information in MongoDB.
   */
  /**
   * Saves or updates a player's rank information in MongoDB.
   */
  private void savePlayerInformation(String puuid, String name, String tagline,
      List<RiotApiAdapter.LeagueEntry> playerRanks) {
    MongoDatabase database = mongoDbAdapter.getDatabase();
    MongoCollection<Document> collection = database
        .getCollection(SERVER_RANKS_COLLECTION + "-" + ContextHolder.getGuildId());

    int soloQelo = 0;
    int flexQelo = 0;
    int soloQwins = 0;
    int flexQwins = 0;
    int soloQlosses = 0;
    int flexQlosses = 0;
    double soloQwinrate = 0.0;
    double flexQwinrate = 0.0;
    String soloQRank = "JUEGA RANKEDS";
    String flexQRank = "JUEGA RANKEDS";

    for (RiotApiAdapter.LeagueEntry entry : playerRanks) {
      if (entry.getQueueType().equals("RANKED_SOLO_5x5")) {
        soloQRank = String.format("%s %s %d LP",
            entry.getTier(),
            entry.getRank(),
            entry.getLeaguePoints());
        soloQelo = computeElo(soloQRank);
        soloQwins = entry.getWins();
        soloQlosses = entry.getLosses();
        // Calculate solo queue winrate
        int totalSoloGames = soloQwins + soloQlosses;
        soloQwinrate = totalSoloGames > 0 ? (double) soloQwins / totalSoloGames * 100 : 0.0;
      }

      if (entry.getQueueType().equals("RANKED_FLEX_SR")) {
        flexQRank = String.format("%s %s %d LP",
            entry.getTier(),
            entry.getRank(),
            entry.getLeaguePoints());
        flexQelo = computeElo(flexQRank);
        flexQwins = entry.getWins();
        flexQlosses = entry.getLosses();
        // Calculate flex queue winrate
        int totalFlexGames = flexQwins + flexQlosses;
        flexQwinrate = totalFlexGames > 0 ? (double) flexQwins / totalFlexGames * 100 : 0.0;
      }
    }

    Document playerDoc = new Document("puuid", puuid)
        .append("name", name)
        .append("tagline", tagline)
        .append("soloQRank", soloQRank)
        .append("soloQElo", soloQelo)
        .append("soloQwins", soloQwins)
        .append("soloQlosses", soloQlosses)
        .append("soloQwinrate", Math.round(soloQwinrate * 100.0) / 100.0) // Round to 2 decimal places
        .append("flexQRank", flexQRank)
        .append("flexQElo", flexQelo)
        .append("flexQwins", flexQwins)
        .append("flexQlosses", flexQlosses)
        .append("flexQwinrate", Math.round(flexQwinrate * 100.0) / 100.0) // Round to 2 decimal places
        .append("timestamp", System.currentTimeMillis());

    collection.replaceOne(
        new Document("puuid", puuid),
        playerDoc,
        new ReplaceOptions().upsert(true));
  }

  public void generateGoldGraph(String gameId, ButtonInteractionEvent event) throws JsonProcessingException {

    MongoDatabase mongoDatabase = mongoDbAdapter.getDatabase();
    MongoCollection<Document> gamesInProgressCollection = mongoDatabase
        .getCollection(GAMES_IN_PROGRESS_COLLECTION + "-" + ContextHolder.getGuildId());
    Document gameDocument = gamesInProgressCollection.find(Filters.eq("id", gameId)).first();
    Document timeLineDocument = (Document) gameDocument.get("timeLineDto");
    String timeLineJson = timeLineDocument.toJson();
    ObjectMapper mapper = new ObjectMapper();
    TimeLineDto timeLineDto = mapper.readValue(timeLineJson, TimeLineDto.class);

    int blueTeamGold = 0;
    int redTeamGold = 0;

    int minutes_frames = timeLineDto.getInfo().getFrames().size();
    int[] blue_team_frames = new int[minutes_frames];
    int[] read_team_frames = new int[minutes_frames];

    for (int i = 0; i < blue_team_frames.length; i++) {
      FramesTimeLineDto frame = timeLineDto.getInfo().getFrames().get(i);

      for (int x = 1; x <= 10; x++) {
        if (x <= 5) {
          blueTeamGold = blueTeamGold + frame.getParticipantFrames().get(String.valueOf(x)).getTotalGold();
          continue;
        }
        redTeamGold = redTeamGold + frame.getParticipantFrames().get(String.valueOf(x)).getTotalGold();
      }

      blue_team_frames[i] = blueTeamGold;
      read_team_frames[i] = redTeamGold;

      blueTeamGold = 0;
      redTeamGold = 0;
    }

    System.out.println("fdsfsdfsd");

    // Assume blueTeam and redTeam arrays are available
    BufferedImage image = GraphGenerator.generateGoldGraph(blue_team_frames, read_team_frames);

    try (ByteArrayOutputStream baos = new ByteArrayOutputStream()) {
      // Write the image to a byte array in PNG format
      ImageIO.write(image, "png", baos);
      baos.flush();
      byte[] imageBytes = baos.toByteArray();

      // Use editOriginal instead of reply since we deferred
      event.getHook().sendMessage("Here is your gold graph:")
          .addFiles(FileUpload.fromData(imageBytes, "GOLD_GRAPH.png"))
          .setEphemeral(true).queue(); // This ensures only the requesting user sees the message

    } catch (IOException e) {
      e.printStackTrace();
      // Optionally reply with an error message
      event.reply("An error occurred while generating the graph.").queue();
    }
  }

  public void generateDmgGraph(String gameId, ButtonInteractionEvent event) throws JsonProcessingException {
    MongoDatabase mongoDatabase = mongoDbAdapter.getDatabase();
    MongoCollection<Document> gamesInProgressCollection = mongoDatabase
        .getCollection(GAMES_IN_PROGRESS_COLLECTION + "-" + ContextHolder.getGuildId());
    Document gameDocument = gamesInProgressCollection.find(Filters.eq("id", gameId)).first();
    Document completedGameInfoDocument = (Document) gameDocument.get("completedGameInfo");
    String completedGameInfoDocumentJson = completedGameInfoDocument.toJson();
    ObjectMapper mapper = new ObjectMapper();
    CompletedGameInfo completedGameInfo = mapper.readValue(completedGameInfoDocumentJson, CompletedGameInfo.class);

    List<CompletedGameInfoParticipant> participants = completedGameInfo.getParticipants();
    BufferedImage image = GraphGenerator.generateDmgGraph(participants);

    try (ByteArrayOutputStream baos = new ByteArrayOutputStream()) {
      // Write the image to a byte array in PNG format
      ImageIO.write(image, "png", baos);
      baos.flush();
      byte[] imageBytes = baos.toByteArray();

      // Use editOriginal instead of reply since we deferred
      event.getHook().sendMessage("Here is your damage graph")
          .addFiles(FileUpload.fromData(imageBytes, "DAMAGE_GRAPH.png"))
          .setEphemeral(true).queue(); // This ensures only the requesting user sees the mess

    } catch (IOException e) {
      e.printStackTrace();
      // Optionally reply with an error message
      event.reply("An error occurred while generating the graph.").queue();
    }

  }

  public void deletePlayer(String name, String tagline) throws Exception {
    // Get player PUUID from Riot API

    // Get database and collections
    MongoDatabase database = mongoDbAdapter.getDatabase();
    String guildId = ContextHolder.getGuildId();
    MongoCollection<Document> ranksCollection = database.getCollection(SERVER_RANKS_COLLECTION + "-" + guildId);
    MongoCollection<Document> gamesInProgressCollection = database
        .getCollection(GAMES_IN_PROGRESS_COLLECTION + "-" + guildId);

    Document player = ranksCollection.find(
        Filters.and(
            Filters.regex("name", "^" + Pattern.quote(name) + "$", "i"),
            Filters.regex("tagline", "^" + Pattern.quote(tagline) + "$", "i")))
        .first();

    if (player == null) {
      throw new Exception("El jugador no existe en la base de datos");
    }

    String puuid = player.getString("puuid");

    // Delete player from ranks collection
    ranksCollection.deleteOne(new Document("puuid", puuid));

    // Find and delete player from any games in progress
    Document gameQuery = new Document("participants",
        new Document("$elemMatch", new Document("puuid", puuid)));

    // First, find the games the player is in
    FindIterable<Document> games = gamesInProgressCollection.find(gameQuery);

    for (Document game : games) {
      List<Document> participants = game.getList("participants", Document.class);

      // Remove the player from participants
      participants.removeIf(participant -> participant.getString("puuid").equals(puuid));

      if (participants.isEmpty()) {
        // If no participants left, delete the whole game
        gamesInProgressCollection.deleteOne(new Document("_id", game.getObjectId("_id")));
      } else {
        // Update the game with the remaining participants
        gamesInProgressCollection.updateOne(
            new Document("_id", game.getObjectId("_id")),
            new Document("$set", new Document("participants", participants)));
      }
    }
  }


  public void deleteEmoji(){

    String guild2 = "1323016315841282180";
    String guild3 = "1323016358157615194";
    String guild4 = "1323016391573508168";
    AtomicReference<Guild> guild1 = new AtomicReference<>(jda.getGuildById("1323016231485440000"));
    List<RichCustomEmoji> emojiList = guild1.get().getEmojis();
   for (RichCustomEmoji em : emojiList){
     em.delete().complete();
     System.out.print("fdsafds");
   }

    AtomicReference<Guild> guild2Reference = new AtomicReference<>(jda.getGuildById(guild2));
     emojiList = guild2Reference.get().getEmojis();
    for (RichCustomEmoji em : emojiList){
      em.delete().complete();
    }


    AtomicReference<Guild> guild3Reference = new AtomicReference<>(jda.getGuildById(guild3));
    emojiList = guild3Reference.get().getEmojis();
    for (RichCustomEmoji em : emojiList){
      em.delete().complete();
    }
    AtomicReference<Guild> guild4Reference = new AtomicReference<>(jda.getGuildById(guild4));
    emojiList = guild4Reference.get().getEmojis();
    for (RichCustomEmoji em : emojiList){
      em.delete().complete();
    }


  }
  public void createCustomEmojiFromURL() {

    List<String> guildIds = Arrays.asList(
        "1323016231485440000", // Guild 1
        "1323016315841282180", // Guild 2
        "1323016358157615194", // Guild 3
        "1323016391573508168"  // Guild 4
    );

    int maxEmojisPerGuild = 50;
    int currentGuildIndex = 0;

    MongoDatabase database = mongoDbAdapter.getDatabase();
    MongoCollection<Document> serverRanksCollection = database.getCollection(CHAMPIONS_COLLECTION);
    MongoCollection<Document> versionCollection = database.getCollection(VERSION_COLLECTION);

    Document documentVersion = versionCollection.find().first();
    String version = documentVersion.get("version").toString();

    Guild currentGuild = jda.getGuildById(guildIds.get(currentGuildIndex));
    System.out.println("Guild Name: " + currentGuild.getName() + ", Guild ID: " + currentGuild.getId());

    List<Document> champions = serverRanksCollection.find().into(new ArrayList<>());

    for (int i = 0; i < champions.size(); i++) {
      // Skip the first document (version document)
      if (i == 0) {
        continue;
      }

      String championName = champions.get(i).getString("id");
      System.out.println(champions.get(i).getString("name"));

      try {
        // Check if emoji already exists in any of the guilds
        boolean emojiExists = false;
        for (String guildId : guildIds) {
          Guild g = jda.getGuildById(guildId);
          if (g != null && !g.getEmojisByName(championName, false).isEmpty()) {
            emojiExists = true;
            break;
          }
        }

        if (emojiExists) {
          continue; // Skip if emoji already exists
        }

        // Check if current guild is full and switch to next one
        while (currentGuild.getEmojis().size() >= maxEmojisPerGuild) {
          currentGuildIndex++;
          if (currentGuildIndex >= guildIds.size()) {
            System.err.println("All guilds are full! Cannot create more emojis.");
            return;
          }
          currentGuild = jda.getGuildById(guildIds.get(currentGuildIndex));
          System.out.println("Switching to guild: " + currentGuild.getName());
        }

        try (InputStream imageStream = new URL(
                "https://ddragon.leagueoflegends.com/cdn/" + version + "/img/champion/" + championName + ".png")
                .openStream()) {
          currentGuild.createEmoji(championName, Icon.from(imageStream)).complete();
          System.out.println("Created emoji: " + championName + " in guild: " + currentGuild.getName());
        }

      } catch (Exception e) {
        e.printStackTrace();
      }
    }
  }

  public void updateChampionsWithEmojiIds() {
    List<String> guildIds = Arrays.asList(
        "1323016231485440000", // Guild 1
        "1323016315841282180", // Guild 2
        "1323016358157615194", // Guild 3
        "1323016391573508168" // Guild 4
    );

    MongoDatabase database = mongoDbAdapter.getDatabase();
    MongoCollection<Document> championsCollection = database.getCollection(CHAMPIONS_COLLECTION);

    // Create a map to store champion name to emoji ID mapping
    Map<String, String> championEmojiMap = new HashMap<>();

    // Iterate through all guilds and collect emoji information
    for (String guildId : guildIds) {
      Guild guild = jda.getGuildById(guildId);
      if (guild != null) {
        System.out.println("Processing guild: " + guild.getName());

        List<RichCustomEmoji> emojis = guild.getEmojis();
        for (RichCustomEmoji emoji : emojis) {
          // Store the emoji ID mapped to the champion name
          championEmojiMap.put(emoji.getName(), emoji.getId());
        }
      }
    }

    // Update the champions collection with emoji IDs
    for (Map.Entry<String, String> entry : championEmojiMap.entrySet()) {
      String championName = entry.getKey();
      String emojiId = entry.getValue();

      // Update the document in MongoDB
      UpdateResult result = championsCollection.updateOne(
          Filters.eq("id", championName),
          Updates.set("emojiId", emojiId));

      if (result.getModifiedCount() > 0) {
        System.out.println("Updated champion " + championName + " with emoji ID: " + emojiId);
      } else {
        System.out.println("No champion found for name: " + championName);
      }
    }

    System.out.println("Finished updating champions with emoji IDs");
  }

  // private MessageEmbed buildOngoingGameEmbed(CurrentGameInfo currentGameInfo) {
  //
  // List<Participant> participants = currentGameInfo.getParticipants();
  //
  // EmbedBuilder embed = new EmbedBuilder();
  // embed.setColor(0x1F8B4C);
  // embed.setTitle("🚨 Partida en curso detectada!");
  // embed.setDescription("**Modo de juego:** " +
  // getQueueType(currentGameInfo.getQueueType()));
  //
  // // Build participants table
  // StringBuilder tableBuilder = new StringBuilder();
  // tableBuilder.append("```")
  // .append(String.format("%-20s %-10s%n", "Player", "Champion"))
  // .append(String.format("%-20s %-10s%n", "--------------------",
  // "----------"));
  //
  // for (Participant participant : participants) {
  // tableBuilder.append(
  // String.format("%-20s %-10s%n",
  // participant.getPlayerName(),
  // getChampionName(participant.getChampionId())));
  // }
  //
  // tableBuilder.append("```");
  // embed.addField("Jugadores en la partida", tableBuilder.toString(), false);
  //
  // String footerMessage =
  // getQueueType(currentGameInfo.getQueueType()).contains("ARAM")
  // ? "💡 Si no dejais que tiren los minions el nexo, sois unos sudorosos!"
  // : "💡 Si os stompean, recordad que siempre es jungle diff!";
  // embed.setFooter(footerMessage);
  // embed.setTimestamp(java.time.Instant.now());
  //
  // return embed.build();
  // }

  private String buildRankedPlayerTable(List<Document> players) {
    StringBuilder sb = new StringBuilder();
    sb.append("```\n");
    sb.append(String.format("%-5s %-25s %-20s %-5s\n", "Rank", "Player", "Rank", "ELO"));
    sb.append(
        String.format("%-5s %-25s %-20s %-5s\n", "----", "-------------------------", "--------------------", "----"));

    int position = 1;
    for (Document player : players) {
      String name = player.getString("name");
      String tagline = player.getString("tagline");
      String rank = player.getString("rank");
      int elo = player.getInteger("elo", 0);

      if (elo > 0) {
        sb.append(String.format("%-5d %-25s %-20s %-5d\n", position, name + "#" + tagline, rank, elo));
      } else {
        sb.append(String.format("%-5d %-25s %-20s\n", position, name + "#" + tagline, rank));
      }
      position++;
    }
    sb.append("```");
    return sb.toString();
  }

  private MessageEmbed buildRankedPlayerEmbed(List<Document> players, String queueType) {
    EmbedBuilder embed = new EmbedBuilder();
    embed.setTitle("🏆 " + (queueType.equals("soloQ") ? "Solo Queue" : "Flex Queue") + " Leaderboard");
    embed.setColor(Color.BLUE);

    String goldMedal = "\uD83E\uDD47";
    String silverMedal = "\uD83E\uDD48";
    String bronzeMedal = "\uD83E\uDD49";

    StringBuilder tableBuilder = new StringBuilder();
    tableBuilder.append("```");
    tableBuilder.append(String.format("%-5s %-25s %-20s%n", "Rank", "Player", "Rank"));
    tableBuilder
        .append(String.format("%-5s %-25s %-20s%n", "----", "-------------------------", "--------------------"));

    int position = 1;
    for (Document player : players) {
      String name = player.getString("name");
      String tagline = player.getString("tagline");
      String rank;
      if (queueType.equals("soloQ")) {
        rank = player.getString("soloQRank");
      } else {
        rank = player.getString("flexQRank");
      }
      String medal = (position == 1) ? goldMedal + " "
          : (position == 2) ? silverMedal + " " : (position == 3) ? bronzeMedal + " " : "";

      tableBuilder.append(String.format("%-5d %-25s %-20s%n%n", position, medal + name + "#" + tagline, rank));
      position++;
    }
    tableBuilder.append("```");
    embed.setDescription(tableBuilder.toString());
    embed.setFooter("Data fetched from " + queueType + " ranks collection • Updated");
    embed.setTimestamp(Instant.now());

    return embed.build();
  }

  private MessageEmbed buildPlayerRankEmbed(AccountInfo accountInfo, List<RiotApiAdapter.LeagueEntry> leagueEntries) {
    EmbedBuilder embed = new EmbedBuilder();
    embed.setTitle("🎮 Player Rank Information");
    embed.setColor(Color.BLUE);

    String playerIdentifier = accountInfo.getGameName() + "#" + accountInfo.getTagLine();

    StringBuilder contentBuilder = new StringBuilder();
    contentBuilder.append("```\n");
    contentBuilder.append("Player: ").append(playerIdentifier).append("\n");
    contentBuilder.append("─────────────────────────────\n");

    // Process each queue type
    for (RiotApiAdapter.LeagueEntry entry : leagueEntries) {
      String queueIcon = entry.getQueueType().equals("RANKED_SOLO_5x5") ? "🏆" : "👥";
      String queueName = entry.getQueueType().equals("RANKED_SOLO_5x5") ? "Solo Queue" : "Flex Queue";

      String rankInfo = String.format("%s %s: %s %s %d LP (%dW/%dL)\n",
          queueIcon,
          queueName,
          entry.getTier(),
          entry.getRank(),
          entry.getLeaguePoints(),
          entry.getWins(),
          entry.getLosses());

      contentBuilder.append(rankInfo).append("\n");
    }

    // Handle unranked case if no entries are found for a queue type
    if (leagueEntries.isEmpty()) {
      contentBuilder.append("🏆 Solo Queue: JUEGA RANKEDS\n");
      contentBuilder.append("👥 Flex Queue: JUEGA RANKEDS\n");
    }

    contentBuilder.append("```");
    embed.setDescription(contentBuilder.toString());

    // Add footer with timestamp
    embed.setFooter("Last updated");
    embed.setTimestamp(Instant.now());

    return embed.build();
  }

  /**
   * Compute ELO score from rank string.
   */
  private int computeElo(String rankString) {
    if (rankString == null || rankString.isEmpty()) {
      return 0;
    }

    String[] parts = rankString.split(" ");
    if (parts.length == 4) {
      // Format: TIER DIVISION LP <Units ignored>
      String tier = parts[0].toUpperCase();
      String division = parts[1].toUpperCase();
      int tierValue = TIER_MAP.getOrDefault(tier, 0);
      int divisionValue = DIVISION_MAP.getOrDefault(division, 0);
      int lpValue = parseIntSafe(parts[2]);
      return tierValue * 1000 + divisionValue * 100 + lpValue;
    } else if (parts.length == 3) {
      // Format: MASTER LP units (No division)
      String tier = parts[0].toUpperCase();
      int tierValue = TIER_MAP.getOrDefault(tier, 0);
      int lpValue = parseIntSafe(parts[2]);
      return tierValue * 1000 + lpValue;
    }
    return 0;
  }

  private int parseIntSafe(String str) {
    try {
      return Integer.parseInt(str);
    } catch (NumberFormatException e) {
      return 0;
    }
  }

}
