package org.kiko.dev;

import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

public class MyScheduledTask {

    private final ScheduledExecutorService scheduler = Executors.newScheduledThreadPool(1);
    private final RankService rankService;

    public MyScheduledTask(RankService rankService ) {
        this.rankService = rankService;
    }

    public void startScheduledTask() {
        Runnable task = this::executeTask;

        // Schedule the task to run every 2 minutes with an initial delay of 0
        scheduler.scheduleAtFixedRate(task, 0, 30, TimeUnit.SECONDS);
    }

    private void executeTask() {
        // Your method logic here
        System.out.println("Task executed at: " + java.time.LocalTime.now());
        try {
            rankService.checkWhoInGame();;
        } catch (Exception e) {
            e.printStackTrace();
        }

    }

    public void stopScheduler() {
        scheduler.shutdown();
        try {
            if (!scheduler.awaitTermination(5, TimeUnit.SECONDS)) {
                scheduler.shutdownNow();
            }
        } catch (InterruptedException e) {
            scheduler.shutdownNow();
        }
    }
}
