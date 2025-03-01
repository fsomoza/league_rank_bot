package org.kiko.dev;

import com.fasterxml.jackson.core.JsonProcessingException;
import net.dv8tion.jda.api.JDA;
import net.dv8tion.jda.api.JDABuilder;
import net.dv8tion.jda.api.entities.Guild;
import net.dv8tion.jda.api.entities.MessageEmbed;
import net.dv8tion.jda.api.entities.channel.concrete.TextChannel;
import net.dv8tion.jda.api.events.guild.GuildJoinEvent;
import net.dv8tion.jda.api.events.interaction.command.SlashCommandInteractionEvent;
import net.dv8tion.jda.api.events.interaction.component.ButtonInteractionEvent;
import net.dv8tion.jda.api.events.interaction.component.StringSelectInteractionEvent;
import net.dv8tion.jda.api.hooks.ListenerAdapter;
import net.dv8tion.jda.api.interactions.commands.DefaultMemberPermissions;
import net.dv8tion.jda.api.interactions.commands.OptionType;
import net.dv8tion.jda.api.interactions.commands.build.Commands;
import net.dv8tion.jda.api.requests.GatewayIntent;
import net.dv8tion.jda.api.utils.MemberCachePolicy;
import net.dv8tion.jda.api.utils.cache.CacheFlag;
import org.kiko.dev.commands.*;
import org.kiko.dev.game_scanner.SharedTaskQueue;

import java.util.List;

public class LeagueRankBotMain extends ListenerAdapter {

  public final RankService rankService;
  private final CommandManager commandManager;
  private static final String PREFIX = "!rank";

  private JDA jda; // Instance variable to hold the JDA object

  public LeagueRankBotMain(JDA jda, CommandManager commandManager) {
    this.jda = jda;
    this.rankService = new RankService(jda);
    this.commandManager = commandManager;
  }

  public static void main(String[] args) throws Exception {

    JDA jda = JDABuilder.createDefault(ConfigurationHolder.getProperty("discord.bot.token"))
        .setEventManager(new AsyncEventManager())
        .enableIntents(GatewayIntent.GUILD_MEMBERS, GatewayIntent.GUILD_PRESENCES, GatewayIntent.MESSAGE_CONTENT)
        .setMemberCachePolicy(MemberCachePolicy.ALL)
        .enableCache(CacheFlag.ACTIVITY)
        .build();

    // Wait until the bot is ready
    jda.awaitReady();

    // Initialize CommandManager
    CommandManager commandManager = new CommandManager();

    // Create an instance of LeagueRankBotMain with the JDA object and
    // CommandManager
    LeagueRankBotMain leagueRankBotMain = new LeagueRankBotMain(jda, commandManager);

    leagueRankBotMain.rankService.doChecks();

    // Register commands
    leagueRankBotMain.registerCommandsForAllGuilds(jda);

    // Register individual command handlers
    commandManager.registerCommand(new RankCommand(leagueRankBotMain.rankService));
    commandManager.registerCommand(new AddCommand(leagueRankBotMain.rankService));
    commandManager.registerCommand(new DeleteCommand(leagueRankBotMain.rankService));
    commandManager.registerCommand(new HelpCommand());
    commandManager.registerCommand(new RankingCommand(leagueRankBotMain.rankService));
    commandManager.registerCommand(new BroadcastCommand(leagueRankBotMain.rankService, jda));
    commandManager.registerCommand(new UpdateCommand(leagueRankBotMain.rankService));

    // Add the event listener
    jda.addEventListener(leagueRankBotMain);

    // Register slash commands globally
    // jda.updateCommands().addCommands(
    // Commands.slash("rank", "Get a player's ranked information")
    // .addOption(OptionType.STRING, "name", "Player's name", true)
    // .addOption(OptionType.STRING, "tagline", "Player's tagline without #)", true)
    // .setDefaultPermissions(DefaultMemberPermissions.ENABLED),
    // Commands.slash("ranking", "Ranking list of server members")
    // .setDefaultPermissions(DefaultMemberPermissions.ENABLED)
    //
    // ).queue();

    SharedTaskQueue taskQueue = new SharedTaskQueue(jda);

    // Start producing tasks every minute
    taskQueue.startScheduledTask();

    // ... some time later ...
    // When shutting down the bot, stop everything
    Runtime.getRuntime().addShutdownHook(new Thread(() -> {
      taskQueue.stop();
    }));

  }

  /**
   * Registers commands for all existing guilds.
   *
   * @param jda The JDA instance.
   */
  private void registerCommandsForAllGuilds(JDA jda) {
    List<Guild> guilds = jda.getGuilds();
    for (Guild guild : guilds) {
      registerCommandsForGuild(guild);
    }
  }

  @Override
  public void onSlashCommandInteraction(SlashCommandInteractionEvent event) {
    try {
      // Initialize ContextHolder with guildId
      String guildId = event.getGuild() != null ? event.getGuild().getId() : null;
      ContextHolder.setGuildId(guildId);

      // Delegate command handling to CommandManager
      commandManager.handleCommand(event);
    } finally {
      // Ensure the ThreadLocal is cleared to prevent memory leaks
      ContextHolder.clear();
    }
  }

  @Override
  public void onGuildJoin(GuildJoinEvent event) {
    Guild guild = event.getGuild();
    registerCommandsForGuild(guild);
    System.out.println("Registered commands for new guild: " + guild.getName());
  }

