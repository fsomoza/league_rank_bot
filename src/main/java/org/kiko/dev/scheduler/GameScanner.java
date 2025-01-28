package org.kiko.dev.scheduler;

import com.mongodb.MongoException;
import com.mongodb.client.MongoCollection;
import com.mongodb.client.MongoCursor;
import com.mongodb.client.MongoDatabase;
import com.mongodb.client.model.Filters;
import com.mongodb.client.model.UpdateOptions;
import com.mongodb.client.model.Updates;
import com.mongodb.client.result.UpdateResult;
import net.dv8tion.jda.api.EmbedBuilder;
import net.dv8tion.jda.api.entities.Guild;
import net.dv8tion.jda.api.entities.MessageEmbed;
import net.dv8tion.jda.api.entities.channel.concrete.TextChannel;
import org.bson.Document;
import org.bson.conversions.Bson;
import org.kiko.dev.ConfigurationHolder;
import org.kiko.dev.ContextHolder;
import org.kiko.dev.adapters.MongoDbAdapter;
import org.kiko.dev.adapters.RiotApiAdapter;
import org.kiko.dev.dtos.CompletedGameInfo;
import org.kiko.dev.dtos.CompletedGameInfoParticipant;
import org.kiko.dev.dtos.CurrentGameInfo;
import org.kiko.dev.dtos.Participant;

