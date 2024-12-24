package org.kiko.dev;

import net.dv8tion.jda.api.JDA;
import net.dv8tion.jda.api.entities.Guild;

import java.time.LocalTime;
import java.util.List;
import java.util.concurrent.*;

public class MyScheduledTask {

    // This scheduler fires the "outer" periodic task every 1 minute
    private final ScheduledExecutorService scheduler = Executors.newScheduledThreadPool(1);

    // This executor handles concurrent guild tasks
    private final ExecutorService taskExecutor = Executors.newCachedThreadPool();

    private final RankService rankService;
    private final JDA jda;

    public MyScheduledTask(RankService rankService, JDA jda) {
        this.rankService = rankService;
        this.jda = jda;
    }

    public void startScheduledTask() {
        // Run the task every 1 minute with an initial delay of 0
        scheduler.scheduleAtFixedRate(this::executeTask, 0, 30, TimeUnit.SECONDS);
    }

    private void executeTask() {
        System.out.println("Master task triggered at: " + LocalTime.now());

        // Get all the guilds
        List<Guild> guilds = jda.getGuilds();

        // Process each guild
        for (Guild guild : guilds) {
            // Submit the processing to our concurrency pool
            Future<?> future = taskExecutor.submit(() -> {
                // The heavy/long-running logic for this guild
                try {
                    ContextHolder.setGuild(guild);
                    ContextHolder.setGuildId(guild.getId());
                    rankService.checkWhoInGame();
                } catch (Exception e) {
                    throw new RuntimeException(e);
                }
            });

            try {
                // Wait up to 5 seconds for this guild's task to finish
                future.get(5, TimeUnit.SECONDS);
                System.out.println("Completed guild " + guild.getName() + " within 5 seconds.");
            } catch (TimeoutException e) {
                // If we hit this, the task is still running in background
                System.err.println("Guild " + guild.getName() + " did not finish in 5 seconds. Moving on.");
            } catch (InterruptedException e) {
                // If the main thread is interrupted while waiting
                Thread.currentThread().interrupt();
                System.err.println("Master task was interrupted. " + e.getMessage());
            } catch (ExecutionException e) {
                // If the task threw an exception
                e.printStackTrace();
            }
        }

        System.out.println("Master task completed iteration at: " + LocalTime.now());
    }

    // Shutdown the schedulers
    public void stopScheduler() {
        scheduler.shutdown();
        try {
            if (!scheduler.awaitTermination(5, TimeUnit.SECONDS)) {
                scheduler.shutdownNow();
            }
        } catch (InterruptedException e) {
            scheduler.shutdownNow();
        }

        taskExecutor.shutdown();
        try {
            if (!taskExecutor.awaitTermination(5, TimeUnit.SECONDS)) {
                taskExecutor.shutdownNow();
            }
        } catch (InterruptedException e) {
            taskExecutor.shutdownNow();
        }
    }
}
