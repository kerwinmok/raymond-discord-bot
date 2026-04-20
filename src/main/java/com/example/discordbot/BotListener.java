package com.example.discordbot;

import org.jetbrains.annotations.NotNull;

import com.example.discordbot.audio.PlayerManager;

import net.dv8tion.jda.api.Permission;
import net.dv8tion.jda.api.entities.Guild;
import net.dv8tion.jda.api.entities.GuildVoiceState;
import net.dv8tion.jda.api.entities.Member;
import net.dv8tion.jda.api.entities.channel.middleman.AudioChannel;
import net.dv8tion.jda.api.events.message.MessageReceivedEvent;
import net.dv8tion.jda.api.hooks.ListenerAdapter;

public class BotListener extends ListenerAdapter {
    private static final String PREFIX = "!";

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
        AudioChannel connectedChannel = audioManager.getConnectedChannel();

        // Avoid reconnect flapping when the bot is already in the same channel.
        if (connectedChannel == null || !connectedChannel.getId().equals(voiceChannel.getId())) {
            audioManager.openAudioConnection(voiceChannel);
        }
        audioManager.setSendingHandler(PlayerManager.getInstance().getGuildMusicManager(guild).getSendHandler());

        PlayerManager.getInstance().loadAndPlay(guild, event.getChannel(), url);
    }

    private String normalizePlayUrl(String rawUrl) {
        String value = rawUrl.trim();
        if (value.startsWith("!")) {
            value = value.substring(1).trim();
        }
        return value;
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
