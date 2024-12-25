package org.kiko.dev;


import net.dv8tion.jda.api.EmbedBuilder;
import net.dv8tion.jda.api.JDA;
import net.dv8tion.jda.api.JDABuilder;
import net.dv8tion.jda.api.entities.Guild;
import net.dv8tion.jda.api.entities.MessageEmbed;
import net.dv8tion.jda.api.entities.channel.concrete.TextChannel;
import net.dv8tion.jda.api.events.guild.GuildJoinEvent;
import net.dv8tion.jda.api.events.interaction.command.SlashCommandInteractionEvent;
import net.dv8tion.jda.api.events.interaction.component.StringSelectInteractionEvent;
import net.dv8tion.jda.api.hooks.ListenerAdapter;
import net.dv8tion.jda.api.interactions.commands.DefaultMemberPermissions;
import net.dv8tion.jda.api.interactions.commands.OptionType;
import net.dv8tion.jda.api.interactions.commands.build.Commands;
import net.dv8tion.jda.api.interactions.components.ActionRow;
import net.dv8tion.jda.api.interactions.components.selections.StringSelectMenu;
import net.dv8tion.jda.api.requests.GatewayIntent;
import net.dv8tion.jda.api.utils.MemberCachePolicy;
import net.dv8tion.jda.api.utils.cache.CacheFlag;

import javax.security.auth.login.LoginException;
import java.awt.*;
import java.time.Duration;
import java.time.Instant;
import java.util.List;

public class ReminderBot extends ListenerAdapter {

    public final RankService rankService;

    private static final String PREFIX = "!rank";


    private JDA jda;  // Instance variable to hold the JDA object

    public ReminderBot(JDA jda) {
        this.jda = jda;
        this.rankService = new RankService(jda);
    }


    public static void main(String[] args) throws LoginException, InterruptedException {

        JDA jda = JDABuilder.createDefault(ConfigurationHolder.getProperty("discord.bot.token"))
                .setEventManager(new AsyncEventManager())
                .enableIntents(GatewayIntent.GUILD_MEMBERS, GatewayIntent.GUILD_PRESENCES, GatewayIntent.MESSAGE_CONTENT)
                .setMemberCachePolicy(MemberCachePolicy.ALL)
                .enableCache(CacheFlag.ACTIVITY)
                .build();


        // Wait until the bot is ready
        jda.awaitReady();

        // Create an instance of ReminderBot with the JDA object
        ReminderBot reminderBot = new ReminderBot(jda);

        // Add the event listener
        jda.addEventListener(reminderBot);

        List<Guild> guilds = jda.getGuilds();
        for (Guild guild : guilds) {

            guild.upsertCommand(Commands.slash("rank", "Get a player's ranked information")
                    .addOption(OptionType.STRING, "name", "Player's name", true)
                    .addOption(OptionType.STRING, "tagline", "Player's tagline without #", true)
                    .setDefaultPermissions(DefaultMemberPermissions.ENABLED)
            ).queue();

            guild.upsertCommand(Commands.slash("ranking", "Ranking list of server members")
                    .setDefaultPermissions(DefaultMemberPermissions.ENABLED)
            ).queue();


            System.out.println("Guild Name: " + guild.getName() + ", Guild ID: " + guild.getId());
            List<TextChannel> textChannels = guild.getTextChannels();
            for (TextChannel channel : textChannels) {
                System.out.println("Text Channel Name: " + channel.getName() + ", Channel ID: " + channel.getId());
            }

            if (guild.getId().equals("1310689513894318121")) {
                guild.upsertCommand(Commands.slash("broadcast", "Send an announcement to all servers")
                        .addOption(OptionType.STRING, "message", "The message to broadcast", true)
                        .addOption(OptionType.STRING, "type", "Announcement type (update/maintenance)", true)
                        .setDefaultPermissions(DefaultMemberPermissions.ENABLED)
                ).queue(
                        success -> System.out.println("Registered /broadcast command for admin guild"),
                        error -> System.err.println("Failed to register /broadcast command for admin guild")
                );

                guild.upsertCommand(Commands.slash("update", "update")
                        .setDefaultPermissions(DefaultMemberPermissions.ENABLED)
                ).queue(
                        success -> System.out.println("Bien"),
                        error -> System.err.println("Mal" + error.getMessage())
                );
            }


        }

        // Replace with your target guild and channel IDs
        String guildId = "YOUR_GUILD_ID";
        String channelId = "YOUR_CHANNEL_ID";

        // Register slash commands globally
//        jda.updateCommands().addCommands(
//                Commands.slash("rank", "Get a player's ranked information")
//                        .addOption(OptionType.STRING, "name", "Player's name", true)
//                        .addOption(OptionType.STRING, "tagline", "Player's tagline without #)", true)
//                        .setDefaultPermissions(DefaultMemberPermissions.ENABLED),
//                Commands.slash("ranking", "Ranking list of server members")
//                        .setDefaultPermissions(DefaultMemberPermissions.ENABLED)
//
//        ).queue();

        MyScheduledTask scheduledTask = new MyScheduledTask(reminderBot.rankService, jda);
        scheduledTask.startScheduledTask();

        // Add shutdown hook to stop the scheduler gracefully
        Runtime.getRuntime().addShutdownHook(new Thread(scheduledTask::stopScheduler));

    }


