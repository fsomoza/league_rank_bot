package org.kiko.dev.commands;

import net.dv8tion.jda.api.EmbedBuilder;
import net.dv8tion.jda.api.entities.MessageEmbed;
import net.dv8tion.jda.api.events.interaction.command.SlashCommandInteractionEvent;
import net.dv8tion.jda.api.interactions.components.ActionRow;
import net.dv8tion.jda.api.interactions.components.selections.StringSelectMenu;
import org.kiko.dev.RankService;

import java.awt.*;
import java.time.Instant;

public class RankingCommand implements Command {

    private final RankService rankService;

    public RankingCommand(RankService rankService) {
        this.rankService = rankService;
    }

    @Override
    public String getName() {
        return "ranking";
    }

    @Override
    public void execute(SlashCommandInteractionEvent event) {
        event.deferReply().queue(); // For longer operations
        try {
            // Create the initial embed with SoloQ rankings
            MessageEmbed initialEmbed = rankService.getRankedPlayerListEmbed("soloQ");

            // Create the queue type selector
            StringSelectMenu queueSelector = StringSelectMenu.create("queue-selector")
                    .setPlaceholder("Select Queue Type")
                    .addOption("Solo Queue", "soloQ", "View Solo Queue rankings")
                    .addOption("Flex Queue", "flexQ", "View Flex Queue rankings")
                    .build();

            // Create action row with the selector
            ActionRow actionRow = ActionRow.of(queueSelector);

            // Send message with both embed and selector
            event.getHook().sendMessageEmbeds(initialEmbed)
                    .setComponents(actionRow)
                    .queue();

        } catch (Exception e) {
            System.out.println(e.getMessage());
            event.getHook().sendMessage("Error getting rank information: " + e.getMessage()).queue();
        }
    }
}
