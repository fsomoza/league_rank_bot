package org.kiko.dev;// File: org/kiko/dev/service/RankService.java


import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.mongodb.client.FindIterable;
import com.mongodb.client.MongoCollection;
import com.mongodb.client.MongoCursor;
import com.mongodb.client.MongoDatabase;
import com.mongodb.client.model.Filters;
import com.mongodb.client.model.ReplaceOptions;
import com.mongodb.client.model.Sorts;
import net.dv8tion.jda.api.EmbedBuilder;
import net.dv8tion.jda.api.JDA;
import net.dv8tion.jda.api.entities.Guild;
import net.dv8tion.jda.api.entities.Message;
import net.dv8tion.jda.api.entities.MessageEmbed;
import net.dv8tion.jda.api.entities.channel.concrete.TextChannel;
import net.dv8tion.jda.api.requests.restaction.MessageCreateAction;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.Response;
import org.bson.Document;
import org.kiko.dev.MongoDbAdapter;
import org.kiko.dev.RiotApiAdapter;

import java.awt.*;
import java.io.IOException;
import java.util.*;
import java.util.List;

public class RankService {

    private final RiotApiAdapter riotApiAdapter;
    private final MongoDbAdapter mongoDbAdapter;

    private final JDA jda;

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

    // Constructor
    public RankService(JDA jda) {
        this.riotApiAdapter = RiotApiAdapter.getInstance();
        this.mongoDbAdapter = MongoDbAdapter.getInstance();
        this.jda = jda;
    }

    public String getPlayerRank(String name, String tagline) throws Exception {


        // Validate input
        if (name.isEmpty() || tagline.equals("#")) {
            throw new IllegalArgumentException("Invalid format. Use: /rank <name> <tag>");
        }

        // Fetch data from Riot API
        AccountInfo accountInfo = riotApiAdapter.getPuuid(name, tagline);
        System.out.println("PUUID: " + accountInfo.getPuuid());
        String encryptedSummonerId = riotApiAdapter.getEncryptedSummonerId(accountInfo.getPuuid());
        String rank = riotApiAdapter.getSoloQueueRank(encryptedSummonerId);

        // Save or update the player's rank in MongoDB
        savePlayerRank(accountInfo.getPuuid(), accountInfo.getName(), accountInfo.getTagLine(), rank, encryptedSummonerId);

        return rank;
    }


