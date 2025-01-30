package org.kiko.dev.game_scanner;

import com.mongodb.client.MongoCollection;
import net.dv8tion.jda.api.EmbedBuilder;
import net.dv8tion.jda.api.entities.MessageEmbed;
import org.bson.Document;
import org.kiko.dev.dtos.CompletedGameInfo;
import org.kiko.dev.dtos.CompletedGameInfoParticipant;
import org.kiko.dev.dtos.CurrentGameInfo;
import org.kiko.dev.dtos.Participant;

import java.awt.*;
import java.time.Instant;
import java.util.*;
import java.util.List;
import java.util.stream.Collectors;

/**
 * Utility class responsible for building the Discord embeds for ongoing and completed games.
 */
public class GameMessageBuilder {

    private GameMessageBuilder() {
        // Utility class; prevent instantiation
    }

    /**
     * Builds an embed message for a completed game.
     *
     * @param completedGameInfo  the data about the finished match
     * @param participantPuuids  the set of participant puuids who are registered players
     * @param champsCollection   MongoDB collection used to fetch champion details (emojis, etc.)
     * @return MessageEmbed containing the completed game summary
     */
    public static MessageEmbed buildCompletedGameEmbed(CompletedGameInfo completedGameInfo,
                                                       Set<String> participantPuuids,
                                                       MongoCollection<Document> champsCollection) {

        // Mark which participants are "registered"
        completedGameInfo.getParticipants().forEach(
                participant -> participant.setRegisteredPlayer(participantPuuids.contains(participant.getPuuid()))
        );

        // Check if at least one registered player was on the winning team
        boolean registeredPlayerWon = completedGameInfo.getParticipants().stream()
                .filter(CompletedGameInfoParticipant::isRegisteredPlayer)
                .findFirst()
                .map(CompletedGameInfoParticipant::isWin)
                .orElse(false);

        // Separate participants by team
        List<CompletedGameInfoParticipant> blueTeam = completedGameInfo.getParticipants().stream()
                .filter(p -> p.getTeamId() == 100)
                .collect(Collectors.toList());
        List<CompletedGameInfoParticipant> redTeam  = completedGameInfo.getParticipants().stream()
                .filter(p -> p.getTeamId() == 200)
                .collect(Collectors.toList());

        // Determine which side won
        boolean blueTeamWin = blueTeam.stream().anyMatch(CompletedGameInfoParticipant::isWin);
        boolean redTeamWin  = redTeam.stream().anyMatch(CompletedGameInfoParticipant::isWin);

        String blueTeamResult = blueTeamWin ? "Win" : "Defeat";
        String redTeamResult  = redTeamWin  ? "Win" : "Defeat";

        // Build participant-line columns
        StringBuilder blueTeamNames = new StringBuilder();
        StringBuilder kdaColumn     = new StringBuilder();
        StringBuilder redTeamNames  = new StringBuilder();

        int maxSize = Math.max(blueTeam.size(), redTeam.size());
        for (int i = 0; i < maxSize; i++) {
            CompletedGameInfoParticipant bp = (i < blueTeam.size()) ? blueTeam.get(i) : null;
            CompletedGameInfoParticipant rp = (i < redTeam.size()) ? redTeam.get(i) : null;

            // Blue side
            if (bp != null) {
                Document champion = champsCollection.find(new Document("key", bp.getChampionId())).first();
                if (champion != null) {
                    blueTeamNames.append("<:")
                            .append(champion.getString("id"))
                            .append(":")
                            .append(champion.getString("emojiId"))
                            .append("> | ");
                }
                if (bp.isRegisteredPlayer()) {
                    blueTeamNames.append("__**").append(bp.getPlayerName()).append("**__");
                } else {
                    blueTeamNames.append(bp.getPlayerName());
                }
                blueTeamNames.append("\n");
            } else {
                blueTeamNames.append("\n");
            }

            // KDA Column
            String blueKda = (bp != null) ? bp.getKda() : "";
            String redKda  = (rp != null) ? rp.getKda() : "";
            kdaColumn.append(String.format("%-7s ⚔️ %-7s", blueKda, redKda)).append("\n");

            // Red side
            if (rp != null) {
                Document champion = champsCollection.find(new Document("key", rp.getChampionId())).first();
                if (champion != null) {
                    redTeamNames.append("<:")
                            .append(champion.getString("id"))
                            .append(":")
                            .append(champion.getString("emojiId"))
                            .append("> | ");
                }
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

        EmbedBuilder embed = new EmbedBuilder();

        List<CompletedGameInfoParticipant> participants = completedGameInfo.getParticipants();
        String queueType = completedGameInfo.getQueueType();
        String map = "Summoner's Rift";
        if (queueType.contains("ARAM")) {
            map = "Bridge of progress";
        }

        // Build title
        StringBuilder titleBuilder = new StringBuilder("🎉 Partida Finalizada! ")
                .append(completedGameInfo.getQueueType())
                .append(" | ")
                .append(map)
                .append(" | ")
                .append(transformSecondsToMMSS(completedGameInfo.getGameDuration()))
                .append(" | ");

        // Registered players in title
        List<String> registeredPlayers = participants.stream()
                .filter(CompletedGameInfoParticipant::isRegisteredPlayer)
                .map(p -> "**" + p.getPlayerName() + "**")
                .collect(Collectors.toList());
        if (!registeredPlayers.isEmpty()) {
            titleBuilder.append("Players: ").append(String.join(", ", registeredPlayers));
        }

        embed.setTitle(titleBuilder.toString());
        embed.setColor(registeredPlayerWon ? Color.GREEN : Color.RED);
        embed.addField("Resultado", registeredPlayerWon ? "🏆 VICTORIA" : "💀 DERROTA", false);

        // Add participant columns
        embed.addField("Blue Team - " + blueTeamResult, blueTeamNames.toString(), true);
        embed.addField("KDA", kdaColumn.toString(), true);
        embed.addField("Red Team - " + redTeamResult, redTeamNames.toString(), true);

        embed.setTimestamp(Instant.now());
        return embed.build();
    }

    /**
     * Builds an embed message for an ongoing (currently in-progress) game.
     *
     * @param currentGameInfo    the data about the ongoing match
     * @param champsCollection   MongoDB collection for champion details
     * @return MessageEmbed containing details of the ongoing game
     */
    public static MessageEmbed buildOngoingGameEmbed(CurrentGameInfo currentGameInfo,
                                                     MongoCollection<Document> champsCollection) {

        List<Participant> participants = currentGameInfo.getParticipants();

        String queueType = getQueueType(currentGameInfo.getQueueType());
        String map = queueType.contains("ARAM") ? "Bridge of progress" : "Summoner's Rift";

        // Build title
        StringBuilder titleBuilder = new StringBuilder("\uD83D\uDEA8 Partida en curso detectada: ")
                .append(queueType)
                .append(" | ")
                .append(map)
                .append(" | ");

        // Registered players
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

        // Prepare text for Blue / Red teams
        StringBuilder blueTeamField      = new StringBuilder();
        StringBuilder blueTeamRankField  = new StringBuilder();
        StringBuilder blueTeamStatsField = new StringBuilder();

        StringBuilder redTeamField      = new StringBuilder();
        StringBuilder redTeamRankField  = new StringBuilder();
        StringBuilder redTeamStatsField = new StringBuilder();

        for (Participant participant : participants) {
            boolean isBlueSide = (participant.getTeamId() == 100);
            StringBuilder nameField  = isBlueSide ? blueTeamField      : redTeamField;
            StringBuilder rankField  = isBlueSide ? blueTeamRankField  : redTeamRankField;
            StringBuilder statsField = isBlueSide ? blueTeamStatsField : redTeamStatsField;

            // Champion + Summoner Name
            Document champion = champsCollection.find(new Document("key", participant.getChampionId())).first();
            if (champion != null) {
                nameField.append("<:")
                        .append(champion.getString("id"))
                        .append(":")
                        .append(champion.getString("emojiId"))
                        .append("> | ");
            }
            if (participant.isRegisteredPlayer()) {
                nameField.append("__**").append(participant.getPlayerName()).append("**__");
            } else {
                nameField.append(participant.getPlayerName());
            }
            nameField.append("\n");

            // Ranks
            String rank = participant.getRank();
            rankField.append((rank != null && !rank.isEmpty()) ? rank : "\u200B").append("\n");

            // Winrate
            String wr = formatWinrate(participant.getWins(), participant.getLosses());
            statsField.append(wr != null ? wr : "\u200B").append("\n");
        }

        String rankType = "SOLO/DUO";
        if ("RANKED_FLEX".equals(queueType)) {
            rankType = "FLEX";
        }

        // Add fields to embed
        embed.addField("Blue Team", blueTeamField.toString(), true);
        embed.addField(rankType + " Rank", blueTeamRankField.toString(), true);
        embed.addField("WR", blueTeamStatsField.toString(), true);

        embed.addBlankField(false);

        embed.addField("Red Team", redTeamField.toString(), true);
        embed.addField(rankType + " Rank", redTeamRankField.toString(), true);
        embed.addField("WR", redTeamStatsField.toString(), true);

        return embed.build();
    }

    /* ------------------------------------------------------------------
       Helper Methods (these used to be in GameScanner, but are purely
       for building messages, so we can keep them here in the builder)
       ------------------------------------------------------------------ */

    /**
     * Transform queue IDs to a textual description.
     */
    private static String getQueueType(String queueId) {
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
     * Format seconds as MM:SS.
     */
    private static String transformSecondsToMMSS(long seconds) {
        long minutes = seconds / 60;
        long secondsLeft = seconds % 60;
        return String.format("%02d:%02d", minutes, secondsLeft);
    }

    /**
     * Parses KDA string like "5/8/3" into three integers.
     */
    private static int[] parseKDA(String kda) {
        if (kda == null || !kda.contains("/")) {
            return new int[]{0, 0, 0};
        }
        try {
            String[] parts = kda.split("/");
            int k = Integer.parseInt(parts[0]);
            int d = Integer.parseInt(parts[1]);
            int a = Integer.parseInt(parts[2]);
            return new int[]{k, d, a};
        } catch (NumberFormatException e) {
            return new int[]{0, 0, 0};
        }
    }

    /**
     * Sums the kills, deaths, assists across a list of participants.
     */
    private static int[] sumTeamKda(List<CompletedGameInfoParticipant> teamParticipants) {
        int kills = 0, deaths = 0, assists = 0;
        for (CompletedGameInfoParticipant p : teamParticipants) {
            int[] kdaParts = parseKDA(p.getKda());
            kills   += kdaParts[0];
            deaths  += kdaParts[1];
            assists += kdaParts[2];
        }
        return new int[]{kills, deaths, assists};
    }

    /**
     * Formatting a player's winrate in a short style: "54.0% (15W/13L)"
     */
    private static String formatWinrate(int wins, int losses) {
        int total = wins + losses;
        if (total == 0) {
            return "0% (0W/0L)";
        }
        double winRate = (wins * 100.0) / total;
        return String.format("%.1f%% (%dW/%dL)", winRate, wins, losses);
    }
}
