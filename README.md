# Raymond Discord Bot (Java)

I built this project as a Discord music and utility bot in Java using JDA + LavaPlayer, with a local browser control panel so I can send bot messages and manage runtime quickly.

## What this bot does

- Plays YouTube audio links with `!play <url>`
- Skips track with `!skip`
- Stops playback and clears queue with `!stop`
- Sends a bot message with `!say <message>` (permission-gated)
- Gets/sets guild volume with `!volume` and `!volume <0-150>`
- Provides local web controls for sending message + optional image embed
- Lets me start/stop the bot from the web panel

## Core architecture

- `BotMain`: app entry point, token resolution (`DISCORD_TOKEN` or `.env`)
- `BotRuntimeManager`: boot/shutdown lifecycle for JDA
- `BotListener`: command parsing and command handlers
- `audio/*`: guild-scoped player manager, queue scheduler, send handler
- `web/WebControlServer`: local HTTP panel on loopback (`127.0.0.1`)

## How I designed it

### Command handling

In `BotListener`, I parse commands from guild messages only and ignore bot/self noise. I normalize input before playback so malformed command text is less likely to break command routing.

### Audio behavior

In `PlayerManager`, I keep one `GuildMusicManager` per guild. This keeps queue and volume isolated per server while sharing one underlying LavaPlayer manager.

### Local control panel

In `WebControlServer`, I host a local-only HTTP server with endpoints for send/start/stop operations. I also support optional panel key protection through `BOT_WEB_KEY`.

## Hard parts I worked through

- Avoiding repeated voice reconnect flapping while Discord is still in connecting states.
- Building safe fallback flows in the web panel when channel IDs, volume, or runtime status are missing.
- Keeping guild volume behavior bounded and predictable (`0` to `150`) across both chat commands and web form submissions.

## Requirements

- Java 17+
- Maven 3.9+
- Discord bot token
- Discord Developer Portal message content intent enabled

## Setup

Set the token in either of these ways:

1. Environment variable:

```powershell
$env:DISCORD_TOKEN="your-token"
```

2. `.env` file in repo root:

```text
DISCORD_TOKEN=your-token
```

Optional web settings:

- `BOT_WEB_PORT` (default `8080`)
- `BOT_DEFAULT_CHANNEL_ID` (used when channel field is blank)
- `BOT_WEB_KEY` (require key in panel)

## Run

```powershell
mvnw.cmd exec:java -Dexec.mainClass=com.example.discordbot.BotMain
```

Web panel:

- `http://127.0.0.1:8080`

## Build

```powershell
mvnw.cmd package
java -jar target/discord-music-bot-1.0.0.jar
```
