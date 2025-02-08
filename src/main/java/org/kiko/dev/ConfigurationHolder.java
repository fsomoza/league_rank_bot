package org.kiko.dev;

import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.Arrays;
import java.util.Properties;

public class ConfigurationHolder {

    private static final Properties PROPERTIES = new Properties();

    static {
        boolean isDev = Arrays.asList(System.getProperty("sun.java.command").split(" ")).contains("dev");

        if (isDev) {
            // Development mode - load from inside JAR
            try (InputStream input = ConfigurationHolder.class.getClassLoader().getResourceAsStream("application.properties")) {
                if (input == null) {
                    throw new RuntimeException("Unable to find application.properties in JAR");
                }
                PROPERTIES.load(input);
                System.out.println("Loaded properties from JAR (dev mode)");
            } catch (IOException ex) {
                throw new RuntimeException("Error loading application.properties from JAR", ex);
            }
        } else {
            // Production mode - load from external file
            try (FileInputStream input = new FileInputStream("application.properties")) {
                PROPERTIES.load(input);
                System.out.println("Loaded properties from external file");
            } catch (IOException ex) {
                throw new RuntimeException("Error loading external application.properties", ex);
            }
        }
    }

    private ConfigurationHolder() {
        // Private constructor to prevent instantiation
    }

    /**
     * Retrieves the value of a property from the loaded properties file.
     *
     * @param key Property key to look up.
     * @return Property value, or null if not found.
     */
    public static String getProperty(String key) {
        return PROPERTIES.getProperty(key);
    }

    // Optionally, you can provide convenience methods for common keys:
    public static String getGuildId() {
        return getProperty("discord.guild.id");
    }

    public static String getChannelId() {
        return getProperty("discord.channel.id");
    }

//    public static String isDevEnv(){
//        retuA
//    }

}
