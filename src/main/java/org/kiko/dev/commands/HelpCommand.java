package org.kiko.dev.commands;

import net.dv8tion.jda.api.EmbedBuilder;
import net.dv8tion.jda.api.entities.MessageEmbed;
import net.dv8tion.jda.api.events.interaction.command.SlashCommandInteractionEvent;

import java.awt.*;
import java.time.Instant;

public class HelpCommand implements Command {

    @Override
    public String getName() {
        return "help";
    }

    @Override
    public void execute(SlashCommandInteractionEvent event) {
        event.deferReply().queue(); // For longer operations
        event.getHook().sendMessageEmbeds(createHelpEmbed()).queue();
    }

    private MessageEmbed createHelpEmbed() {
        EmbedBuilder builder = new EmbedBuilder();
        builder.setTitle("Bot Help & Commands")
                .setColor(Color.CYAN)
                .setDescription("Hello! I am a bot designed to provide live-tracking of your League of Legends games amongst other things. Here are the commands and features you can use:")
                .addField("GAME SCANNER",
                        "This is the main feature of the bot. Does a live-tracking of the games of the players you have registered." +
                                "You need to create a channel called `game_scanner` in your server to use this feature.", false)
                .addField("/add <name> <tagline>",
                        "Registers a player in the bot's database using their **name** and **tagline** (without #). This is required to use the other commands.",
                        false)
                .addField("/rank <name> <tagline>",
                        "Retrieves a player's ranked information based on their in-game **name** and **tagline** (without #) and adds it to the bot's database.",
                        false)
                .addField("/ranking",
                        "Displays a ranking list of all registered server members. You can switch between Solo Queue and Flex Queue via a dropdown menu.",
                        false)
                .addField("/delete <name> <tagline>",
                        "Removes a specific player from the server's stored list using their **name** and **tagline**.",
                        false)
                .setFooter("Use these commands wisely! Have fun!")
                .setTimestamp(Instant.now());
        return builder.build();
    }
}
