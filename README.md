# Discord Music Bot (Java)

Simple Discord bot in Java using JDA and LavaPlayer.

## Features

- `!play <youtube-url>` queues and plays audio
- `!skip` skips the current track
- `!stop` stops playback and clears the queue
- `!say <message>` sends a message as the bot (requires Manage Server permission)
- Local web panel to send message + image URL as the bot

## Requirements

- Java 17+
- Maven 3.9+
- A Discord bot token stored in the `DISCORD_TOKEN` environment variable
- In the Discord Developer Portal, enable the `MESSAGE CONTENT INTENT`

## Important

If you pasted your token into chat or source files, rotate it in the Discord Developer Portal before using this bot.

## Run

1. Set your environment variable:
   - PowerShell: `$env:DISCORD_TOKEN="your-new-token"`
2. Start the bot:
   - `mvnw.cmd exec:java -Dexec.mainClass=com.example.discordbot.BotMain`
3. Open the panel in your browser:
   - `http://127.0.0.1:8080`

## VS Code one-click Run/Debug

If you want Run/Debug on `BotMain.java` to just work every time, create a `.env` file in the project root:

`DISCORD_TOKEN=your-new-token`

Then click **Run** or **Debug** above `BotMain.java`. Stopping the debug session stops the bot process.

## Build jar

- `mvnw.cmd package`
- Run: `java -jar target/discord-music-bot-1.0.0.jar`

## Notes

For `!play` to work, the user must be in a voice channel and the bot must have permission to connect and speak.
This project includes a local Windows Maven wrapper script in [mvnw.cmd](mvnw.cmd) that downloads Maven automatically on first run.

## Web panel configuration (optional)

- `BOT_WEB_PORT` (default `8080`)
- `BOT_DEFAULT_CHANNEL_ID` (lets you leave Channel ID empty in the form)
- `BOT_WEB_KEY` (if set, the panel requires this key before sending)
