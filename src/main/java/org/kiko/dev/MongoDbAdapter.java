package org.kiko.dev;

import com.mongodb.ConnectionString;
import com.mongodb.MongoClientSettings;
import com.mongodb.client.MongoClient;
import com.mongodb.client.MongoClients;
import com.mongodb.client.MongoDatabase;

public class MongoDbAdapter {



    private final MongoClient mongoClient;
    private final MongoDatabase database;

    private final String CONNECTION_STRING;
    private final String DATABASE_NAME;

    // Private constructor to prevent instantiation
    private MongoDbAdapter() {
        this.CONNECTION_STRING = ConfigurationHolder.getProperty("mongodb.connection.string");
        this.DATABASE_NAME = ConfigurationHolder.getProperty("mongodb.database.name");


        ConnectionString connectionString = new ConnectionString(CONNECTION_STRING);
        MongoClientSettings settings = MongoClientSettings.builder()
                .applyConnectionString(connectionString)
                .build();
        this.mongoClient = MongoClients.create(settings);
        this.database = mongoClient.getDatabase(DATABASE_NAME);
    }

    // Holder class for lazy-loaded singleton instance
    private static class Holder {
        private static final MongoDbAdapter INSTANCE = new MongoDbAdapter();
    }

    // Public method to provide access to the singleton instance
    public static MongoDbAdapter getInstance() {
        return Holder.INSTANCE;
    }

    public MongoDatabase getDatabase() {
        return database;
    }

    // Method to close the MongoClient connection
    public void close() {
        mongoClient.close();
    }
}
