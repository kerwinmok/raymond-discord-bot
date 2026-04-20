package com.example.discordbot;

import org.jetbrains.annotations.NotNull;

import com.example.discordbot.audio.PlayerManager;

import net.dv8tion.jda.api.Permission;
import net.dv8tion.jda.api.Region;
import net.dv8tion.jda.api.audio.hooks.ConnectionStatus;
import net.dv8tion.jda.api.entities.Guild;
import net.dv8tion.jda.api.entities.GuildVoiceState;
import net.dv8tion.jda.api.entities.Member;
import net.dv8tion.jda.api.entities.channel.middleman.AudioChannel;
import net.dv8tion.jda.api.events.message.MessageReceivedEvent;
import net.dv8tion.jda.api.hooks.ListenerAdapter;

public class BotListener extends ListenerAdapter {
    private static final String PREFIX = "!";
    private static final Region PREFERRED_NA_REGION = Region.US_EAST;

    @Override
    public void onMessageReceived(@NotNull MessageReceivedEvent event) {
        if (event.getAuthor().isBot() || !event.isFromGuild()) {
            return;
        }

        String raw = event.getMessage().getContentRaw().trim();
        if (!raw.startsWith(PREFIX)) {
            return;
        }

        if (raw.startsWith("!http://") || raw.startsWith("!https://")) {
            event.getChannel().sendMessage("Use: !play <youtube-url>").queue();
            return;
        }

        if (raw.equals("!play") || raw.startsWith("!play ")) {
            handlePlay(event, getCommandArgument(raw, "!play"));
            return;
        }

        if (raw.equals("!say") || raw.startsWith("!say ")) {
            handleSay(event, getCommandArgument(raw, "!say"));
            return;
        }

        if (raw.equals("!volume") || raw.startsWith("!volume ")) {
            handleVolume(event, getCommandArgument(raw, "!volume"));
            return;
        }

        if (raw.equals("!skip")) {
            Guild guild = event.getGuild();
            PlayerManager.getInstance().getGuildMusicManager(guild).scheduler.nextTrack();
            event.getChannel().sendMessage("Skipped the current track.").queue();
            return;
        }

        if (raw.equals("!stop")) {
            Guild guild = event.getGuild();
            var musicManager = PlayerManager.getInstance().getGuildMusicManager(guild);
            musicManager.scheduler.clearQueue();
            musicManager.player.stopTrack();
            guild.getAudioManager().closeAudioConnection();
            event.getChannel().sendMessage("Stopped playback and cleared the queue.").queue();
        }
    }

    private String getCommandArgument(String rawCommand, String commandName) {
        if (rawCommand.length() <= commandName.length()) {
            return "";
        }

        return rawCommand.substring(commandName.length()).trim();
    }

    private void handlePlay(MessageReceivedEvent event, String url) {
        url = normalizePlayUrl(url);

        if (url.isBlank()) {
            event.getChannel().sendMessage("Usage: !play <youtube-url>").queue();
            return;
        }

        if (!looksLikeUrl(url)) {
            event.getChannel().sendMessage("Usage: !play <youtube-url>").queue();
            return;
        }

        Member member = event.getMember();
        if (member == null) {
            event.getChannel().sendMessage("Join a voice channel first.").queue();
            return;
        }

        GuildVoiceState voiceState = member.getVoiceState();
        if (voiceState == null || !voiceState.inAudioChannel()) {
            event.getChannel().sendMessage("Join a voice channel first.").queue();
            return;
        }

        Guild guild = event.getGuild();
        AudioChannel voiceChannel = voiceState.getChannel();
        if (voiceChannel == null) {
            event.getChannel().sendMessage("Join a voice channel first.").queue();
            return;
        }

        var audioManager = guild.getAudioManager();
        preferNorthAmericaRegion(voiceChannel);
        // Avoid reconnect flapping if already connected or already queued to connect.
        if (shouldOpenAudioConnection(audioManager, voiceChannel)) {
            audioManager.openAudioConnection(voiceChannel);
        }
        audioManager.setSendingHandler(PlayerManager.getInstance().getGuildMusicManager(guild).getSendHandler());

        PlayerManager.getInstance().loadAndPlay(guild, event.getChannel(), url);
    }

