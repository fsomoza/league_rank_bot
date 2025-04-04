package org.kiko.dev.game_scanner;

import net.dv8tion.jda.api.JDA;
import net.dv8tion.jda.api.entities.Guild;
import org.kiko.dev.ContextHolder;

import java.time.LocalTime;
import java.util.List;
import java.util.concurrent.*;

public class SharedTaskQueue {

    // This schedules the "master" job to run periodically
    private final ScheduledExecutorService scheduler = Executors.newScheduledThreadPool(1);

    // Bounded queue to store tasks
    // - LinkedBlockingQueue will block if full
    // - Adjust capacity to suit how many tasks you can buffer
    private final BlockingQueue<Runnable> taskQueue = new LinkedBlockingQueue<>(5);

    // Fixed-size pool of workers that will process tasks from the queue
    // - If you want 5 concurrent tasks at most, set pool size to 5

    //as now I am using a single thread for the game scanner because with just one thread I
    // already am hitting the rate limit ( personal level api key -> 100 requests every 2 minutes) of the riot api,
    // so it doesnt makes sense to be hitting it with multiple threads
    private final ExecutorService workerPool = Executors.newFixedThreadPool(1);


    private final GameScanner gameScanner;
    private final JDA jda;

    public SharedTaskQueue(JDA jda) {
        this.jda = jda;
        this.gameScanner = new GameScanner();

        // Start workers in the background
        startWorkerThreads();
    }

    /**
     * Start the scheduled job that periodically creates tasks and inserts them into the queue.
     *
     * For instance, run once a minute with no initial delay.
     */
    public void startScheduledTask() {
        scheduler.scheduleAtFixedRate(this::produceTasks, 0, 1, TimeUnit.MINUTES);
    }

    /**
     * Produce tasks for each guild, placing them in the queue.
     * If the queue is full, the put() call will block until space is available.
     */
    private void produceTasks() {
        System.out.println("Master task triggered at: " + LocalTime.now());

        // Get all guilds
        List<Guild> guilds = jda.getGuilds();

        for (Guild guild : guilds) {
            //skip the guilds that are uses to contain the emojis
            if(guild.getName().contains("emoji-champions")) continue;
            try {
                // Create a Runnable that processes this guild
                Runnable guildTask = createGuildTask(guild);

                // Insert the task into the queue (blocking if the queue is full)
                taskQueue.put(guildTask);

            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                System.err.println("Producer was interrupted when adding tasks to queue: " + e.getMessage());
                break; // optional: decide how you want to handle interruption
            }
        }

        System.out.println("Master task completed iteration at: " + LocalTime.now());
    }

    /**
     * Create a Runnable (or Callable) for processing a single guild.
     */
    private Runnable createGuildTask(Guild guild) {
        return () -> {
            long startTime = System.currentTimeMillis();  // Start timing

            try {
                System.out.println("Started guild task for " + guild.getName());

                // Set context
                ContextHolder.setGuild(guild);
                ContextHolder.setGuildId(guild.getId());

                // The heavy logic
                gameScanner.checkWhoInGame();

            } catch (Exception e) {
                System.err.println("Error during guild task: " + e.getMessage());
            } finally {
                ContextHolder.clear();
                long endTime = System.currentTimeMillis(); // Stop timing
                long duration = endTime - startTime;
                System.out.println("Completed guild " + guild.getName() + " within " + duration + " ms.");
            }
        };
    }


    /**
     * Start background worker threads that pull tasks from the queue and execute them.
     *
     * We'll submit a fixed number of "forever-running" consumers to the workerPool.
     */
    private void startWorkerThreads() {
        // We want each thread to run an infinite loop,
        // polling tasks from the queue and executing them
        for (int i = 0; i < 1; i++) {
            workerPool.submit(() -> {
                while (!Thread.currentThread().isInterrupted()) {
                    try {
                        // Take blocks until a task is available
                        Runnable task = taskQueue.take();
                        // Execute the task
                        task.run();
                    } catch (InterruptedException e) {
                        // Worker was interrupted (e.g., on shutdown)
                        Thread.currentThread().interrupt();
                        break;
                    }
                }
            });
        }
    }

    /**
     * Shutdown logic for both the scheduler and the worker pool.
     */
    public void stop() {
        // Stop scheduling new tasks
        scheduler.shutdown();
        try {
            if (!scheduler.awaitTermination(5, TimeUnit.SECONDS)) {
                scheduler.shutdownNow();
            }
        } catch (InterruptedException e) {
            scheduler.shutdownNow();
            Thread.currentThread().interrupt();
        }

        // Shutdown worker pool
        workerPool.shutdownNow(); // interrupt the forever loops
        try {
            if (!workerPool.awaitTermination(5, TimeUnit.SECONDS)) {
                workerPool.shutdownNow();
            }
        } catch (InterruptedException e) {
            workerPool.shutdownNow();
            Thread.currentThread().interrupt();
        }
    }
}
