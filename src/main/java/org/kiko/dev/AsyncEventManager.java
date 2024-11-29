package org.kiko.dev;

import jakarta.annotation.Nonnull;
import net.dv8tion.jda.api.events.GenericEvent;
import net.dv8tion.jda.api.hooks.EventListener;
import net.dv8tion.jda.api.hooks.IEventManager;

import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public class AsyncEventManager implements IEventManager {
    private final List<Object> listeners = new CopyOnWriteArrayList<>();
    private final ExecutorService executor = Executors.newCachedThreadPool();

    @Override
    public void register(@Nonnull Object listener) {
        listeners.add(listener);
    }

    @Override
    public void unregister(@Nonnull Object listener) {
        listeners.remove(listener);
    }

    @Nonnull
    @Override
    public List<Object> getRegisteredListeners() {
        return listeners;
    }

    @Override
    public void handle(@Nonnull GenericEvent event) {
        for (Object listener : listeners) {
            executor.submit(() -> {
                try {
                    if (listener instanceof EventListener) {
                        ((EventListener) listener).onEvent(event);
                    }
                } catch (Exception e) {
                    e.printStackTrace(); // Handle exceptions appropriately
                }
            });
        }
    }
}
