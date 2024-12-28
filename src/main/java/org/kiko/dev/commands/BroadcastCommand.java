package org.kiko.dev.commands;

import net.dv8tion.jda.api.EmbedBuilder;
import net.dv8tion.jda.api.JDA;
import net.dv8tion.jda.api.entities.Guild;
import net.dv8tion.jda.api.entities.MessageEmbed;
import net.dv8tion.jda.api.entities.channel.concrete.TextChannel;
import net.dv8tion.jda.api.events.interaction.command.SlashCommandInteractionEvent;
import org.kiko.dev.RankService;

import java.awt.*;
import java.time.Instant;
import java.util.List;

public class BroadcastCommand implements Command {

    private final RankService rankService;
    private final JDA jda;

    private static final String ADMIN_GUILD_ID = "1310689513894318121";
    private static final String ALLOWED_USER_ID = "164763054842576897";

    public BroadcastCommand(RankService rankService, JDA jda) {
        this.rankService = rankService;
        this.jda = jda;
    }

    @Override
    public String getName() {
        return "broadcast";
    }

    @Override
    public void execute(SlashCommandInteractionEvent event) {
        if (!event.getGuild().getId().equals(ADMIN_GUILD_ID)) {
            event.reply("This command can only be used from the authorized server.").setEphemeral(true).queue();
            return;
        }

        String userId = event.getUser().getId();

        if (!userId.equals(ALLOWED_USER_ID)) {
            event.reply("You don't have permission to use this command.")
                    .setEphemeral(true) // Makes the response only visible to the command user
                    .queue();
            return;
        }

        String message = event.getOption("message").getAsString();
        String type = event.getOption("type").getAsString().toLowerCase();

        // Create appropriate embed based on type
        MessageEmbed broadcastEmbed = createBroadcastEmbed(message, type);

        // Defer the reply since broadcasting might take time
        event.deferReply().queue();

        // Broadcast to all servers
        broadcastToAllServers(broadcastEmbed);

        // Confirm to the sender
        event.getHook().sendMessage("Broadcast sent successfully to all servers!").setEphemeral(true).queue();
    }

    private MessageEmbed createBroadcastEmbed(String message, String type) {
        EmbedBuilder builder = new EmbedBuilder();

        switch (type) {
            case "update":
                builder.setTitle("🚀 Version Update")
                        .setColor(Color.BLUE);
                break;
            case "maintenance":
                builder.setTitle("🔧 Maintenance")
                        .setColor(Color.ORANGE);
                break;
            default:
                builder.setTitle("📢 Announcement")
                        .setColor(Color.GREEN);
        }

        builder.setDescription(message)
                .setTimestamp(Instant.now())
                .setFooter("YamatoCannon", null);

        return builder.build();
    }

    private void broadcastToAllServers(MessageEmbed embed) {
        for (Guild guild : jda.getGuilds()) {
            // Try to send to the first available text channel named "game_scanner"
            List<TextChannel> channels = guild.getTextChannelsByName("game_scanner", true);
            if (!channels.isEmpty()) {
                TextChannel channel = channels.get(0);
                channel.sendMessageEmbeds(embed)
                        .queue(
                                success -> System.out.println("Broadcast sent to " + guild.getName()),
                                error -> System.err.println("Failed to send broadcast to " + guild.getName())
                        );
            } else {
                System.err.println("No 'game_scanner' channel found in guild: " + guild.getName());
            }
        }
    }
}