    private boolean shouldOpenAudioConnection(net.dv8tion.jda.api.managers.AudioManager audioManager, AudioChannel targetChannel) {
        AudioChannel connectedChannel = audioManager.getConnectedChannel();
        if (isSameChannel(connectedChannel, targetChannel)) {
            return false;
        }

        // Ignore repeated open requests while a join attempt is already in-flight.
        ConnectionStatus status = audioManager.getConnectionStatus();
        return switch (status) {
            case CONNECTING_AWAITING_ENDPOINT,
                    CONNECTING_AWAITING_WEBSOCKET_CONNECT,
                    CONNECTING_AWAITING_AUTHENTICATION,
                    CONNECTING_ATTEMPTING_UDP_DISCOVERY,
                    CONNECTING_AWAITING_READY -> false;
            default -> true;
        };
    }

    private boolean isSameChannel(AudioChannel first, AudioChannel second) {
        return first != null && second != null && first.getId().equals(second.getId());
    }

    private void preferNorthAmericaRegion(AudioChannel voiceChannel) {
        Region currentRegion = voiceChannel.getRegion();
        if (isNorthAmericaRegion(currentRegion) || currentRegion == PREFERRED_NA_REGION) {
            return;
        }

        voiceChannel.getManager()
                .setRegion(PREFERRED_NA_REGION)
                .queue(
                        ignored -> { },
                        error -> System.out.println("Could not set voice channel region to US East: " + error.getMessage())
                );
    }

    private boolean isNorthAmericaRegion(Region region) {
        return region == Region.US_EAST
                || region == Region.US_CENTRAL
                || region == Region.US_SOUTH
                || region == Region.US_WEST
                || region == Region.VIP_US_EAST
                || region == Region.VIP_US_CENTRAL
                || region == Region.VIP_US_SOUTH
                || region == Region.VIP_US_WEST;
    }

    private String normalizePlayUrl(String rawUrl) {
        String value = rawUrl.trim();
        while (true) {
            String lower = value.toLowerCase();

            if (lower.startsWith("!play ")) {
                value = value.substring(6).trim();
                continue;
            }

            if (lower.equals("!play")) {
                return "";
            }

            if (lower.startsWith("play ")) {
                value = value.substring(5).trim();
                continue;
            }

            if (value.startsWith("!")) {
                value = value.substring(1).trim();
                continue;
            }

            break;
        }

        return value;
    }

    private boolean looksLikeUrl(String value) {
        String lower = value.toLowerCase();
        return lower.startsWith("http://") || lower.startsWith("https://");
    }

    private void handleSay(MessageReceivedEvent event, String content) {
        Member member = event.getMember();
        if (member == null || !member.hasPermission(Permission.MANAGE_SERVER)) {
            event.getChannel().sendMessage("You need the Manage Server permission to use !say.").queue();
            return;
        }

        if (content.isBlank()) {
            event.getChannel().sendMessage("Usage: !say <message>").queue();
            return;
        }

        event.getChannel().sendMessage(content).queue();
    }

    private void handleVolume(MessageReceivedEvent event, String rawVolume) {
        Guild guild = event.getGuild();

        if (rawVolume.isBlank()) {
            int current = PlayerManager.getInstance().getVolume(guild);
            event.getChannel().sendMessage("Current volume: " + current + "%").queue();
            return;
        }

        Integer parsed = parseInteger(rawVolume);
        if (parsed == null) {
            event.getChannel().sendMessage("Usage: !volume <0-150>").queue();
            return;
        }

        int applied = PlayerManager.getInstance().setVolume(guild, parsed);
        event.getChannel().sendMessage("Volume set to " + applied + "%").queue();
    }

    private Integer parseInteger(String rawValue) {
        try {
            return Integer.parseInt(rawValue.trim());
        } catch (NumberFormatException e) {
            return null;
        }
    }
}