    public void checkWhoInGame() throws Exception {



        Guild guild = jda.getGuildById("1304851342497546372");
        TextChannel channel = guild.getTextChannelById("1312125144659132416");
        MongoDatabase database = mongoDbAdapter.getDatabase();

        MongoCollection<Document> serverRanksCollection = database.getCollection("serverRanks");
        MongoCollection<Document> gamesInProgressCollection = database.getCollection("gamesInProgress");



        // Keep track of players who are still in a game
        Set<String> playersInGame = new HashSet<>();

        // Check games in progress
        try (MongoCursor<Document> cursor = gamesInProgressCollection.find().iterator()) {
            while (cursor.hasNext()) {
                Document gameDoc = cursor.next();
                String gameId = gameDoc.getString("id");

                List<Document> participants = gameDoc.get("participants", List.class);
                String puuid = participants.get(0).get("puuid").toString();

                String foundGameId = riotApiAdapter.searchGameId(puuid, gameId);
                if (foundGameId != null) {
                    // Game is completed

                    HashSet<String> participantPuuids = new HashSet<>();
                    for (Document participant : participants) {
                        participantPuuids.add(participant.getString("puuid"));
                    }

                    CompletedGameInfo completedGameInfo = riotApiAdapter.checkCompletedGame(foundGameId, participantPuuids);
                    completedGameInfo.setQueueType(gameDoc.getString("queueType"));
                    //gamesInProgressCollection.deleteOne(new Document("id", gameId));


                    // Build your message content
//                    MessageCreateAction messageAction = channel.sendMessageEmbeds(buildEmbedMessage(completedGameInfo));
//
//
//                    // Set the message reference to reply to the specific message
//                    messageAction.setMessageReference(gameDoc.getString("messageId")).queue();

                   Message message = channel.sendMessageEmbeds(buildEmbedMessage(completedGameInfo))
                            .setMessageReference(gameDoc.getString("messageId")).complete();
                    if (message != null) {
                        System.out.println("Message sent successfully");
                        gamesInProgressCollection.deleteOne(new Document("id", gameId));

                    }else{
                        System.out.println("Message not sent");
                        throw new Exception("Message not sent");
                    }


                } else {
                    // Game is still in progress
                    for (Document participant : participants) {
                        playersInGame.add(participant.getString("puuid"));
                    }
                }
                System.out.println("Game ID: " + gameId);
            }
        } catch (Exception e) {
            e.printStackTrace();
            // Handle exceptions as needed
        }



        // Fetch players sorted by elo in descending order
        // Exclude players who are already in a game
//        List<Document> players = serverRanksCollection.find(
//                Filters.nin("puuid", playersInGame)
//        ).sort(Sorts.descending("elo")).into(new ArrayList<>());


        // Fetch players sorted by elo in descending order
        //Players who are already in a game are not excluded
        //to cover a corner case where a player is in the same game
        //as someone else(already in a game) and the add themselves to he players collection
        // this would cause to override the previous players info
        List<Document> players = serverRanksCollection.find()
        .sort(Sorts.descending("elo")).into(new ArrayList<>());

        //we use this set in the RiotApiAdapter to check if any of the participants (players)
        // in this game, are in the players list. If they are, we add them to the  playersInGame set
        // so we don't check them again. Also, we can then notify they are playing together

        HashMap<String, AccountInfo> playersMap = new HashMap<>();
        for (Document playerDoc : players) {
            String puuid = playerDoc.getString("puuid");
            if (puuid != null) {
                playersMap.put(puuid, new AccountInfo(playerDoc.getString("name")));
            }
        }

        // Check which players are currently in a game
        for (Document playerDoc : players) {

            String playerName = playerDoc.getString("name");
            String puuid = playerDoc.getString("puuid");

            if (playerName == null || puuid == null) {
                System.out.println("Player name or puuid is null");
                continue;
            }

            if (!playersInGame.contains(puuid) && playersMap.containsKey(puuid)) {
                CurrentGameInfo currentGameInfo = riotApiAdapter.checkWhoInGame(puuid, playersMap);
                if (currentGameInfo != null) {

                     List<Participant> participants = currentGameInfo.getParticipants();

                    List<Document> participantDocs = new ArrayList<>();

                     for (Participant participant : participants) {
                         // Add the player to the playersInGame set so we don't check them again
                         playersInGame.add(puuid);

                         System.out.println(playerName + " is in the game  " + currentGameInfo.getGameId() + " with " +
                                 getChampionName(participant.getChampionId()));
                         System.out.println("--------------------------------------------------------------------------------");

                         // Create a Document for the participant
                         Document participantDoc = new Document("puuid", participant.getPuuid())
                                 .append("championId", participant.getChampionId())
                                 .append("playerName", participant.getPlayerName());

                         // Add the participant Document to the list
                         participantDocs.add(participantDoc);

                     }

//                    StringBuilder sb = new StringBuilder();
//
//                    sb.append("\n\n"); // Add spacing before the message for separation
//                    sb.append("━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━\n"); // Visual separator
//                    sb.append("Partida en curso detectada!\n");
//                    sb.append("Modo de juego: " + getQueueType(currentGameInfo.getQueueType()) + "\n");
//                    sb.append("Jugadores en la partida:\n");
//                    sb.append("```\n"); // Start of code block
//                    sb.append(String.format("%-20s %-10s\n", "Player", "Champion"));
//                    sb.append(String.format("%-20s %-10s\n", "--------------------", "----------"));
//                    for (Participant participant : participants) {
//                        sb.append(String.format("%-20s %-10s\n", participant.getPlayerName(), getChampionName(participant.getChampionId())));
//                    }
//                    sb.append("```"); // End of code block
//                    sb.append("\n");
//
//                    if (getQueueType(currentGameInfo.getQueueType()).contains("ARAM")) {
//                        sb.append("Si no dejais que tiren los minions el nexo, sois unos sudorosos!\n");
//                    } else {
//                        sb.append("Si os stompean, recordad que siempre es jungle diff!\n");
//                    }
//
//                    sb.append("━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━\n"); // Visual separator
//                    sb.append("\n\n"); // Add spacing after the message for separation
//
//                    Message message = channel.sendMessage(sb.toString()).complete();


                    EmbedBuilder embed = new EmbedBuilder();
                    embed.setColor(0x1F8B4C); // Set a nice green color
                    embed.setTitle("🚨 Partida en curso detectada!");
                    embed.setDescription("**Modo de juego:** " + getQueueType(currentGameInfo.getQueueType()));

// Build the table as a single code block
                    StringBuilder tableBuilder = new StringBuilder();
                    tableBuilder.append("```");
                    tableBuilder.append(String.format("%-20s %-10s%n", "Player", "Champion"));
                    tableBuilder.append(String.format("%-20s %-10s%n", "--------------------", "----------"));

                    for (Participant participant : participants) {
                        String playerInfo = String.format("%-20s %-10s", participant.getPlayerName(), getChampionName(participant.getChampionId()));
                        tableBuilder.append(playerInfo).append("\n");
                    }

                    tableBuilder.append("```");

// Add the table as a single field
                    embed.addField("Jugadores en la partida", tableBuilder.toString(), false);

// Add a fun note depending on the game mode
                    if (getQueueType(currentGameInfo.getQueueType()).contains("ARAM")) {
                        embed.setFooter("💡 Si no dejais que tiren los minions el nexo, sois unos sudorosos!");
                    } else {
                        embed.setFooter("💡 Si os stompean, recordad que siempre es jungle diff!");
                    }

                    embed.setTimestamp(java.time.Instant.now());

// Send the embed message
                    Message message = channel.sendMessageEmbeds(embed.build()).complete();

                    // Prepare document for MongoDB
                    Document gameDoc = new Document("id", currentGameInfo.getGameId())
                            .append("queueType", getQueueType(currentGameInfo.getQueueType()))
                            .append("participants", participantDocs)
                            .append("messageId", message.getId());



                    // Upsert the game document in 'gamesInProgress' collection
                    gamesInProgressCollection.replaceOne(
                            new Document("id", currentGameInfo.getGameId()),
                            gameDoc,
                            new ReplaceOptions().upsert(true)
                    );


                } else {
                    System.out.println(playerName + " is not in a game");
                }
            }
        }
    }



