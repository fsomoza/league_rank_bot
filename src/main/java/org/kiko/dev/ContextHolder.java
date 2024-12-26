package org.kiko.dev;

import net.dv8tion.jda.api.entities.Guild;

public class ContextHolder {
    private static final ThreadLocal<String> guildId = new ThreadLocal<>();
    private static final ThreadLocal<Guild> guild = new ThreadLocal<>();

    /**
     * Sets the guildId for the current thread.
     *
     * @param id The guild ID to set.
     */
    public static void setGuildId(String id) {
        guildId.set(id);
    }

    /**
     * Retrieves the guildId for the current thread.
     *
     * @return The current thread's guild ID, or null if not set.
     */
    public static String getGuildId() {
        return guildId.get();
    }

    public static Guild getGuild() {
        return guild.get();
    }

    public static void setGuild(Guild guildd) {
       guild.set(guildd);
    }

    /**
     * Clears the guildId from the current thread to prevent memory leaks.
     */
    public static void clear() {
        guildId.remove();
        guild.remove();
    }
}
