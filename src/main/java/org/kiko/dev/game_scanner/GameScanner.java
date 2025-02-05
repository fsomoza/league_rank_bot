package org.kiko.dev.game_scanner;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.mongodb.MongoException;
import com.mongodb.client.MongoCollection;
import com.mongodb.client.MongoCursor;
import com.mongodb.client.MongoDatabase;
import com.mongodb.client.model.Filters;
import com.mongodb.client.model.UpdateOptions;
import com.mongodb.client.model.Updates;
import com.mongodb.client.result.UpdateResult;
import net.dv8tion.jda.api.entities.Guild;
import net.dv8tion.jda.api.entities.MessageEmbed;
import net.dv8tion.jda.api.entities.channel.concrete.TextChannel;
import net.dv8tion.jda.api.interactions.components.buttons.Button;
import org.bson.Document;
import org.bson.conversions.Bson;
import org.kiko.dev.ConfigurationHolder;
import org.kiko.dev.ContextHolder;
import org.kiko.dev.adapters.MongoDbAdapter;
import org.kiko.dev.adapters.RiotApiAdapter;
import org.kiko.dev.dtos.CompletedGameInfo;
import org.kiko.dev.dtos.CurrentGameInfo;
import org.kiko.dev.dtos.Participant;
import org.kiko.dev.dtos.timeline.TimeLineDto;
import org.kiko.dev.gold_graph.GraphGenerator;

import java.util.*;
import java.util.List;

