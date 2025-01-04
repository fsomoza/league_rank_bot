package org.kiko.dev.commands;

import net.dv8tion.jda.api.EmbedBuilder;
import net.dv8tion.jda.api.entities.MessageEmbed;
import net.dv8tion.jda.api.events.interaction.command.SlashCommandInteractionEvent;
import org.kiko.dev.RankService;

import java.awt.*;
import java.time.Instant;

public class DeleteCommand implements Command {

    private final RankService rankService;

    public DeleteCommand(RankService rankService) {
        this.rankService = rankService;
    }

    @Override
    public String getName() {
        return "delete";
    }

    @Override
    public void execute(SlashCommandInteractionEvent event) {
        String name = event.getOption("name").getAsString();
        String tagline = event.getOption("tagline").getAsString();

        event.deferReply().queue(); // For longer operations

        try {
            rankService.deletePlayer(name, tagline);
            event.getHook().sendMessageEmbeds(createDeleteEmbed(name, tagline)).queue();
        } catch (IllegalArgumentException e) {
            System.out.println(e.getMessage());
            event.getHook().sendMessage(e.getMessage()).queue();
        } catch (Exception e) {
            System.out.println(e.getMessage());
            event.getHook().sendMessage("Error deleting player: " + e.getMessage()).queue();
        }
    }

    private MessageEmbed createDeleteEmbed(String name, String tagline) {
        EmbedBuilder builder = new EmbedBuilder();
        builder.setTitle("🗑️ Player Deleted")
                .setColor(Color.RED)
                .addField("Name", name, true)
                .addField("Tagline", tagline, true)
                .setTimestamp(Instant.now());
        return builder.build();
    }
}
