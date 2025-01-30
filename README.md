# LeagueRankBot - README

Welcome to the LeagueRankBot project. This bot is built using the Java Discord API (JDA) and interacts with Riot Games’ League of Legends APIs to track and report player ranks, ongoing matches, and recently finished matches. It also stores information in a MongoDB database.

1. Overview
LeagueRankBot is designed to:

Manage player registrations: Add or remove players by their Riot ID and tagline.
Check in-progress and completed matches: Scans every minute to detect:
Which registered players are currently in a match.
Which matches have recently completed.
Display ranks and match results: Sends status updates and match summaries in designated Discord text channels.
Provide slash commands for ease of use (/rank, /ranking, /add, /delete, etc.).
This project includes:

Main Bot Class (LeagueRankBotMain)
Scheduler/Queue to handle scanning tasks (SharedTaskQueue)
Match Scanner (GameScanner)
Commands (e.g., RankCommand, AddCommand, DeleteCommand, etc.)
Riot API Adapter (RiotApiAdapter) for calls to Riot endpoints
MongoDB Adapter (MongoDbAdapter) for database interactions
Configuration Holders (ConfigurationHolder, ContextHolder) for storing environment data (bot token, API keys, guild IDs, etc.).
