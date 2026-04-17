package com.example.discordbot;

import com.example.discordbot.audio.PlayerManager;
import net.dv8tion.jda.api.Permission;
import net.dv8tion.jda.api.entities.Guild;
import net.dv8tion.jda.api.entities.GuildVoiceState;
import net.dv8tion.jda.api.entities.Member;
import net.dv8tion.jda.api.entities.channel.middleman.AudioChannel;
import net.dv8tion.jda.api.events.message.MessageReceivedEvent;
import net.dv8tion.jda.api.hooks.ListenerAdapter;
import org.jetbrains.annotations.NotNull;

public class BotListener extends ListenerAdapter {
    @Override
    public void onMessageReceived(@NotNull MessageReceivedEvent event) {
        if (event.getAuthor().isBot() || !event.isFromGuild()) {
            return;
        }

        String raw = event.getMessage().getContentRaw().trim();
        if (!raw.startsWith("!")) {
            return;
        }

        if (raw.equals("!play") || raw.startsWith("!play ")) {
            handlePlay(event, raw.length() > 5 ? raw.substring(5).trim() : "");
            return;
        }

        if (raw.equals("!say") || raw.startsWith("!say ")) {
            handleSay(event, raw.length() > 4 ? raw.substring(4).trim() : "");
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

    private void handlePlay(MessageReceivedEvent event, String url) {
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

        guild.getAudioManager().openAudioConnection(voiceChannel);
        guild.getAudioManager().setSendingHandler(PlayerManager.getInstance().getGuildMusicManager(guild).getSendHandler());

        PlayerManager.getInstance().loadAndPlay(guild, event.getChannel(), url);
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
}