  @Override
  public void onButtonInteraction(ButtonInteractionEvent event) {
    ContextHolder.setGuildId(event.getGuild().getId());
    String[] parts = event.getComponentId().split(":");
    event.deferReply(true).queue();
    try {
      switch (parts[0]) {
        case "goldGraph":
          rankService.generateGoldGraph(parts[1], event);
          break;
        case "dmgGraph":
          rankService.generateDmgGraph(parts[1], event);
          break;
        default:
          event.reply("Unknown button interaction type: " + parts[0]).queue();
          break;
      }
    } catch (ArrayIndexOutOfBoundsException e) {
      event.reply("Invalid button ID format").queue();
    } catch (Exception e) {
      e.printStackTrace();
      event.reply("An error occurred while processing your request").queue();
    }
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
      } finally {
        ContextHolder.clear();
      }
    }
  }

  /**
   * Registers commands for a specific guild.
   *
   * @param guild The guild where commands should be registered.
   */
  private void registerCommandsForGuild(Guild guild) {
    // Register /rank command
    guild.upsertCommand(Commands.slash("rank", "Get a player's ranked information")
        .addOption(OptionType.STRING, "name", "Player's name", true)
        .addOption(OptionType.STRING, "tagline", "Player's tagline without #", true)
        .setDefaultPermissions(DefaultMemberPermissions.ENABLED)).queue(
            success -> System.out.println("Registered /rank command for guild: " + guild.getName()),
            error -> System.err.println("Failed to register /rank command for guild: " + guild.getName()));

    // Register /ranking command
    guild.upsertCommand(Commands.slash("ranking", "Ranking list of server members")
        .setDefaultPermissions(DefaultMemberPermissions.ENABLED)).queue(
            success -> System.out.println("Registered /ranking command for guild: " + guild.getName()),
            error -> System.err.println("Failed to register /ranking command for guild: " + guild.getName()));

    // Register /add command
    guild.upsertCommand(Commands.slash("add", "Add a player to the bot's database")
        .addOption(OptionType.STRING, "name", "Player's name", true)
        .addOption(OptionType.STRING, "tagline", "Player's tagline without #", true)
        .setDefaultPermissions(DefaultMemberPermissions.ENABLED)).queue(
            success -> System.out.println("Registered /add command for guild: " + guild.getName()),
            error -> System.err.println("Failed to register /add command for guild: " + guild.getName()));

    // Register /help command
    guild.upsertCommand(Commands.slash("help", "Get help with the bot")
        .setDefaultPermissions(DefaultMemberPermissions.ENABLED)).queue(
            success -> System.out.println("Registered /help command for guild: " + guild.getName()),
            error -> System.err.println("Failed to register /help command for guild: " + guild.getName()));

    // Register /delete command
    guild.upsertCommand(Commands.slash("delete", "Delete a player from the bot's database")
        .addOption(OptionType.STRING, "name", "Player's name", true)
        .addOption(OptionType.STRING, "tagline", "Player's tagline without #", true)
        .setDefaultPermissions(DefaultMemberPermissions.ENABLED)).queue(
            success -> System.out.println("Registered /delete command for guild: " + guild.getName()),
            error -> System.err.println("Failed to register /delete command for guild: " + guild.getName()));

    // Additional commands for specific guilds
    if (guild.getId().equals("1310689513894318121")) {
      // Register /broadcast command
      guild.upsertCommand(Commands.slash("broadcast", "Send an announcement to all servers")
          .addOption(OptionType.STRING, "message", "The message to broadcast", true)
          .addOption(OptionType.STRING, "type", "Announcement type (update/maintenance)", true)
          .setDefaultPermissions(DefaultMemberPermissions.ENABLED)).queue(
              success -> System.out.println("Registered /broadcast command for admin guild"),
              error -> System.err.println("Failed to register /broadcast command for admin guild"));

      // Register /update command
      guild.upsertCommand(Commands.slash("update", "Update the bot")
          .setDefaultPermissions(DefaultMemberPermissions.ENABLED)).queue(
              success -> System.out.println("Registered /update command for guild: " + guild.getName()),
              error -> System.err.println("Failed to register /update command for guild: " + guild.getName()));
    }

    // Log guild and channel information (optional)
    System.out.println("Guild Name: " + guild.getName() + ", Guild ID: " + guild.getId());
    List<TextChannel> textChannels = guild.getTextChannels();
    for (TextChannel channel : textChannels) {
      System.out.println("Text Channel Name: " + channel.getName() + ", Channel ID: " + channel.getId());
    }
  }

  // @Override
  // public void onUserActivityStart(UserActivityStartEvent event) {
  // Member member = event.getMember();
  // Activity activity = event.getNewActivity();
  // RichPresence richPresence = activity.asRichPresence();
  //
  // System.out.printf(richPresence.getDetails() + "%n");
  // System.out.printf("%s started activity: %s%n", member.getEffectiveName(),
  // activity.getName());
  // // Implement your desired action here
  // }
  //
  // @Override
  // public void onUserActivityEnd(UserActivityEndEvent event) {
  // Member member = event.getMember();
  // Activity activity = event.getOldActivity();
  // System.out.printf("%s stopped activity: %s%n", member.getEffectiveName(),
  // activity.getName());
  // // Implement your desired action here
  // }

}