public class GameScanner {

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
            "CHALLENGER", 10
    );

    private static final Map<String, Integer> DIVISION_MAP = Map.of(
            "IV", 1,
            "III", 2,
            "II", 3,
            "I", 4
    );

    private MongoCollection<Document> champsCollection;

    private static final String SERVER_RANKS_COLLECTION = "serverRanks";
    private static final String GAMES_IN_PROGRESS_COLLECTION = "gamesInProgress";

    private final RiotApiAdapter riotApiAdapter;
    private final MongoDbAdapter mongoDbAdapter;
    private final String GUILD_ID;
    private final String CHANNEL_ID;

    public GameScanner() {
        this.riotApiAdapter = RiotApiAdapter.getInstance();
        this.mongoDbAdapter = MongoDbAdapter.getInstance();
        this.GUILD_ID = ConfigurationHolder.getGuildId();
        this.CHANNEL_ID = ConfigurationHolder.getChannelId();
        this.champsCollection = mongoDbAdapter.getDatabase().getCollection("champions");
    }

    /**
     * Checks which players are currently in-game or have just finished a game.
     * z
     *
     * @throws Exception if data retrieval or messaging fails.
     */
    public void checkWhoInGame() throws Exception {

        //TimeLineDto timeLineDto = riotApiAdapter.getTimeLine("EUW1_7199642701");
        Guild guild = ContextHolder.getGuild();

        TextChannel textChannel = guild.getTextChannelsByName("game_scanner", true).get(0);


        MongoDatabase database = mongoDbAdapter.getDatabase();
        MongoCollection<Document> serverRanksCollection = database.getCollection(SERVER_RANKS_COLLECTION + "-" + ContextHolder.getGuildId());
        MongoCollection<Document> gamesInProgressCollection = database.getCollection(GAMES_IN_PROGRESS_COLLECTION + "-" + ContextHolder.getGuildId());

        // Track players currently in a game
        handleCompletedGames(textChannel, gamesInProgressCollection);

        // Retrieve all players by Elo
        List<Document> allPlayers = fetchAllPlayersAndExcludeTheOnesInGame(serverRanksCollection, gamesInProgressCollection);

        // Map player puuid -> player name
        Map<String, String> playersMap = buildPlayersMap(allPlayers);

        // Check if players are currently in a game
        for (Document playerDoc : allPlayers) {
            String playerName = playerDoc.getString("name");
            String puuid = playerDoc.getString("puuid");
            if (playerName == null || puuid == null) continue;

            // Only check players not known to be in-game
            if (playersMap.containsKey(puuid)) {
                CurrentGameInfo currentGameInfo = riotApiAdapter.checkIfPlayerIsInGame(puuid, playersMap);

                //look for the game id in the games in progress collection
                Document gameDoc = null;

                if (currentGameInfo != null) {
                    gameDoc = gamesInProgressCollection.find(
                            new Document("id", currentGameInfo.getGameId())
                                    .append("onGoing", false)
                    ).first();
                }

                //TODO: QUICK HACK TO SKIP THE CUSTOM GAMES THAT DONT APPEAR IN THE MATCH HISTORY
                if (currentGameInfo != null && gameDoc == null && getQueueType(currentGameInfo.getQueueType()) != "UNKNOWN") {
                    handlePlayerInGame(currentGameInfo, textChannel, gamesInProgressCollection);
                }
            }
        }
    }


    /**
     * Handles games that have potentially completed and sends a completion message if so.
     * Returns a set of players still in ongoing games.
     */
    private Set<String> handleCompletedGames(TextChannel channel, MongoCollection<Document> gamesInProgressCollection) {

        Set<String> playersInGame = new HashSet<>();
        //TODO try cath needs to be inside the loop, so if one of the api calls fails, it will not stop the whole thing
        // also maybe just log the error inside searchGameId() or checkCompletedGame() and continue
        try (MongoCursor<Document> cursor = gamesInProgressCollection.find(new Document("onGoing", true)).iterator()) {
            while (cursor.hasNext()) {
                Document gameDoc = cursor.next();
                String gameId = gameDoc.getString("id");
                List<Document> participants = gameDoc.get("participants", List.class);

                if (participants == null || participants.isEmpty()) continue;

                String puuid = participants.get(0).getString("puuid");
                String foundGameId = riotApiAdapter.searchGameId(puuid, gameId);

                if (foundGameId != null) {
                    // Game completed
                    Set<String> participantPuuids = extractParticipantPuuids(participants);
                    CompletedGameInfo completedGameInfo = riotApiAdapter.checkCompletedGame(foundGameId);
                    TimeLineDto timeLineDto = riotApiAdapter.getTimeLine(foundGameId);
                    completedGameInfo.setQueueType(gameDoc.getString("queueType"));

                    // Use the new builder method
                    MessageEmbed embed = GameMessageBuilder.buildCompletedGameEmbed(
                            completedGameInfo,
                            participantPuuids,
                            champsCollection
                    );

                    // Create a button for requesting the gold graph.
                    // "goldGraph" is the custom ID that I will use to handle the button's interaction,
                    // and "Gold Graph" is the text label shown on the button.
                    Button goldGraphButton = Button.primary("goldGraph:" + foundGameId.split("_")[1], "Gold Graph");
                    Button dmgGraphButton = Button.primary("dmgGraph:" + foundGameId.split("_")[1], "Damage Graph");

                    channel.sendMessageEmbeds(embed)
                            .setActionRow(goldGraphButton,dmgGraphButton)
                            .setMessageReference(gameDoc.getString("messageId"))
                            .complete();

                    //TODO maybe implement a retry mechanism here in case the message is not sent

                    ObjectMapper mapper = new ObjectMapper();
                    // Serialize TimeLineDto to a JSON string
                    String timeLineJson = mapper.writeValueAsString(timeLineDto);
                    // Parse the JSON string into a MongoDB Document
                    Document timeLineDocument = Document.parse(timeLineJson);

                    String completedGameJson = mapper.writeValueAsString(completedGameInfo);
                    Document completedGameInfoDocument = Document.parse(completedGameJson);

                    // Update the game document to set onGoing to false
                    gamesInProgressCollection.updateOne(
                            new Document("id", gameId),
                            new Document("$set", new Document("onGoing", false)
                                    .append("timeLineDto", timeLineDocument)
                                    .append("completedGameInfo", completedGameInfoDocument))
                    );

                    //update the player rank

                    List<String> participantPuuidsList = new ArrayList<>(participantPuuids);

                    // Create a filter using the $in operator to match any puuid in the list
                    Bson filter = Filters.in("puuid", participantPuuidsList);
                    // retrieve the players
                    mongoDbAdapter.getDatabase().getCollection(SERVER_RANKS_COLLECTION + "-" + ContextHolder.getGuildId()).find(filter).forEach(player -> {
                        try {
                            updatePlayerRank(player.getString("puuid"), gameDoc.getString("queueType"), player.getString("encryptedSummonerId"));
                        } catch (Exception e) {
                            throw new RuntimeException(e);
                        }
                    });
                }
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
        return playersInGame;
    }

    /**
     * Handles a current game situation by notifying the channel and updating the DB.
     */
    private void handlePlayerInGame(CurrentGameInfo currentGameInfo, TextChannel channel,
                                    MongoCollection<Document> gamesInProgressCollection) throws Exception {

        List<Participant> participants = currentGameInfo.getParticipants();
        List<Document> participantDocs = new ArrayList<>();

        for (Participant participant : participants) {

            if (participant.isRegisteredPlayer()) {
                participantDocs.add(new Document("puuid", participant.getPuuid())
                        .append("championId", participant.getChampionId())
                        .append("playerName", participant.getPlayerName()));
            }
        }

        enrichCurrentGameInfo(currentGameInfo);

        // Use the new builder method for the ongoing match
        MessageEmbed embed = GameMessageBuilder.buildOngoingGameEmbed(currentGameInfo, champsCollection);

        channel.sendMessageEmbeds(embed)
                .queue(message -> {
                    //TODO maybe implement a retry mechanism here in case the message is not sent
                    // also need to think about possible race conditions and how to handle them

                    // This block is executed asynchronously once the message is sent
                    String messageId = message.getId();

                    // Define the filter to find the existing game document by gameId
                    Bson filter = Filters.eq("id", currentGameInfo.getGameId());

                    // Define the update operations:
                    // - Set or update the queueType and messageId
                    // - Add new participants to the participants array without duplicating existing ones
                    Bson update = Updates.combine(
                            Updates.set("queueType", getQueueType(currentGameInfo.getQueueType())),
                            Updates.set("messageId", messageId),
                            Updates.set("onGoing", true),
                            Updates.addEachToSet("participants", participantDocs)
                    );

                    try {
                        // Perform the update with upsert option
                        UpdateResult result = gamesInProgressCollection.updateOne(
                                filter,
                                update,
                                new UpdateOptions().upsert(true)
                        );

                        //log the success or perform further actions
                        System.out.println("Game document updated successfully. Matched: "
                                + result.getMatchedCount()
                                + ", Modified: " + result.getModifiedCount());

                    } catch (MongoException e) {
                        // Handle any errors that occurred during the update
                        e.printStackTrace();

                    }
                }, failure -> {
                    // Handle any errors that occurred when trying to send the message
                    failure.printStackTrace();
                });

    }

    /**
     * Fetch all players sorted by ELO.
     */
    private List<Document> fetchAllPlayersAndExcludeTheOnesInGame(
            MongoCollection<Document> serverRanksCollection,
            MongoCollection<Document> gamesInProgressCollection) {

        List<String> participantIdsToExclude = new ArrayList<>();

        gamesInProgressCollection.find(new Document("onGoing", true)).forEach(doc -> {
            List<Document> participants = doc.getList("participants", Document.class);
            if (participants != null) {
                participants.forEach(participant -> {
                    String puuid = participant.getString("puuid");
                    if (puuid != null && !puuid.isEmpty()) {
                        participantIdsToExclude.add(puuid);
                    }
                });
            }
        });

        // If participantIdsToExclude is empty, avoid using the $nin filter which can be inefficient
        if (participantIdsToExclude.isEmpty()) {
            return serverRanksCollection.find().into(new ArrayList<>());
        }

        return serverRanksCollection.find(Filters.nin("puuid", participantIdsToExclude))
                .into(new ArrayList<>());
    }

    private void updatePlayerRank(String puuid, String queueType, String encryptedSummonerId) throws Exception {
        int elo = 0;
        int wins = 0;
        int losses = 0;
        double winrate = 0.0;
        String rank = "JUEGA RANKEDS";

        Optional<RiotApiAdapter.LeagueEntry> optionalEntry;
        RiotApiAdapter.LeagueEntry entry;

        if (queueType.equals("RANKED_SOLO/DUO")) {
            optionalEntry = riotApiAdapter.getSoloQueueRank(encryptedSummonerId);
            entry = optionalEntry.get();
            rank = String.format("%s %s %d LP",
                    entry.getTier(),
                    entry.getRank(),
                    entry.getLeaguePoints());
            elo = computeElo(rank);
            wins = entry.getWins();
            losses = entry.getLosses();
            winrate = wins > 0 ? (double) wins / (wins + losses) * 100 : 0.0;
        } else {
            optionalEntry = riotApiAdapter.getFlexQueueRank(encryptedSummonerId);
            entry = optionalEntry.get();
            rank = String.format("%s %s %d LP",
                    entry.getTier(),
                    entry.getRank(),
                    entry.getLeaguePoints());
            elo = computeElo(rank);
            wins = entry.getWins();
            losses = entry.getLosses();
            winrate = wins > 0 ? (double) wins / (wins + losses) * 100 : 0.0;
        }

        // Get the database and collection
        MongoDatabase database = mongoDbAdapter.getDatabase();
        MongoCollection<Document> collection = database.getCollection(SERVER_RANKS_COLLECTION + "-" + ContextHolder.getGuildId());

        // Create the filter to find the document by "puuid"
        Document filter = new Document("puuid", puuid);

        // Create the update document with $set to update "rank" and "elo"
        Document update;

        if (queueType.equals("RANKED_SOLO/DUO")) {
            update = new Document("$set", new Document("soloQRank", rank).append("soloQElo", elo).append("soloQwins", wins).append("soloQlosses", losses).
                    append("soloQwinrate", Math.round(winrate * 100.0) / 100.0));  // Round to 2 decimal places
        } else {
            update = new Document("$set", new Document("flexQRank", rank).append("flexQElo", elo).append("flexQwins", wins).append("flexQlosses", losses).
                    append("flexQwinrate", Math.round(winrate * 100.0) / 100.0));  // Round to 2 decimal places
        }

        // Perform the update with upsert option
        collection.updateOne(
                filter,
                update,
                new UpdateOptions().upsert(true)
        );
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


    /**
     * Extract participant PUUIDs from a list of participant documents.
     */
    private Set<String> extractParticipantPuuids(List<Document> participants) {
        Set<String> participantPuuids = new HashSet<>();
        for (Document participant : participants) {
            participantPuuids.add(participant.getString("puuid"));
        }
        return participantPuuids;
    }

    /**
     * Constructs a puuid -> name map from the player documents.
     */
    private Map<String, String> buildPlayersMap(List<Document> players) {
        Map<String, String> playersMap = new HashMap<>();
        for (Document playerDoc : players) {
            String puuid = playerDoc.getString("puuid");
            if (puuid != null) {
                playersMap.put(puuid, playerDoc.getString("name"));
            }
        }
        return playersMap;
    }

    /**
     * Determine game queue type from queue ID.
     */
    private String getQueueType(String queueId) {
        switch (queueId) {
            case "420":
                return "RANKED_SOLO/DUO";
            case "440":
                return "RANKED_FLEX";
            case "100":
            case "450":
                return "ARAM";
            case "720":
                return "ARAM_CLASH";
            case "400":
                return "DRAFT_PICK";
            case "430":
                return "BLIND_PICK";
            default:
                return "UNKNOWN";
        }
    }

    private CurrentGameInfo enrichCurrentGameInfo(CurrentGameInfo currentGameInfo) throws Exception {
        String guildId = ContextHolder.getGuildId();
        MongoDatabase database = mongoDbAdapter.getDatabase();
        MongoCollection<Document> ranksCollection = database.getCollection(SERVER_RANKS_COLLECTION + "-" + guildId);

        String queueType = getQueueType(currentGameInfo.getQueueType());

        List<Participant> participants = currentGameInfo.getParticipants();

        for (Participant participant : participants) {
            if (participant.isRegisteredPlayer()) {
                Document player = ranksCollection.find(new Document("puuid", participant.getPuuid())).first();
                if (player != null) {
                    participant.setRank(queueType.equals("RANKED_FLEX") ? player.getString("flexQRank") : player.getString("soloQRank"));
                    participant.setLosses(queueType.equals("RANKED_FLEX") ? player.getInteger("flexQlosses") : player.getInteger("soloQlosses"));
                    participant.setWins(queueType.equals("RANKED_FLEX") ? player.getInteger("flexQwins") : player.getInteger("soloQwins"));
                    participant.setWinrate(queueType.equals("RANKED_FLEX") ? player.getDouble("flexQwinrate") : player.getDouble("soloQwinrate"));
                    ;
                }
            } else {

                participant.setPlayerName(riotApiAdapter.getByPuuid(participant.getPuuid()).getGameName());

                Optional<RiotApiAdapter.LeagueEntry> optionalEntry = queueType.equals("RANKED_FLEX") ? riotApiAdapter.getFlexQueueRank(participant.getSummonerId()) :
                        riotApiAdapter.getSoloQueueRank(participant.getSummonerId());
                if (optionalEntry.isPresent()) {
                    RiotApiAdapter.LeagueEntry entry = optionalEntry.get();
                    participant.setRank(String.format("%s %s %d LP",
                            entry.getTier(),
                            entry.getRank(),
                            entry.getLeaguePoints()));
                    participant.setLosses(entry.getLosses());
                    participant.setWins(entry.getWins());
                    int totalGames = entry.getWins() + entry.getLosses();
                    double winrate = totalGames > 0 ? (double) entry.getWins() / totalGames * 100 : 0.0;
                    participant.setWinrate(Math.round(winrate * 100.0) / 100.0);
                }
            }
        }

        return currentGameInfo;
    }
}
