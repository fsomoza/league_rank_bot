package org.kiko.dev.commands;

import net.dv8tion.jda.api.events.interaction.command.SlashCommandInteractionEvent;
import org.kiko.dev.ContextHolder;
import org.kiko.dev.RankService;

public class UpdateCommand implements Command {

    private final RankService rankService;

    private static final String ADMIN_GUILD_ID = "1310689513894318121";
    private static final String ALLOWED_USER_ID = "164763054842576897";

    public UpdateCommand(RankService rankService) {
        this.rankService = rankService;
    }

    @Override
    public String getName() {
        return "update";
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

        event.deferReply().queue(); // For longer operations
        try {
//            rankService.updatePlayersInfo();
            rankService.updateChampionsWithEmojiIds();
            event.getHook().sendMessage("Data update completed successfully!").setEphemeral(true).queue();
        } catch (Exception e) {
            event.getHook().sendMessage("Error updating data: " + e.getMessage()).setEphemeral(true).queue();
        }finally {
            ContextHolder.clear();
        }
    }
}
