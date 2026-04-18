package com.example.discordbot;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import com.example.discordbot.web.WebControlServer;

import net.dv8tion.jda.api.JDA;
import net.dv8tion.jda.api.JDABuilder;
import net.dv8tion.jda.api.requests.GatewayIntent;

public class BotMain {
    public static void main(String[] args) {
        String token = resolveToken();

        if (token == null || token.isBlank()) {
            throw new IllegalStateException("Missing DISCORD_TOKEN. Set environment variable or add it to .env in the project root.");
        }

        try {
            JDA jda = JDABuilder.createDefault(token)
                    .enableIntents(
                            GatewayIntent.GUILD_MESSAGES,
                            GatewayIntent.MESSAGE_CONTENT,
                            GatewayIntent.GUILD_VOICE_STATES
                    )
                    .addEventListeners(new BotListener())
                    .build()
                    .awaitReady();

            new WebControlServer(jda).start();
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new RuntimeException("Bot startup interrupted.", e);
        }
    }

    private static String resolveToken() {
        String fromEnv = System.getenv("DISCORD_TOKEN");
        if (fromEnv != null && !fromEnv.isBlank()) {
            return fromEnv.trim();
        }

        Path envPath = Path.of(".env");
        if (!Files.exists(envPath)) {
            return null;
        }

        try {
            List<String> lines = Files.readAllLines(envPath);
            for (String rawLine : lines) {
                String line = rawLine.trim();
                if (line.isBlank() || line.startsWith("#") || !line.startsWith("DISCORD_TOKEN=")) {
                    continue;
                }

                String value = line.substring("DISCORD_TOKEN=".length()).trim();
                if (value.startsWith("\"") && value.endsWith("\"") && value.length() >= 2) {
                    value = value.substring(1, value.length() - 1);
                }
                if (value.isBlank()) {
                    return null;
                }
                return value;
            }
        } catch (IOException ignored) {
            return null;
        }

        return null;
    }
}
