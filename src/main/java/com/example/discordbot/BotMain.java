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
    private static final String TOKEN_ENV_NAME = "DISCORD_TOKEN";
    private static final String ENV_FILE_NAME = ".env";

    public static void main(String[] args) {
        String token = resolveToken();

        if (token == null || token.isBlank()) {
            throw new IllegalStateException("Missing DISCORD_TOKEN. Set environment variable or add it to .env in the project root.");
        }

        try {
            JDA jda = buildAndAwaitJda(token);

            new WebControlServer(jda).start();
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new RuntimeException("Bot startup interrupted.", e);
        }
    }

    private static JDA buildAndAwaitJda(String token) throws InterruptedException {
        return JDABuilder.createDefault(token)
                .enableIntents(
                        GatewayIntent.GUILD_MESSAGES,
                        GatewayIntent.MESSAGE_CONTENT,
                        GatewayIntent.GUILD_VOICE_STATES
                )
                .addEventListeners(new BotListener())
                .build()
                .awaitReady();
    }

    private static String resolveToken() {
        String fromEnv = System.getenv(TOKEN_ENV_NAME);
        if (fromEnv != null && !fromEnv.isBlank()) {
            return fromEnv.trim();
        }

        Path envPath = Path.of(ENV_FILE_NAME);
        if (!Files.exists(envPath)) {
            return null;
        }

        try {
            List<String> lines = Files.readAllLines(envPath);
            for (String rawLine : lines) {
                String line = rawLine.trim();
                String expectedPrefix = TOKEN_ENV_NAME + "=";
                if (line.isBlank() || line.startsWith("#") || !line.startsWith(expectedPrefix)) {
                    continue;
                }

                String value = line.substring(expectedPrefix.length()).trim();
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