    @Override
    public void onSlashCommandInteraction(SlashCommandInteractionEvent event) {
        try {
            // Initialize ContextHolder with guildId
            String guildId = event.getGuild() != null ? event.getGuild().getId() : null;
            ContextHolder.setGuildId(guildId);

            // Handle the command
            handleCommand(event);
        } finally {
            // Ensure the ThreadLocal is cleared to prevent memory leaks
            ContextHolder.clear();
        }
    }


    private void handleCommand(SlashCommandInteractionEvent event) {
        //print the time
        // Record the start time
        Instant start = Instant.now();
        switch (event.getName()) {
            case "hello":
                event.reply("Hello! I am your friendly bot!").queue();
                break;
            case "rank":
                String name = event.getOption("name").getAsString();
                String tagline = event.getOption("tagline").getAsString();

                event.deferReply().queue(); // For longer operations

                try {
                    MessageEmbed rank = rankService.getPlayerInformation(name, tagline);
                    event.getHook().sendMessageEmbeds(rank).queue();
//                    event.getHook().sendMessage(rank).queue();
                } catch (IllegalArgumentException e) {
                    System.out.println(e.getMessage());
                    event.getHook().sendMessage(e.getMessage()).queue();
                } catch (Exception e) {
                    System.out.println(e.getMessage());
                    event.getHook().sendMessage("Error getting rank information: " + e.getMessage()).queue();
                }
                break;
            case "ranking":
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
                break;
            case "broadcast":
                if (!event.getGuild().getId().equals("1310689513894318121")) {
                    event.reply("This command can only be used from the authorized server.").setEphemeral(true).queue();
                    return;
                }

                String userId = event.getUser().getId();
                String allowedUserId = "164763054842576897";

                if (!userId.equals(allowedUserId)) {
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
                break;
            case "update":
                if (!event.getGuild().getId().equals("1310689513894318121")) {
                    event.reply("This command can only be used from the authorized server.").setEphemeral(true).queue();
                    break;
                }

                String updateUserId = event.getUser().getId();
                String updateAllowedUserId = "164763054842576897";

                if (!updateUserId.equals(updateAllowedUserId)) {
                    event.reply("You don't have permission to use this command.")
                            .setEphemeral(true) // Makes the response only visible to the command user
                            .queue();
                    break;
                }

                event.deferReply().queue(); // For longer operations
                try {
                    rankService.actualizarInfo();
                    event.getHook().sendMessage("Actualización de datos realizada con éxito!").setEphemeral(true).queue();
                } catch (Exception e) {
                    event.getHook().sendMessage("Error actualizando datos: " + e.getMessage()).setEphemeral(true).queue();
                }
                break;
        }

        Instant end = Instant.now();
        // Calculate the duration
        Duration duration = Duration.between(start, end);
        long millis = duration.toMillis();

        // Log the duration
        System.out.println("Rank command executed in " + millis + " ms");
    }


    @Override
    public void onGuildJoin(GuildJoinEvent event) {
        Guild guild = event.getGuild();
        registerCommands(guild);
        System.out.println("Registered commands for new guild: " + guild.getName());
    }

    private void registerCommands(Guild guild) {
        guild.upsertCommand(Commands.slash("rank", "Get a player's ranked information")
                .addOption(OptionType.STRING, "name", "Player's name", true)
                .addOption(OptionType.STRING, "tagline", "Player's tagline without #", true)
                .setDefaultPermissions(DefaultMemberPermissions.ENABLED)
        ).queue(
                success -> System.out.println("Registered /rank command for guild: " + guild.getName()),
                error -> System.err.println("Failed to register /rank command for guild: " + guild.getName())
        );

        guild.upsertCommand(Commands.slash("ranking", "Ranking list of server members")
                .setDefaultPermissions(DefaultMemberPermissions.ENABLED)
        ).queue(
                success -> System.out.println("Registered /ranking command for guild: " + guild.getName()),
                error -> System.err.println("Failed to register /ranking command for guild: " + guild.getName())
        );
    }

    private MessageEmbed createBroadcastEmbed(String message, String type) {
        EmbedBuilder builder = new EmbedBuilder();

        switch (type) {
            case "update":
                builder.setTitle("🚀 Actualización de versión")
                        .setColor(Color.BLUE);
                break;
            case "maintenance":
                builder.setTitle("🔧 Mantenimiento")
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

    @Override
    public void onStringSelectInteraction(StringSelectInteractionEvent event) {
        // Initialize ContextHolder with guildId
        String guildId = event.getGuild() != null ? event.getGuild().getId() : null;
        ContextHolder.setGuildId(guildId);
        if (event.getComponentId().equals("queue-selector")) {
            event.deferEdit().queue();
            String selectedQueue = event.getValues().get(0); // SOLO or FLEX

            try {
                MessageEmbed updatedEmbed = rankService.getRankedPlayerListEmbed(selectedQueue);
                event.getHook().editOriginalEmbeds(updatedEmbed).queue();
            } catch (Exception e) {
                event.getHook().sendMessage("Error updating rankings: " + e.getMessage()).queue();
            }
        }
    }

    private void broadcastToAllServers(MessageEmbed embed) {
        for (Guild guild : jda.getGuilds()) {
            // Try to send to the first available text channel
            List<TextChannel> channels = guild.getTextChannels();
            for (TextChannel channel : channels) {
                if (channel.getName().equals("game_scanner")) {
                    channel.sendMessageEmbeds(embed)
                            .queue(
                                    success -> System.out.println("Broadcast sent to " + guild.getName()),
                                    error -> System.err.println("Failed to send broadcast to " + guild.getName())
                            );
                    break;  // Break after sending to first available channel
                }
            }
        }
    }




    public static void sendMessageToChannel(JDA jda, String guildId, String channelId, String message) {
        // Get the guild by its ID
        Guild guild = jda.getGuildById(guildId);

        if (guild != null) {
            // Get the text channel by its ID within the guild
            TextChannel channel = guild.getTextChannelById(channelId);

            if (channel != null) {
                // Send the message to the channel
                channel.sendMessage(message).queue();
            } else {
                System.out.println("Channel with ID " + channelId + " not found in guild " + guildId + "!");
            }
        } else {
            System.out.println("Guild with ID " + guildId + " not found!");
        }
    }



//    @Override
//    public void onUserActivityStart(UserActivityStartEvent event) {
//        Member member = event.getMember();
//        Activity activity = event.getNewActivity();
//        RichPresence richPresence = activity.asRichPresence();
//
//        System.out.printf(richPresence.getDetails() + "%n");
//        System.out.printf("%s started activity: %s%n", member.getEffectiveName(), activity.getName());
//        // Implement your desired action here
//    }
//
//    @Override
//    public void onUserActivityEnd(UserActivityEndEvent event) {
//        Member member = event.getMember();
//        Activity activity = event.getOldActivity();
//        System.out.printf("%s stopped activity: %s%n", member.getEffectiveName(), activity.getName());
//        // Implement your desired action here
//    }

}
