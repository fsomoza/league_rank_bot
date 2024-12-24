package org.kiko.dev;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.mongodb.MongoException;
import com.mongodb.client.MongoCollection;
import com.mongodb.client.MongoCursor;
import com.mongodb.client.MongoDatabase;
import com.mongodb.client.model.*;
import com.mongodb.client.result.UpdateResult;
import net.dv8tion.jda.api.EmbedBuilder;
import net.dv8tion.jda.api.JDA;
import net.dv8tion.jda.api.entities.Guild;
import net.dv8tion.jda.api.entities.MessageEmbed;
import net.dv8tion.jda.api.entities.channel.concrete.TextChannel;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.Response;
import org.bson.Document;
import org.bson.conversions.Bson;

import java.awt.*;
import java.io.IOException;
import java.util.*;
import java.util.List;

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
            "CHALLENGER", 10
    );

    private static final Map<String, Integer> DIVISION_MAP = Map.of(
            "IV", 1,
            "III", 2,
            "II", 3,
            "I", 4
    );

    // Collection names
    private static final String SERVER_RANKS_COLLECTION = "serverRanks";
    private static final String GAMES_IN_PROGRESS_COLLECTION = "gamesInProgress";
    private static final String CHAMPIONS_COLLECTION = "champions";

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

    /**
     * Fetches and updates the player's rank based on their name and tagline.
     *
     * @param name    The player's name.
     * @param tagline The player's tagline.
     * @return The player's rank.
     * @throws Exception if invalid input or data retrieval fails.
     */
    public String getPlayerRank(String name, String tagline) throws Exception {

        //TODO upgrade this funcionality to save more data from the player, like rank on flex queue as well, wins/losses, most played champions,
        // winrates, kda, etc.

        if (name.isEmpty() || "#".equals(tagline)) {
            throw new IllegalArgumentException("Invalid format. Use: /rank <name> <tag>");
        }

        AccountInfo accountInfo = riotApiAdapter.getPuuid(name, tagline);
        String encryptedSummonerId = riotApiAdapter.getEncryptedSummonerId(accountInfo.getPuuid());
        String rank = riotApiAdapter.getSoloQueueRank(encryptedSummonerId);

        //RiotApiAdapter.LeagueEntry playerInfo = riotApiAdapter.getSoloQueusdsdseRank(encryptedSummonerId);

        savePlayerRank(accountInfo.getPuuid(), accountInfo.getGameName(), accountInfo.getTagLine(), rank, encryptedSummonerId);
        return rank;
    }

    /**
     * Checks which players are currently in-game or have just finished a game.
     *
     * @throws Exception if data retrieval or messaging fails.
     */
    public void checkWhoInGame() throws Exception {
        Guild guild = ContextHolder.getGuild();
        //TextChannel channel = guild.getTextChannelById(CHANNEL_ID);
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
                if (currentGameInfo != null) {
                    handlePlayerInGame(currentGameInfo, textChannel, gamesInProgressCollection);
                }
            }
        }
    }

    /**
     * Fetch champion data from Riot's CDN and store it in MongoDB.
     *
     * @throws IOException if network or parsing fails.
     */
    public void fetchAndStoreChampions() throws IOException {
        String url = "https://ddragon.leagueoflegends.com/cdn/14.23.1/data/en_US/champion.json";
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

            MongoDatabase database = mongoDbAdapter.getDatabase();
            MongoCollection<Document> collection = database.getCollection(CHAMPIONS_COLLECTION);

            Iterator<Map.Entry<String, JsonNode>> fields = dataNode.fields();
            while (fields.hasNext()) {
                Map.Entry<String, JsonNode> entry = fields.next();
                Champion champion = new ObjectMapper().treeToValue(entry.getValue(), Champion.class);

                Document championDoc = new Document("id", champion.getKey())
                        .append("name", champion.getName());

                collection.replaceOne(
                        new Document("id", champion.getId()),
                        championDoc,
                        new ReplaceOptions().upsert(true)
                );
            }
        }
    }

    /**
     * Retrieves a ranked list of players in a formatted code block.
     *
     * @return String representation of ranked players.
     */
    public String getRankedPlayerList() {
        MongoDatabase database = mongoDbAdapter.getDatabase();
        MongoCollection<Document> collection = database.getCollection(SERVER_RANKS_COLLECTION);

        List<Document> players = collection.find().sort(Sorts.descending("elo")).into(new ArrayList<>());
        return buildRankedPlayerTable(players);
    }

    /**
     * Builds an embed with a ranked player leaderboard.
     *
     * @return MessageEmbed for the leaderboard.
     */
    public MessageEmbed getRankedPlayerListEmbed() {
        MongoDatabase database = mongoDbAdapter.getDatabase();
        MongoCollection<Document> collection = database.getCollection(SERVER_RANKS_COLLECTION + "-" + ContextHolder.getGuildId());

        List<Document> players = collection.find().sort(Sorts.descending("elo")).into(new ArrayList<>());

        return buildRankedPlayerEmbed(players);
    }

    // ----------------------------------------------
    // PRIVATE HELPER METHODS
    // ----------------------------------------------

    /**
     * Saves or updates a player's rank information in MongoDB.
     */
    private void savePlayerRank(String puuid, String name, String tagline, String rank, String encryptedSummonerId) {
        MongoDatabase database = mongoDbAdapter.getDatabase();
        MongoCollection<Document> collection = database.getCollection(SERVER_RANKS_COLLECTION + "-" +ContextHolder.getGuildId());

        int elo = computeElo(rank);
        Document playerDoc = new Document("puuid", puuid)
                .append("encryptedSummonerId", encryptedSummonerId)
                .append("name", name)
                .append("tagline", tagline)
                .append("rank", rank)
                .append("elo", elo)
                .append("timestamp", System.currentTimeMillis());

        collection.replaceOne(
                new Document("puuid", puuid),
                playerDoc,
                new ReplaceOptions().upsert(true)
        );
    }

    private void updatePlayerRank(String puuid, String rank) {

        // Get the database and collection
        MongoDatabase database = mongoDbAdapter.getDatabase();
        MongoCollection<Document> collection = database.getCollection(SERVER_RANKS_COLLECTION + "-" + ContextHolder.getGuildId());

        // Create the filter to find the document by "puuid"
        Document filter = new Document("puuid", puuid);

        // Create the update document with $set to update "rank" and "elo"
        Document update = new Document("$set", new Document("rank", rank).append("elo", computeElo(rank)));

        // Perform the update with upsert option
        collection.updateOne(
                filter,
                update,
                new UpdateOptions().upsert(true)
        );
    }


    /**
     * Handles games that have potentially completed and sends a completion message if so.
     * Returns a set of players still in ongoing games.
     */
    private Set<String> handleCompletedGames(TextChannel channel, MongoCollection<Document> gamesInProgressCollection) {
        Set<String> playersInGame = new HashSet<>();
        //TODO try cath needs to be inside the loop, so if one of the api calls fails, it will not stop the whole thing
        // also maybe just log the error inside searchGameId() or checkCompletedGame() and continue
        try (MongoCursor<Document> cursor = gamesInProgressCollection.find().iterator()) {
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
                    CompletedGameInfo completedGameInfo = riotApiAdapter.checkCompletedGame(foundGameId, participantPuuids);
                    completedGameInfo.setQueueType(gameDoc.getString("queueType"));

                    channel.sendMessageEmbeds(buildEmbedMessage(completedGameInfo))
                            .setMessageReference(gameDoc.getString("messageId"))
                            .queue(message -> {
                                //TODO maybe implement a retry mechanism here in case the message is not sent
                                // also need to think about possible race conditions and how to handle them
                                gamesInProgressCollection.deleteOne(new Document("id", gameId));
                            }, failure -> {
                                // Handle any errors that occurred when trying to send the message
                                failure.printStackTrace();
                            });

                    //TODO update the player rank

                    if(gameDoc.getString("queueType").contains("RANKED_SOLO/DUO")){
                        List<String> participantPuuidsList = new ArrayList<>(participantPuuids);

                        // Create a filter using the $in operator to match any puuid in the list
                        Bson filter = Filters.in("puuid", participantPuuidsList);
                        // retrieve the players
                        mongoDbAdapter.getDatabase().getCollection(SERVER_RANKS_COLLECTION + "-" + ContextHolder.getGuildId()).find(filter).forEach(player -> {
                            try {
                                String rank = riotApiAdapter.getSoloQueueRank(player.getString("encryptedSummonerId"));
                                updatePlayerRank(player.getString("puuid"), rank);
                            } catch (Exception e) {
                                throw new RuntimeException(e);
                            }
                        });

                    }




                }
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
        return playersInGame;
    }

    /**
     * Fetch all players sorted by ELO.
     */
    private List<Document> fetchAllPlayersAndExcludeTheOnesInGame(
            MongoCollection<Document> serverRanksCollection,
            MongoCollection<Document> gamesInProgressCollection) {

        List<String> participantIdsToExclude = new ArrayList<>();

        gamesInProgressCollection.find().forEach(doc -> {
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
     * Handles a current game situation by notifying the channel and updating the DB.
     */
    private void handlePlayerInGame(CurrentGameInfo currentGameInfo, TextChannel channel,
                                    MongoCollection<Document> gamesInProgressCollection) {

        List<Participant> participants = currentGameInfo.getParticipants();
        List<Document> participantDocs = new ArrayList<>();

        for (Participant participant : participants) {

            participantDocs.add(new Document("puuid", participant.getPuuid())
                    .append("championId", participant.getChampionId())
                    .append("playerName", participant.getPlayerName()));
        }

        channel.sendMessageEmbeds(buildOngoingGameEmbed(currentGameInfo, participants))
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
     * Extract participant PUUIDs from a list of participant documents.
     */
    private Set<String> extractParticipantPuuids(List<Document> participants) {
        Set<String> participantPuuids = new HashSet<>();
        for (Document participant : participants) {
            participantPuuids.add(participant.getString("puuid"));
        }
        return participantPuuids;
    }

    // ----------------------------------------------
    // MESSAGE AND EMBED BUILDERS
    // ----------------------------------------------

    private MessageEmbed buildEmbedMessage(CompletedGameInfo completedGameInfo) {
        EmbedBuilder embed = new EmbedBuilder();
        embed.setColor(completedGameInfo.getWin() ? Color.GREEN : Color.RED);
        embed.setTitle("🎉 Partida Finalizada!");
        embed.setDescription("**Modo de juego:** " + completedGameInfo.getQueueType());
        embed.addField("Resultado", completedGameInfo.getWin() ? "🏆 VICTORIA" : "💀 DERROTA", true);

        // Build players table
        StringBuilder tableBuilder = new StringBuilder();
        tableBuilder.append("```")
                .append(String.format("%-20s %-15s %-5s%n", "Jugador", "Campeón", "KDA"))
                .append(String.format("%-20s %-15s %-5s%n", "--------------------", "---------------", "----"));

        for (CompletedGameInfoParticipant participant : completedGameInfo.getParticipants()) {
            tableBuilder.append(
                    String.format("%-20s %-15s %-5s%n",
                            participant.getPlayerName(),
                            participant.getChampion(),
                            participant.getKda()));
        }

        tableBuilder.append("```");
        embed.addField("Jugadores en la partida", tableBuilder.toString(), false);

        embed.setFooter("💡 " + getFooterMessageForCompletedGame(completedGameInfo));
        embed.setTimestamp(java.time.Instant.now());

        return embed.build();
    }

    private MessageEmbed buildOngoingGameEmbed(CurrentGameInfo currentGameInfo, List<Participant> participants) {
        EmbedBuilder embed = new EmbedBuilder();
        embed.setColor(0x1F8B4C);
        embed.setTitle("🚨 Partida en curso detectada!");
        embed.setDescription("**Modo de juego:** " + getQueueType(currentGameInfo.getQueueType()));

        // Build participants table
        StringBuilder tableBuilder = new StringBuilder();
        tableBuilder.append("```")
                .append(String.format("%-20s %-10s%n", "Player", "Champion"))
                .append(String.format("%-20s %-10s%n", "--------------------", "----------"));

        for (Participant participant : participants) {
            tableBuilder.append(
                    String.format("%-20s %-10s%n",
                            participant.getPlayerName(),
                            getChampionName(participant.getChampionId())));
        }

        tableBuilder.append("```");
        embed.addField("Jugadores en la partida", tableBuilder.toString(), false);

        String footerMessage = getQueueType(currentGameInfo.getQueueType()).contains("ARAM")
                ? "💡 Si no dejais que tiren los minions el nexo, sois unos sudorosos!"
                : "💡 Si os stompean, recordad que siempre es jungle diff!";
        embed.setFooter(footerMessage);
        embed.setTimestamp(java.time.Instant.now());

        return embed.build();
    }

    private String buildRankedPlayerTable(List<Document> players) {
        StringBuilder sb = new StringBuilder();
        sb.append("```\n");
        sb.append(String.format("%-5s %-25s %-20s %-5s\n", "Rank", "Player", "Rank", "ELO"));
        sb.append(String.format("%-5s %-25s %-20s %-5s\n", "----", "-------------------------", "--------------------", "----"));

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

    private MessageEmbed buildRankedPlayerEmbed(List<Document> players) {
        EmbedBuilder embed = new EmbedBuilder();
        embed.setTitle("🏆 Ranked Players Leaderboard");
        embed.setColor(Color.BLUE);

        String goldMedal = "\uD83E\uDD47";
        String silverMedal = "\uD83E\uDD48";
        String bronzeMedal = "\uD83E\uDD49";

        StringBuilder tableBuilder = new StringBuilder();
        tableBuilder.append("```");
        tableBuilder.append(String.format("%-5s %-25s %-20s%n", "Rank", "Player", "Rank"));
        tableBuilder.append(String.format("%-5s %-25s %-20s%n", "----", "-------------------------", "--------------------"));

        int position = 1;
        for (Document player : players) {
            String name = player.getString("name");
            String tagline = player.getString("tagline");
            String rank = player.getString("rank");
            String medal = (position == 1) ? goldMedal + " " :
                    (position == 2) ? silverMedal + " " :
                            (position == 3) ? bronzeMedal + " " : "";

            tableBuilder.append(String.format("%-5d %-25s %-20s%n%n", position, medal + name + "#" + tagline, rank));
            position++;
        }
        tableBuilder.append("```");
        embed.setDescription(tableBuilder.toString());
        embed.setFooter("Data fetched from the server ranks collection.");
        embed.setTimestamp(java.time.Instant.now());

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

    /**
     * Retrieve champion name by ID from the database.
     */
    private String getChampionName(String id) {
        MongoDatabase database = mongoDbAdapter.getDatabase();
        MongoCollection<Document> collection = database.getCollection(CHAMPIONS_COLLECTION);
        Document champion = collection.find(new Document("id", id)).first();
        return champion != null ? champion.getString("name") : "Unknown Champion";
    }

    /**
     * Footer message for completed games based on game mode and result.
     */
    private String getFooterMessageForCompletedGame(CompletedGameInfo completedGameInfo) {
        String queueType = completedGameInfo.getQueueType().toUpperCase();
        boolean win = completedGameInfo.getWin();

        switch (queueType) {
            case "ARAM":
                return win ? "El putísimo amo, maestro de todos los campeones, el Faker de los ARAMs! 🏅"
                        : "Mala suerte, no te han sonreído los dados. 🎲";
            case "RANKED_SOLO/DUO":
                return win ? "Da gracias por esos LP's que buena falta te hacen. 📈"
                        : "Recuerda, ¡JUNGLE DIFF! 🌿";
            case "RANKED_FLEX":
                return win ? "EL terror de las Flex, ¡T1 en su prime! 🏆"
                        : "Damian no podía rotar, qué le vamos a hacer. 🤷‍♂️";
            default:
                return "¡GG! 🎮";
        }
    }
}