    public void fetchAndStoreChampions() throws IOException {
        String url = "https://ddragon.leagueoflegends.com/cdn/14.23.1/data/en_US/champion.json";

        OkHttpClient client = new OkHttpClient();

        Request request = new Request.Builder()
                .url(url)
                .build();

        try (Response response = client.newCall(request).execute()) {
            if (!response.isSuccessful()) {
                throw new IOException("Unexpected code " + response);
            }

            String responseBody = response.body().string();

            ObjectMapper objectMapper = new ObjectMapper();
            JsonNode rootNode = objectMapper.readTree(responseBody);
            JsonNode dataNode = rootNode.get("data");

            if (dataNode == null || !dataNode.isObject()) {
                throw new IOException("Invalid JSON structure: 'data' field is missing or not an object.");
            }

            MongoDatabase database = mongoDbAdapter.getDatabase();
            MongoCollection<Document> collection = database.getCollection("champions");

            Iterator<Map.Entry<String, JsonNode>> fields = dataNode.fields();
            while (fields.hasNext()) {
                Map.Entry<String, JsonNode> entry = fields.next();
                JsonNode championNode = entry.getValue();

                Champion champion = objectMapper.treeToValue(championNode, Champion.class);

                // Create a document for MongoDB
                Document championDoc = new Document("id", champion.getKey())
                        .append("name", champion.getName());

                // Upsert the champion document based on the 'id' field
                collection.replaceOne(
                        new Document("id", champion.getId()),
                        championDoc,
                        new ReplaceOptions().upsert(true)
                );
            }

            System.out.println("Champion data successfully fetched and stored.");
        }
    }


