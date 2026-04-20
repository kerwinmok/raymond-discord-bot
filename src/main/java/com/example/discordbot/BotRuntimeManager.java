package com.example.discordbot;

import net.dv8tion.jda.api.JDA;
import net.dv8tion.jda.api.JDABuilder;
import net.dv8tion.jda.api.requests.GatewayIntent;

public class BotRuntimeManager {
    private final String token;
    private final Object lock;

    private volatile JDA jda;

    public BotRuntimeManager(String token) {
        this.token = token;
        this.lock = new Object();
    }

    public String startBot() {
        synchronized (lock) {
            if (isOnlineUnsafe()) {
                return "Bot is already online.";
            }

            try {
                JDA started = JDABuilder.createDefault(token)
                        .enableIntents(
                                GatewayIntent.GUILD_MESSAGES,
                                GatewayIntent.MESSAGE_CONTENT,
                                GatewayIntent.GUILD_VOICE_STATES
                        )
                        .addEventListeners(new BotListener())
                        .build()
                        .awaitReady();

                this.jda = started;
                return "Bot is now online.";
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                return "Bot startup was interrupted.";
            } catch (Exception e) {
                return "Failed to start bot: " + e.getMessage();
            }
        }
    }

    public String stopBot() {
        synchronized (lock) {
            JDA current = this.jda;
            if (current == null || !isOnlineUnsafe()) {
                this.jda = null;
                return "Bot is already offline.";
            }

            current.shutdownNow();
            this.jda = null;
            return "Bot is now offline.";
        }
    }

    public boolean isOnline() {
        synchronized (lock) {
            return isOnlineUnsafe();
        }
    }

    public String getStatusLabel() {
        synchronized (lock) {
            JDA current = this.jda;
            if (current == null) {
                return "OFFLINE";
            }

            return current.getStatus().name();
        }
    }

    public JDA getJda() {
        return this.jda;
    }

    private boolean isOnlineUnsafe() {
        JDA current = this.jda;
        if (current == null) {
            return false;
        }

        return current.getStatus() == JDA.Status.CONNECTED;
    }
}
