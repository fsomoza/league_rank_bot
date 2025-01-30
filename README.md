# LeagueRankBot - README

Welcome to the **LeagueRankBot** project. This discord bot is built using the Java Discord API (JDA) and interacts with Riot Games’ League of Legends APIs to track and report player ranks, ongoing matches, and recently finished matches. It also stores information in a MongoDB database.

## Overview

**LeagueRankBot** is designed to:

- **Manage player registrations**: Add or remove players by their Riot ID and tagline.
- **Check in-progress and completed matches**: Scans every minute to detect:
  - Which registered players are currently in a match.
  - Which matches have recently completed.
- **Display ranks and match results**: Sends status updates and match summaries in designated Discord text channels.
- **Provide slash commands for ease of use**: (`/rank`, `/ranking`, `/add`, `/delete`, etc.).

## Project Structure

This project includes:

- **Main Bot Class (`LeagueRankBotMain`)**: The entry point of the bot that initializes and runs the bot.
- **Scheduler/Queue (`SharedTaskQueue`)**: Handles scanning tasks at regular intervals.
- **Match Scanner (`GameScanner`)**: Scans for ongoing and completed matches for registered players.
- **Commands**: 
  - `RankCommand`: Displays the rank of a registered player.
  - `AddCommand`: Adds a player to the bot's tracking system.
  - `DeleteCommand`: Removes a player from the bot's tracking system.
- **Riot API Adapter (`RiotApiAdapter`)**: Handles calls to the Riot API to fetch player data and match details.
- **MongoDB Adapter (`MongoDbAdapter`)**: Handles database interactions for storing player and match data.
- **Configuration Holders**:
  - `ConfigurationHolder`: Stores environment data like bot token, API keys, and other configurations.
  - `ContextHolder`: Stores guild-specific information like guild IDs and channel IDs.

## Requirements

- Java 17 or higher.
- MongoDB instance (can be local or hosted).
- JDA library for Java.
- Riot Games API key.


# Key Classes

Below is an overview of the key classes in this League of Legends rank-tracking bot application. Each class is described with its primary responsibilities and how it interacts with other components.

---

## 1. LeagueRankBotMain

- **Purpose**: Acts as the entry point of the application.
- **Responsibilities**:
  - Initializes the Discord bot using the relevant token from `ConfigurationHolder`.
  - Registers slash commands through `CommandManager`.
  - Sets up event listeners for Discord events.
  - Configures scheduling for periodic tasks via `SharedTaskQueue`.
- **Key Interactions**:
  - Collaborates with `CommandManager` to handle incoming commands.
  - Depends on `ConfigurationHolder` for obtaining necessary configuration values (e.g., bot token).
  - Utilizes `SharedTaskQueue` for running periodic scanning or updates.

---

## 2. CommandManager

- **Purpose**: Routes incoming slash commands to the appropriate command handler.
- **Responsibilities**:
  - Receives commands from Discord and determines which handler should process them.
  - Ensures commands are executed with correct parameters and context.
  - Acts as a central dispatcher to keep individual command logic separate from the main class.
- **Key Interactions**:
  - Called by `LeagueRankBotMain` when a command is triggered.
  - Communicates with various service classes (e.g., `RankService`) to fulfill command requests.

---

## 3. GameScanner

- **Purpose**: Core logic for detecting in-progress and recently completed matches.
- **Responsibilities**:
  - Periodically scans player data from MongoDB to identify those who might be in a current game.
  - Uses `RiotApiAdapter` to check the status of ongoing matches.
  - Determines whether a match has just concluded and triggers subsequent rank updates or notifications.
- **Key Interactions**:
  - Fetches player data from `MongoDbAdapter`.
  - Invokes `RiotApiAdapter` to communicate with Riot endpoints.
  - May use `SharedTaskQueue` to schedule routine scanning tasks.

---

## 4. SharedTaskQueue

- **Purpose**: Manages periodic tasks and ensures concurrency control.
- **Responsibilities**:
  - Wraps a `ScheduledExecutorService` to run tasks at a fixed interval (by default, every minute).
  - Maintains a bounded queue to prevent excessive task submission.
  - Processes tasks using a fixed-size thread pool to respect rate limits.
- **Key Interactions**:
  - Utilized by `LeagueRankBotMain` to schedule scanning or other repeating tasks.
  - Employed by classes like `GameScanner` to ensure tasks do not overwhelm the system.

---

## 5. RiotApiAdapter

- **Purpose**: Interfaces with Riot’s various endpoints (Account API, Summoner API, Match API, etc.).
- **Responsibilities**:
  - Provides methods to retrieve player information: PUUIDs, match history, current game data, and rank details.
  - Implements a basic rate-limiting strategy to prevent hitting Riot’s rate limit restrictions.
  - Abstracts the complexity of Riot API calls from other services.
- **Key Interactions**:
  - Called by `GameScanner` to retrieve match details.
  - Communicates with `RankService` for fetching and updating rank-related data.
  - References configuration values (e.g., Riot API key) from `ConfigurationHolder`.

---

## 6. MongoDbAdapter

- **Purpose**: Manages all database operations involving MongoDB.
- **Responsibilities**:
  - Provides a singleton connection to MongoDB.
  - Allows storing and retrieving player data, rank information, and records of in-progress games.
  - Simplifies database access by providing well-defined methods for common queries.
- **Key Interactions**:
  - Accessed by `GameScanner` to fetch players to scan.
  - Used by `RankService` for retrieving and persisting rank updates.

---

## 7. ConfigurationHolder

- **Purpose**: Central source for configuration and property values.
- **Responsibilities**:
  - Stores the Discord bot token, Riot API key, Guild/Channel IDs, and other environment-dependent settings.
  - Ensures consistent access to configuration data across the application.
- **Key Interactions**:
  - Queried by `LeagueRankBotMain` for the Discord bot token.
  - Utilized by `RiotApiAdapter` for the Riot API key and other environment-specific properties.

---

## 8. ContextHolder

- **Purpose**: Holds the current context (e.g., guild ID) in a thread-local manner.
- **Responsibilities**:
  - Avoids the need to pass context information (like Guild ID) across multiple method calls explicitly.
  - Provides thread-safe access to context for classes that need it during request handling.
- **Key Interactions**:
  - Used by various command or service classes where guild context is required.
  - Often set by `LeagueRankBotMain` or `CommandManager` when a new command request is received.

---

## 9. RankService

- **Purpose**: Encapsulates the logic for rank-related functionality.
- **Responsibilities**:
  - Generates rank lists and corresponding Discord embed messages.
  - Updates rank information in the database.
  - Synchronizes rank changes with Discord if needed (e.g., roles or announcements).
- **Key Interactions**:
  - Retrieves and stores rank data through `MongoDbAdapter`.
  - Fetches additional information from `RiotApiAdapter` if rank details need to be updated.
  - Called by `CommandManager` or other classes when rank details are requested.

---

**In summary**, these classes collaborate to provide a functional Discord bot that monitors League of Legends matches, tracks player ranks, and communicates results back to Discord. Each class has well-defined responsibilities to maintain modularity, readability, and maintainability throughout the system.