import java.awt.*;
import java.time.Instant;
import java.util.*;
import java.util.List;
import java.util.stream.Collectors;

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
     *z
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

                if(currentGameInfo != null){
                    gameDoc  = gamesInProgressCollection.find(
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
                    completedGameInfo.setQueueType(gameDoc.getString("queueType"));

                    channel.sendMessageEmbeds(buildEmbedMessage(completedGameInfo, participantPuuids))
                            .setMessageReference(gameDoc.getString("messageId"))
                            .complete();

                    //TODO maybe implement a retry mechanism here in case the message is not sent

                    // Update the game document to set onGoing to false
                    gamesInProgressCollection.updateOne(
                            new Document("id", gameId),
                            new Document("$set", new Document("onGoing", false))
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

            if(participant.isRegisteredPlayer()){
                participantDocs.add(new Document("puuid", participant.getPuuid())
                        .append("championId", participant.getChampionId())
                        .append("playerName", participant.getPlayerName()));
            }
        }

        enrichCurrentGameInfo(currentGameInfo);

        channel.sendMessageEmbeds(buildOngoingGameEmbed(currentGameInfo))
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

        if(queueType.equals("RANKED_SOLO/DUO")) {
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
        }else {
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

        if(queueType.equals("RANKED_SOLO/DUO")){
            update = new Document("$set", new Document("soloQRank", rank).append("soloQElo", elo).append("soloQwins", wins).append("soloQlosses", losses).
                    append("soloQwinrate", Math.round(winrate * 100.0) / 100.0));  // Round to 2 decimal places
        }else{
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
            if(participant.isRegisteredPlayer()){
                Document player = ranksCollection.find(new Document("puuid", participant.getPuuid())).first();
                if(player != null){
                    participant.setRank(queueType.equals("RANKED_FLEX") ? player.getString("flexQRank") : player.getString("soloQRank"));
                    participant.setLosses(queueType.equals("RANKED_FLEX") ? player.getInteger("flexQlosses") : player.getInteger("soloQlosses"));
                    participant.setWins(queueType.equals("RANKED_FLEX") ? player.getInteger("flexQwins") : player.getInteger("soloQwins"));
                    participant.setWinrate(queueType.equals("RANKED_FLEX") ? player.getDouble("flexQwinrate") : player.getDouble("soloQwinrate"));;
                }
            }else{

                participant.setPlayerName(riotApiAdapter.getByPuuid(participant.getPuuid()).getGameName());

                Optional<RiotApiAdapter.LeagueEntry> optionalEntry = queueType.equals("RANKED_FLEX") ? riotApiAdapter.getFlexQueueRank(participant.getSummonerId()) :
                        riotApiAdapter.getSoloQueueRank(participant.getSummonerId());
                if(optionalEntry.isPresent()){
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

    private MessageEmbed buildEmbedMessage(CompletedGameInfo completedGameInfo, Set<String> participantPuuids) {

        MongoCollection<Document> championsCollection = this.champsCollection;



        completedGameInfo.getParticipants().stream().forEach(
                participant -> participant.setRegisteredPlayer(participantPuuids.contains(participant.getPuuid()))
        );


        // Find the first registered player and determine if they won
        boolean registeredPlayerWon = completedGameInfo.getParticipants().stream()
                .filter(participant -> participant.isRegisteredPlayer())
                .findFirst()
                .map(participant -> participant.isWin())
                .orElse(false);

        // Separate participants by team
        List<CompletedGameInfoParticipant> blueTeam = completedGameInfo.getParticipants().stream()
                .filter(p -> p.getTeamId() == 100)
                .collect(Collectors.toList());
        List<CompletedGameInfoParticipant> redTeam = completedGameInfo.getParticipants().stream()
                .filter(p -> p.getTeamId() == 200)
                .collect(Collectors.toList());

        // Determine which side won
        boolean blueTeamWin = blueTeam.stream().anyMatch(CompletedGameInfoParticipant::isWin);
        boolean redTeamWin  = redTeam.stream().anyMatch(CompletedGameInfoParticipant::isWin);

        String blueTeamResult = blueTeamWin ? "Win" : "Defeat";
        String redTeamResult  = redTeamWin  ? "Win" : "Defeat";

        // Sum kills for scoreboard: e.g., "22 - 17"
        int[] blueTotals = sumTeamKda(blueTeam);
        int[] redTotals  = sumTeamKda(redTeam);

        int blueKills = blueTotals[0];
        int redKills  = redTotals[0];

        // Build column fields for the embed
        //  -- Column 1: Blue Summoner Names
        //  -- Column 2: KDA strings (blue vs red)
        //  -- Column 3: Red Summoner Names

        StringBuilder blueTeamNames = new StringBuilder();
        StringBuilder kdaColumn     = new StringBuilder();
        StringBuilder redTeamNames  = new StringBuilder();

        // We'll iterate over the maximum size of either team
        int maxSize = Math.max(blueTeam.size(), redTeam.size());
        for (int i = 0; i < maxSize; i++) {
            CompletedGameInfoParticipant bp = (i < blueTeam.size()) ? blueTeam.get(i) : null;
            CompletedGameInfoParticipant rp = (i < redTeam.size()) ? redTeam.get(i) : null;

            // --- Blue side
            if (bp != null) {
                Document champion = championsCollection.find(new Document("key", bp.getChampionId())).first();
                blueTeamNames.append("<:"+ champion.getString("id") +":"+ champion.getString("emojiId") +"> | ");
                if (bp.isRegisteredPlayer()) {
                    blueTeamNames.append("__**").append(bp.getPlayerName()).append("**__");
                } else {
                    blueTeamNames.append(bp.getPlayerName());
                }
                blueTeamNames.append("\n");
            } else {
                blueTeamNames.append("\n"); // Keep line alignment if teams are uneven
            }

            // --- Middle KDA (combine both sides into one line, or just do Blue KDA + " | " + Red KDA)
            String blueKda = (bp != null) ? bp.getKda() : "";
            String redKda  = (rp != null) ? rp.getKda() : "";
            // Example: "5/8/3   12/3/12"
            kdaColumn.append(String.format("%-7s ⚔️ %-7s", blueKda, redKda)).append("\n");

            // --- Red side
            if (rp != null) {
                Document champion = championsCollection.find(new Document("key", rp.getChampionId())).first();
                redTeamNames.append("<:"+ champion.getString("id") +":"+ champion.getString("emojiId") +"> | ");
                if (rp.isRegisteredPlayer()) {
                    redTeamNames.append("__**").append(rp.getPlayerName()).append("**__");
                } else {
                    redTeamNames.append(rp.getPlayerName());
                }
                redTeamNames.append("\n");
            } else {
                redTeamNames.append("\n");
            }
        }

        // Now build the embed
        EmbedBuilder embed = new EmbedBuilder();


        List<CompletedGameInfoParticipant> participants = completedGameInfo.getParticipants();
        String map = "Summoner's Rift";
        String queueType = completedGameInfo.getQueueType();
        if(queueType.contains("ARAM")){
            map = "Bridge of progress";
        }

        // Build title with registered players
        StringBuilder titleBuilder = new StringBuilder("🎉 Partida Finalizada! " + completedGameInfo.getQueueType() + " | " + map
                + " | " + tranformSecondsToMMSS(completedGameInfo.getGameDuration()) + " | ");
        List<String> registeredPlayers = participants.stream()
                .filter(CompletedGameInfoParticipant::isRegisteredPlayer)
                .map(p -> "**" + p.getPlayerName() + "**")
                .collect(Collectors.toList());

        if (!registeredPlayers.isEmpty()) {
            titleBuilder.append("Players: ").append(String.join(", ", registeredPlayers));
        }


        // Use whichever color logic you prefer. If you have "completedGameInfo.getWin()"
        // to reflect the "player's team" result, you can do that. For demonstration:
        embed.setColor(registeredPlayerWon ? Color.GREEN : Color.RED);

        // Title + description
        embed.setTitle(titleBuilder.toString());

        // Show a main "Resultado" field. If you track the player's perspective in completedGameInfo.getWin():
        embed.addField("Resultado", registeredPlayerWon ? "🏆 VICTORIA" : "💀 DERROTA", false);



        // Add the columns for side-by-side participant lines
        // 1) Blue Team column
        embed.addField("Blue Team - " + blueTeamResult, blueTeamNames.toString(), true);

        // 2) KDA column
        embed.addField("KDA", kdaColumn.toString(), true);

        // 3) Red Team column
        embed.addField("Red Team - " + redTeamResult, redTeamNames.toString(), true);

        // Footer with match duration, or anything else
        embed.setTimestamp(Instant.now());

        return embed.build();
    }

    private MessageEmbed buildOngoingGameEmbed(CurrentGameInfo currentGameInfo) {
        List<Participant> participants = currentGameInfo.getParticipants();
        String map = "Summoner's Rift";
        String queueType = getQueueType(currentGameInfo.getQueueType());
        if(queueType.contains("ARAM")){
            map = "Bridge of progress";
        }

        // Build title with registered players
        StringBuilder titleBuilder = new StringBuilder("\uD83D\uDEA8 Partida en curso detectada: " + getQueueType(currentGameInfo.getQueueType()) + " | " + map + " | ");
        List<String> registeredPlayers = participants.stream()
                .filter(Participant::isRegisteredPlayer)
                .map(p -> "**" + p.getPlayerName() + "**")
                .collect(Collectors.toList());

        if (!registeredPlayers.isEmpty()) {
            titleBuilder.append("Players: ").append(String.join(", ", registeredPlayers));
        }

        EmbedBuilder embed = new EmbedBuilder();
        embed.setTitle(titleBuilder.toString());
        embed.setColor(0x1F8B4C);

        // Create field for Blue Team
        StringBuilder blueTeamField = new StringBuilder();
        StringBuilder blueTeamRankField = new StringBuilder();
        StringBuilder blueTeamStatsField = new StringBuilder();

        // Create field for Red Team
        StringBuilder redTeamField = new StringBuilder();
        StringBuilder redTeamRankField = new StringBuilder();
        StringBuilder redTeamStatsField = new StringBuilder();

        MongoCollection<Document> championsCollection = this.champsCollection;



        // Process participants and sort them into teams
        for (Participant participant : participants) {
            StringBuilder nameField = participant.getTeamId() == 100 ? blueTeamField : redTeamField;
            StringBuilder rankField = participant.getTeamId() == 100 ? blueTeamRankField : redTeamRankField;
            StringBuilder statsField = participant.getTeamId() == 100 ? blueTeamStatsField : redTeamStatsField;

            // Add player name (with champion icon placeholder)

            Document champion = championsCollection.find(new Document("key", participant.getChampionId())).first();
//            nameField.append("🦸‍♂️ | ");
            //nameField.append("<:Aatrox:1323017539508240464> | ");
            nameField.append("<:"+ champion.getString("id") +":"+ champion.getString("emojiId") +"> | ");
            if (participant.isRegisteredPlayer()) {
                nameField.append("__**").append(participant.getPlayerName()).append("**__");
            } else {
                nameField.append(participant.getPlayerName());
            }
            nameField.append("\n");

            // Add rank information (without emoji), ensure empty ranks still take a line
            String rank = participant.getRank();
            rankField.append(rank != null && !rank.isEmpty() ? rank : "\u200B").append("\n");

            // Add winrate information, ensure empty winrates still take a line
            String winrate = formatWinrate(participant.getWins(), participant.getLosses());
            statsField.append(winrate != null && !winrate.isEmpty() ? winrate : "\u200B").append("\n");
        }

        String rankType = "SOLO/DUO";
        if(getQueueType(currentGameInfo.getQueueType()).equals("RANKED_FLEX")){
            rankType = "FLEX";
        }
        // Add fields to embed
        embed.addField("Blue Team", blueTeamField.toString(), true);
        embed.addField(  rankType+ " Rank", blueTeamRankField.toString(), true);
        embed.addField("WR", blueTeamStatsField.toString(), true);

        embed.addField("\u200B", "\u200B", false); // Empty field for spacing

        embed.addField("Red Team", redTeamField.toString(), true);
        embed.addField(rankType+ " Rank", redTeamRankField.toString(), true);
        embed.addField("WR", redTeamStatsField.toString(), true);

        return embed.build();
    }


    /**
     * Helper to sum Kills/Deaths/Assists from the participants' KDA strings.
     * Returns an array [kills, deaths, assists].
     */
    private int[] sumTeamKda(List<CompletedGameInfoParticipant> teamParticipants) {
        int kills = 0, deaths = 0, assists = 0;
        for (CompletedGameInfoParticipant p : teamParticipants) {
            int[] kdaParts = parseKDA(p.getKda());
            kills   += kdaParts[0];
            deaths  += kdaParts[1];
            assists += kdaParts[2];
        }
        return new int[] { kills, deaths, assists };
    }

    /**
     * Parses a string like "5/8/3" into int[] {5, 8, 3}.
     */
    private int[] parseKDA(String kda) {
        if (kda == null || !kda.contains("/")) {
            return new int[] { 0, 0, 0 };
        }
        try {
            String[] parts = kda.split("/");
            int k = Integer.parseInt(parts[0]);
            int d = Integer.parseInt(parts[1]);
            int a = Integer.parseInt(parts[2]);
            return new int[] { k, d, a };
        } catch (NumberFormatException e) {
            return new int[] { 0, 0, 0 };
        }
    }

    // Helper method to format winrate only
    private String formatWinrate(int wins, int losses) {
        return String.format("%.1f%% (%dW/%dL)",
                ((double) wins / (wins + losses)) * 100,
                wins,
                losses);
    }

    private String tranformSecondsToMMSS(long seconds) {
        long minutes = seconds / 60;
        long secondsLeft = seconds % 60;
        return String.format("%02d:%02d", minutes, secondsLeft);
    }


}
