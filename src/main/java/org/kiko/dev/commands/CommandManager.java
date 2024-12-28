package org.kiko.dev.commands;

import net.dv8tion.jda.api.events.interaction.command.SlashCommandInteractionEvent;

import java.util.HashMap;
import java.util.Map;

public class CommandManager {
    private final Map<String, Command> commands = new HashMap<>();

    public void registerCommand(Command command) {
        commands.put(command.getName(), command);
    }

    public void handleCommand(SlashCommandInteractionEvent event) {
        Command command = commands.get(event.getName());
        if (command != null) {
            command.execute(event);
        } else {
            event.reply("Unknown command.").setEphemeral(true).queue();
        }
    }
}
