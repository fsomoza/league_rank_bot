package org.kiko.dev;


import net.dv8tion.jda.api.JDA;
import net.dv8tion.jda.api.JDABuilder;
import net.dv8tion.jda.api.entities.Guild;
import net.dv8tion.jda.api.entities.channel.concrete.TextChannel;
import net.dv8tion.jda.api.events.guild.GuildJoinEvent;
import net.dv8tion.jda.api.events.interaction.command.SlashCommandInteractionEvent;
import net.dv8tion.jda.api.hooks.ListenerAdapter;
import net.dv8tion.jda.api.interactions.commands.DefaultMemberPermissions;
import net.dv8tion.jda.api.interactions.commands.OptionType;
import net.dv8tion.jda.api.interactions.commands.build.Commands;
import net.dv8tion.jda.api.requests.GatewayIntent;
import net.dv8tion.jda.api.utils.MemberCachePolicy;
import net.dv8tion.jda.api.utils.cache.CacheFlag;

import javax.security.auth.login.LoginException;
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
                    String rank = rankService.getPlayerRank(name, tagline);
                    event.getHook().sendMessage(rank).queue();
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
                    event.getHook().sendMessageEmbeds(rankService.getRankedPlayerListEmbed()).queue();
                } catch (Exception e) {
                    System.out.println(e.getMessage());
                    event.getHook().sendMessage("Error getting rank information: " + e.getMessage()).queue();
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


//    @Override
//    public void onSlashCommandInteraction(SlashCommandInteractionEvent event) {
//        //print the time
//        // Record the start time
//        Instant start = Instant.now();
//        switch (event.getName()) {
//            case "hello":
//                event.reply("Hello! I am your friendly bot!").queue();
//                break;
//            case "rank":
//                String name = event.getOption("name").getAsString();
//                String tagline = event.getOption("tagline").getAsString();
//
//                event.deferReply().queue(); // For longer operations
//
//
//                try {
//                    String rank = rankService.getPlayerRank(name, tagline);
//                    event.getHook().sendMessage(rank).queue();
//                } catch (IllegalArgumentException e) {
//                    event.getHook().sendMessage(e.getMessage()).queue();
//                } catch (Exception e) {
//                    event.getHook().sendMessage("Error getting rank information: " + e.getMessage()).queue();
//                }
//                break;
//            case "ranking":
//                event.deferReply().queue(); // For longer operations
//                try {
//                    event.getHook().sendMessageEmbeds(rankService.getRankedPlayerListEmbed()).queue();
//                } catch (Exception e) {
//                    event.getHook().sendMessage("Error getting rank information: " + e.getMessage()).queue();
//                }
//
//                break;
//        }
//
//        Instant end = Instant.now();
//        // Calculate the duration
//        Duration duration = Duration.between(start, end);
//        long millis = duration.toMillis();
//
//        // Log the duration
//        System.out.println("Rank command executed in " + millis + " ms");
//
//    }


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
//    public void onMessageReceived(MessageReceivedEvent event) {
//        if (event.getAuthor().isBot()) return;
//
//        String message = event.getMessage().getContentRaw();
//        if (message.equalsIgnoreCase("!hello")) {
//            event.getChannel().sendMessage("Hello! I am your friendly bot!").queue();
//        } else if (message.equalsIgnoreCase("!hotaru")) {
//            event.getChannel().sendMessage("El medio está rotando, no puede hacer nada!").queue();
//        } else if (message.equalsIgnoreCase("!santi")) {
//            event.getChannel().sendMessage("El jungler más pajero!").queue();
//        } else if (message.equalsIgnoreCase("!kiko")) {
//            event.getChannel().sendMessage("El putísimo Deft gallego, un carry natural, poco más que decir.").queue();
//        }else if (message.toLowerCase().startsWith(PREFIX.toLowerCase())) {
//
//            // Remove the command prefix and trim
//            String withoutPrefix = message.substring(PREFIX.length()).trim();
//
//            // Find the last occurrence of # to separate tagline
//            int taglineIndex = withoutPrefix.lastIndexOf("#");
//            if (taglineIndex == -1) {
//                System.out.println("Invalid format. Tagline must start with #");
//                return;
//            }
//
//            // Everything before the # is the name (trimmed to remove extra spaces)
//            String name = withoutPrefix.substring(0, taglineIndex).trim();
//
//            // Everything after the # is the tagline (including the #)
//            String tagline = withoutPrefix.substring(taglineIndex + 1).trim();
//
//            // Validate we have both parts
//            if (name.isEmpty() || tagline.equals("#")) {
//                System.out.println("Formato de comando invalido. Usa: !rank <nombre> <tag>");
//                event.getChannel().sendMessage("Formato de comando invalido. Usa: !rank <nombre> <tag>").queue();
//                return;
//            }
//
//            try {
//               String puuid = riotApiAdapter.getPuuid(name, tagline);
//               String encryptedSummonerId = riotApiAdapter.getEncryptedSummonerId(puuid);
//
//                event.getChannel().sendMessage(riotApiAdapter.getSoloQueueRank(encryptedSummonerId)).queue();
//               System.out.println(puuid);
//
//            } catch (Exception e) {
//                throw new RuntimeException(e);
//            }
//
//        }
//    }

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
