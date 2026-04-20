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
            guild.getAudioManager().setSendingHandler(guildMusicManager.getSendHandler());
            return guildMusicManager;
        });
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
}
