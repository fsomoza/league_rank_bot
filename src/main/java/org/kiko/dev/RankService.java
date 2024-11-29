package org.kiko.dev;// File: org/kiko/dev/service/RankService.java


import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.mongodb.client.FindIterable;
import com.mongodb.client.MongoCollection;
import com.mongodb.client.MongoCursor;
import com.mongodb.client.MongoDatabase;
import com.mongodb.client.model.ReplaceOptions;
import com.mongodb.client.model.Sorts;
import net.dv8tion.jda.api.JDA;
import net.dv8tion.jda.api.entities.Guild;
import net.dv8tion.jda.api.entities.channel.concrete.TextChannel;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.Response;
import org.bson.Document;
import org.kiko.dev.MongoDbAdapter;
import org.kiko.dev.RiotApiAdapter;

import java.io.IOException;
import java.util.*;

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


       List<String> playersInGame = new ArrayList<>();




        MongoCollection<Document> collection = database.getCollection("serverRanks");
        // Fetch and sort players by elo in descending order
        FindIterable<Document> iterable = collection.find().sort(Sorts.descending("elo"));
        List<Document> players = iterable.into(new ArrayList<>());



        MongoCollection<Document> gamesCollection = database.getCollection("gamesInProgress");
        FindIterable<Document> gamesIterable = gamesCollection.find();

        try (MongoCursor<Document> cursor = gamesIterable.iterator()) {
            while (cursor.hasNext()) {
                Document game = cursor.next();
                String gameId = game.getString("id");
                String puuid =  game.getString("puuid");

                String foundGameId = riotApiAdapter.searchGameId(puuid, gameId);
                if (foundGameId != null){
                    CompletedGameInfo completedGameInfo = riotApiAdapter.checkCompletedGame(foundGameId, puuid);
                    gamesCollection.deleteOne(new Document("id", gameId));

                    if(completedGameInfo.getWin()){
                    channel.sendMessage(completedGameInfo.getPlayerName() + " ha ganado una partida con " + completedGameInfo.getChampion()
                    + " KDA :" + completedGameInfo.getKda()).queue();
                    }else{
                        channel.sendMessage(completedGameInfo.getPlayerName() + " perdió una partida con " + completedGameInfo.getChampion()
                                + " KDA :" + completedGameInfo.getKda()).queue();
                    }
                }else{
                    playersInGame.add(puuid);
                }
                System.out.println("Game ID: " + gameId);
            }
        } catch (Exception e) {
            e.printStackTrace();
            // Handle exceptions as needed
        }

        //iterate over gamesIterable and get the gameId



        //iterate over the map and check who is in a game


        Map<String,CurrentGameInfo> playersMap = new HashMap<>();



        for (Document player : players) {
            if (player.getString("name") == null){
                System.out.println("null");
            }
            if (player.getString("name").equalsIgnoreCase("gimi23")){
                System.out.println(player.getString("name"));
            }

           CurrentGameInfo currentGameInfo = riotApiAdapter.checkWhoInGame(player.getString("puuid"));
           if (currentGameInfo != null && !playersInGame.contains(player.getString("puuid"))){
               System.out.println(player.getString("name") + " is in a game with " + currentGameInfo.getChampion());

               currentGameInfo.setPlayerName(player.getString("name"));
                currentGameInfo.setPuuid(player.getString("puuid"));
               // Create a document for MongoDB


               Document gameDoc = new Document("id", currentGameInfo.getGameId())
                       .append("championName", this.getChampionName(currentGameInfo.getChampion()))
                       .append("playerName", player.getString("name")).
                       append("puuid", currentGameInfo.getPuuid());

               // Upsert the champion document based on the 'id' field
               gamesCollection.replaceOne(
                       new Document("id", currentGameInfo.getGameId()),
                       gameDoc,
                       new ReplaceOptions().upsert(true)
               );

               channel.sendMessage(currentGameInfo.getPlayerName() + " está jugando una partida con " + this.getChampionName(currentGameInfo.getChampion())).queue();



           }else{
               System.out.println(player.getString("name") + " is not in a game");
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
}