    private void savePlayerRank(String puuid, String name, String tagline, String rank, String encryptedSummonerId) {
        MongoDatabase database = mongoDbAdapter.getDatabase();
        MongoCollection<Document> collection = database.getCollection("serverRanks");

        int elo = computeElo(rank);

        // Create a document to represent the player
        Document playerDoc = new Document("puuid", puuid)
                .append("encryptedSummonerId", encryptedSummonerId)
                .append("name", name)
                .append("tagline", tagline)
                .append("rank", rank)
                .append("elo", elo)
                .append("timestamp", System.currentTimeMillis());

        // Upsert the document (insert if not exists, update if exists)
        collection.replaceOne(
                new Document("puuid", puuid),
                playerDoc,
                new ReplaceOptions().upsert(true)
        );
    }


    //TODO: load on memory on startup and update it like once a day?
    public String getQueueType(String queueId){
        switch (queueId){
            case "420":
                return "RANKED_SOLO/DUO";
            case "440":
                return "RANKED_FLEX";
            case "100":
                return "ARAM";
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


    //TODO: load on memory on startup and update it like once a day?
    public String getChampionName(String id){
        MongoDatabase database = mongoDbAdapter.getDatabase();
        MongoCollection<Document> collection = database.getCollection("champions"); // Replace with the actual collection name
        Document champion = collection.find(new Document("id", id)).first();
        return champion.getString("name");
    }


    // Method to get the ranked list of players
    public String getRankedPlayerList() {
        MongoDatabase database = mongoDbAdapter.getDatabase();
        MongoCollection<Document> collection = database.getCollection("serverRanks");

        // Fetch and sort players by elo in descending order
        FindIterable<Document> iterable = collection.find().sort(Sorts.descending("elo"));
        List<Document> players = iterable.into(new ArrayList<>());

        // Build the table using code blocks for monospaced font
        StringBuilder sb = new StringBuilder();
        sb.append("```\n"); // Start of code block

        // Table headers
        sb.append(String.format("%-5s %-25s %-20s %-5s\n", "Rank", "Player", "Rank", "ELO"));
        sb.append(String.format("%-5s %-25s %-20s %-5s\n", "----", "-------------------------", "--------------------", "----"));

        int position = 1;
        for (Document player : players) {
            String name = player.getString("name");
            String tagline = player.getString("tagline");
            String rank = player.getString("rank");
            int elo = player.getInteger("elo", 0);

            if (elo >0){
                sb.append(String.format("%-5d %-25s %-20s %-5d\n", position, name + "#" + tagline, rank, elo));
            }else{
                sb.append(String.format("%-5d %-25s %-20s\n", position, name + "#" + tagline, rank));
            }


            position++;
        }

        sb.append("```"); // End of code block
        return sb.toString();
    }


    public MessageEmbed getRankedPlayerListEmbed() {
        MongoDatabase database = mongoDbAdapter.getDatabase();
        MongoCollection<Document> collection = database.getCollection("serverRanks");

        // Fetch and sort players by ELO in descending order
        FindIterable<Document> iterable = collection.find().sort(Sorts.descending("elo"));
        List<Document> players = iterable.into(new ArrayList<>());

        // Initialize the EmbedBuilder
        EmbedBuilder embed = new EmbedBuilder();

        // Set the title and color of the embed
        embed.setTitle("🏆 Ranked Players Leaderboard");
        embed.setColor(Color.BLUE); // Set a neutral blue color for the leaderboard

        // Define medal emojis
        String goldMedal = "\uD83E\uDD47";   // 🥇
        String silverMedal = "\uD83E\uDD48"; // 🥈
        String bronzeMedal = "\uD83E\uDD49"; // 🥉

        // Build the table header for the embed
        StringBuilder tableBuilder = new StringBuilder();
        tableBuilder.append("```");
        tableBuilder.append(String.format("%-5s %-25s %-20s%n", "Rank", "Player", "Rank"));
        tableBuilder.append(String.format("%-5s %-25s %-20s%n", "----", "-------------------------", "--------------------"));

        int position = 1;
        for (Document player : players) {
            String name = player.getString("name");
            String tagline = player.getString("tagline");
            String rank = player.getString("rank");
            int elo = player.getInteger("elo", 0);

            // Assign medals to top 3 players
            String medal = "";
            if (position == 1) {
                medal = goldMedal + " ";
            } else if (position == 2) {
                medal = silverMedal + " ";
            } else if (position == 3) {
                medal = bronzeMedal + " ";
            }

            // Append each player's data to the table with an extra newline for spacing
            tableBuilder.append(String.format("%-5d %-25s %-20s%n%n", position, medal + name + "#" + tagline, rank));

            position++;
        }
        tableBuilder.append("```");

        // Add the table to the embed
        embed.setDescription(tableBuilder.toString());

        // Set the footer with additional information
        embed.setFooter("Data fetched from the server ranks collection.", null);

        // Add a timestamp
        embed.setTimestamp(java.time.Instant.now());

        return embed.build();
    }





    // Method to compute elo from rank string
    private int computeElo(String rankString) {


        if (rankString == null || rankString.isEmpty()) {
            return 0;
        }
        String[] parts = rankString.split(" ");
        if (parts.length == 4) {
            String tier = parts[0].toUpperCase();
            String division = parts[1].toUpperCase();
            int tierValue = TIER_MAP.getOrDefault(tier, 0);
            int divisionValue = DIVISION_MAP.getOrDefault(division, 0);
            int lpValue = Integer.parseInt(parts[2]);
            int elo = tierValue * 1000 + divisionValue * 100 + lpValue;
            return elo;
        } else if (parts.length == 3) {
            // For ranks like "MASTER", "GRANDMASTER", "CHALLENGER"
            String tier = parts[0].toUpperCase();
            int tierValue = TIER_MAP.getOrDefault(tier, 0);
            int lpValue = Integer.parseInt(parts[2]);
            int elo = tierValue * 1000 + lpValue;
            return elo;
        } else {
            return 0;
        }
    }

    private String buildMessage(CompletedGameInfo completedGameInfo) {

        StringBuilder sb = new StringBuilder();

        sb.append("\n\n"); // Add spacing before the message for separation
        sb.append("━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━\n"); // Visual separator
        sb.append("Partida finalizada!\n");
        sb.append("Modo de juego: " + completedGameInfo.getQueueType() + "\n");
        sb.append("Resultado: " + (completedGameInfo.getWin() ? "VICTORIA" : "DERROTA") + "\n");
        sb.append("Jugadores en la partida:\n");
        sb.append("```\n"); // Start of code block
        sb.append(String.format("%-20s %-10s %-5s\n", "Jugadoe", "Campeón", "KDA"));
        sb.append(String.format("%-20s %-10s %-5s\n", "--------------------", "----------", "----"));
        for (CompletedGameInfoParticipant participant : completedGameInfo.getParticipants()) {
            sb.append(String.format("%-20s %-10s %-5s\n", participant.getPlayerName(), participant.getChampion(), participant.getKda()));
        }
        sb.append("```"); // End of code block
        sb.append("\n");

        if (completedGameInfo.getQueueType().equalsIgnoreCase("ARAM")) {
            if (completedGameInfo.getWin()) {
                sb.append("El putisimo amo, maestro de todos los campeones, el faker de los arams! \n");
            } else {
                sb.append("Mala suerte, no te han sonreido los dados \n");
            }
        }

        if (completedGameInfo.getQueueType().equalsIgnoreCase("RANKED_SOLO/DUO")) {
            if (completedGameInfo.getWin()) {
                sb.append("Da gracias por esos LP's que buena falta te hacen \n");
            } else {
                sb.append("Recuerda,  JUNGLE DIFF \n");
            }
        }

        if (completedGameInfo.getQueueType().equalsIgnoreCase("RANKED_FLEX")) {
            if (completedGameInfo.getWin()) {
                sb.append("EL terror de las flex, T1 en su prime \n");
            } else {
                sb.append("Damian no podia rotar, que le vamos a hacer \n");
            }
        }

            sb.append("━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━\n"); // Visual separator
            sb.append("\n\n"); // Add spacing after the message for separation

            return sb.toString();
        }

    private MessageEmbed buildEmbedMessage(CompletedGameInfo completedGameInfo) {
        // Initialize the EmbedBuilder
        EmbedBuilder embed = new EmbedBuilder();

        // Set the color based on the game result: green for victory, red for defeat
        embed.setColor(completedGameInfo.getWin() ? Color.GREEN : Color.RED);

        // Set the title of the embed
        embed.setTitle("🎉 Partida Finalizada!");

        // Set the description with the game mode
        embed.setDescription("**Modo de juego:** " + completedGameInfo.getQueueType());

        // Add the result as a field
        embed.addField("Resultado", completedGameInfo.getWin() ? "🏆 VICTORIA" : "💀 DERROTA", true);

        // Build the players table within a code block for formatting
        StringBuilder tableBuilder = new StringBuilder();
        tableBuilder.append("```");
        tableBuilder.append(String.format("%-20s %-15s %-5s%n", "Jugador", "Campeón", "KDA"));
        tableBuilder.append(String.format("%-20s %-15s %-5s%n", "--------------------", "---------------", "----"));

        for (CompletedGameInfoParticipant participant : completedGameInfo.getParticipants()) {
            tableBuilder.append(String.format("%-20s %-15s %-5s%n",
                    participant.getPlayerName(),
                    participant.getChampion(),
                    participant.getKda()));
        }

        tableBuilder.append("```");

        // Add the players table as a field
        embed.addField("Jugadores en la partida", tableBuilder.toString(), false);

        // Determine the footer message based on game mode and result
        String footerMessage = "";
        String queueType = completedGameInfo.getQueueType().toUpperCase();

        switch (queueType) {
            case "ARAM":
                footerMessage = completedGameInfo.getWin()
                        ? "El putísimo amo, maestro de todos los campeones, el Faker de los ARAMs! 🏅"
                        : "Mala suerte, no te han sonreído los dados. 🎲";
                break;
            case "RANKED_SOLO/DUO":
                footerMessage = completedGameInfo.getWin()
                        ? "Da gracias por esos LP's que buena falta te hacen. 📈"
                        : "Recuerda, ¡JUNGLE DIFF! 🌿";
                break;
            case "RANKED_FLEX":
                footerMessage = completedGameInfo.getWin()
                        ? "EL terror de las Flex, ¡T1 en su prime! 🏆"
                        : "Damian no podía rotar, qué le vamos a hacer. 🤷‍♂️";
                break;
            default:
                footerMessage = "¡GG! 🎮";
                break;
        }

        // Set the footer with a lightbulb emoji and the dynamic message
        embed.setFooter("💡 " + footerMessage);

        // Optionally, add a timestamp to the embed
        embed.setTimestamp(java.time.Instant.now());

        return embed.build();
    }

}
