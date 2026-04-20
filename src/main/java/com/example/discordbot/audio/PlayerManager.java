package com.example.discordbot.audio;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

import com.sedmelluq.discord.lavaplayer.player.AudioLoadResultHandler;
import com.sedmelluq.discord.lavaplayer.player.AudioPlayerManager;
import com.sedmelluq.discord.lavaplayer.player.DefaultAudioPlayerManager;
import com.sedmelluq.discord.lavaplayer.tools.FriendlyException;
import com.sedmelluq.discord.lavaplayer.track.AudioPlaylist;
import com.sedmelluq.discord.lavaplayer.track.AudioTrack;

import dev.lavalink.youtube.YoutubeAudioSourceManager;
import net.dv8tion.jda.api.entities.Guild;
import net.dv8tion.jda.api.entities.channel.middleman.MessageChannel;

public class PlayerManager {
    private static final String QUEUED_MESSAGE_PREFIX = "Queued: **";
    public static final int MIN_VOLUME = 0;
    public static final int MAX_VOLUME = 150;
    public static final int DEFAULT_VOLUME = 100;

    private static PlayerManager instance;

    private final AudioPlayerManager audioPlayerManager;
    private final Map<Long, GuildMusicManager> musicManagers;

    private PlayerManager() {
        this.audioPlayerManager = new DefaultAudioPlayerManager();
        this.audioPlayerManager.registerSourceManager(new YoutubeAudioSourceManager());
        this.musicManagers = new ConcurrentHashMap<>();
    }

    public static synchronized PlayerManager getInstance() {
        if (instance == null) {
            instance = new PlayerManager();
        }
        return instance;
    }

    public GuildMusicManager getGuildMusicManager(Guild guild) {
        return musicManagers.computeIfAbsent(guild.getIdLong(), id -> {
            GuildMusicManager guildMusicManager = new GuildMusicManager(audioPlayerManager);
            guildMusicManager.player.setVolume(DEFAULT_VOLUME);
            guild.getAudioManager().setSendingHandler(guildMusicManager.getSendHandler());
            return guildMusicManager;
        });
    }

    public int getVolume(Guild guild) {
        GuildMusicManager musicManager = getGuildMusicManager(guild);
        return musicManager.player.getVolume();
    }

    public int setVolume(Guild guild, int requestedVolume) {
        GuildMusicManager musicManager = getGuildMusicManager(guild);
        int clampedVolume = clampVolume(requestedVolume);
        musicManager.player.setVolume(clampedVolume);
        return clampedVolume;
    }

    public void loadAndPlay(Guild guild, MessageChannel channel, String trackUrl) {
        GuildMusicManager musicManager = getGuildMusicManager(guild);

        audioPlayerManager.loadItemOrdered(musicManager, trackUrl, new AudioLoadResultHandler() {
            @Override
            public void trackLoaded(AudioTrack track) {
                musicManager.scheduler.queue(track);
                sendQueuedMessage(channel, track);
            }

            @Override
            public void playlistLoaded(AudioPlaylist playlist) {
                AudioTrack track = resolveTrackFromPlaylist(playlist);

                musicManager.scheduler.queue(track);
                sendQueuedMessage(channel, track);
            }

            @Override
            public void noMatches() {
                channel.sendMessage("Nothing found for that URL.").queue();
            }

            @Override
            public void loadFailed(FriendlyException exception) {
                channel.sendMessage("Could not load track: " + exception.getMessage()).queue();
            }
        });
    }

    private AudioTrack resolveTrackFromPlaylist(AudioPlaylist playlist) {
        AudioTrack selectedTrack = playlist.getSelectedTrack();
        if (selectedTrack != null) {
            return selectedTrack;
        }

        return playlist.getTracks().get(0);
    }

    private void sendQueuedMessage(MessageChannel channel, AudioTrack track) {
        channel.sendMessage(QUEUED_MESSAGE_PREFIX + track.getInfo().title + "**").queue();
    }

    private int clampVolume(int value) {
        if (value < MIN_VOLUME) {
            return MIN_VOLUME;
        }

        if (value > MAX_VOLUME) {
            return MAX_VOLUME;
        }

        return value;
    }
}
