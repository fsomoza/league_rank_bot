package org.kiko.dev.commands;

import net.dv8tion.jda.api.EmbedBuilder;
import net.dv8tion.jda.api.entities.MessageEmbed;
import net.dv8tion.jda.api.events.interaction.command.SlashCommandInteractionEvent;
import org.kiko.dev.RankService;

import java.awt.*;
import java.time.Instant;

public class RankCommand implements Command {

    private final RankService rankService;

    public RankCommand(RankService rankService) {
        this.rankService = rankService;
    }

    @Override
    public String getName() {
        return "rank";
    }

    @Override
    public void execute(SlashCommandInteractionEvent event) {
        String name = event.getOption("name").getAsString();
        String tagline = event.getOption("tagline").getAsString();

        event.deferReply().queue(); // For longer operations

        try {
            MessageEmbed rank = rankService.getPlayerInformation(name, tagline);
            event.getHook().sendMessageEmbeds(rank).queue();
        } catch (IllegalArgumentException e) {
            System.out.println(e.getMessage());
            event.getHook().sendMessage(e.getMessage()).queue();
        } catch (Exception e) {
            System.out.println(e.getMessage());
            event.getHook().sendMessage("Error getting rank information: " + e.getMessage()).queue();
        }
    }
}
