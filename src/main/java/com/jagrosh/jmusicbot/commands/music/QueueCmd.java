/*
 * Copyright 2018 John Grosh <john.a.grosh@gmail.com>.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.jagrosh.jmusicbot.commands.music;

import java.util.List;
import java.util.concurrent.TimeUnit;

import com.jagrosh.jdautilities.command.Command;
import com.jagrosh.jdautilities.command.CommandEvent;
import com.jagrosh.jdautilities.menu.Paginator;
import com.jagrosh.jmusicbot.Bot;
import com.jagrosh.jmusicbot.JMusicBot;
import com.jagrosh.jmusicbot.audio.AudioHandler;
import com.jagrosh.jmusicbot.audio.QueuedTrack;
import com.jagrosh.jmusicbot.commands.MusicCommand;
import com.jagrosh.jmusicbot.settings.RepeatMode;
import com.jagrosh.jmusicbot.settings.Settings;
import com.jagrosh.jmusicbot.utils.FormatUtil;
import com.sedmelluq.discord.lavaplayer.track.AudioTrack;

import net.dv8tion.jda.api.MessageBuilder;
import net.dv8tion.jda.api.Permission;
import net.dv8tion.jda.api.entities.Message;
import net.dv8tion.jda.api.exceptions.PermissionException;

/**
 *
 * @author John Grosh <john.a.grosh@gmail.com>
 */
public class QueueCmd extends MusicCommand 
{
    private final Paginator.Builder builder;
    
    public QueueCmd(Bot bot)
    {
        super(bot);
        this.name = "queue";
        this.help = "shows the current queues";
        this.arguments = "[pagenum]";
        this.aliases = bot.getConfig().getAliases(this.name);
        this.bePlaying = true;
        this.botPermissions = new Permission[]{Permission.MESSAGE_ADD_REACTION,Permission.MESSAGE_EMBED_LINKS};
        builder = new Paginator.Builder()
                .setColumns(1)
                .setFinalAction(m -> {try{m.clearReactions().queue();}catch(PermissionException ignore){}})
                .waitOnSinglePage(false)
                .useNumberedItems(true)
                .showPageNumbers(true)
                .wrapPageEnds(true)
                .setEventWaiter(bot.getWaiter())
                .setTimeout(1, TimeUnit.MINUTES);
    }

    @Override
    public void doCommand(CommandEvent event)
    {
        // TEMPORARY -- can only show one queue right now, because JDA-Utilities paginator has bug preventing multiple paginators
        AudioHandler ah = (AudioHandler)event.getGuild().getAudioManager().getSendingHandler();
        if (ah.playingFromBackgroundQueue() || (ah.getQueue().isEmpty() && !ah.getBackgroundQueue().isEmpty())) {
            showQueue(event, true, true);
        } else {
            showQueue(event, false, true);
            if (!ah.getBackgroundQueue().isEmpty() || ah.getQueue().isEmpty()) {
                showQueue(event, true, false);
            }
        }
    }

    private void showQueue(CommandEvent event, boolean backgroundQueue, boolean paginate) {
        int pagenum = 1;
        try
        {
            pagenum = Integer.parseInt(event.getArgs());
        }
        catch(NumberFormatException ignore){}
        AudioHandler ah = (AudioHandler)event.getGuild().getAudioManager().getSendingHandler();
        List<QueuedTrack> list = (backgroundQueue) ? ah.getBackgroundQueue().getList() : ah.getQueue().getList();
        String queueName = (backgroundQueue) ? "the background queue" : "the queue";
        if(list.isEmpty() && (backgroundQueue == ah.playingFromBackgroundQueue()))
        {
            Message nowp = ah.getNowPlaying(event.getJDA());
            Message nonowp = ah.getNoMusicPlaying(event.getJDA());
            Message built = new MessageBuilder()
                    .setContent(bot.getWarning(event) + " There is no music in "+queueName+"!")
                    .setEmbeds((nowp==null ? nonowp : nowp).getEmbeds().get(0)).build();
            event.reply(built, m -> 
            {
                if(nowp!=null)
                    bot.getNowplayingHandler().setLastNPMessage(m);
            });
            return;
        }
        String[] songs = new String[list.size()];
        long total = 0;
        for(int i=0; i<list.size(); i++)
        {
            total += list.get(i).getTrack().getDuration();
            songs[i] = list.get(i).toString();
        }
        Settings settings = event.getClient().getSettingsFor(event.getGuild());
        long fintotal = total;
        if (paginate) {
            builder
                .setItemsPerPage((ah.getQueue().isEmpty() || ah.getBackgroundQueue().isEmpty()) ? 10 : backgroundQueue ? 2 : 6)
                .setText((i1,i2) -> getQueueTitle(ah, backgroundQueue, bot.getSuccess(event), songs.length, fintotal, settings.getRepeatMode()))
                .setItems(songs)
                .setUsers(event.getAuthor())
                .setColor(event.getSelfMember().getColor())
                ;
            builder.build().paginate(event.getChannel(), pagenum);
        } else {
            event.reply(getQueueTitle(ah, backgroundQueue, bot.getSuccess(event), songs.length, fintotal, settings.getRepeatMode())
                +"\n(cannot show multiple queues yet -- coming soon!)");
        }
    }
    
    private String getQueueTitle(AudioHandler ah, boolean backgroundQueue, String success, int songslength, long total, RepeatMode repeatmode)
    {
        StringBuilder sb = new StringBuilder();
        if(ah.getPlayer().getPlayingTrack()!=null && (backgroundQueue == ah.playingFromBackgroundQueue()))
        {
            sb.append(ah.getStatusEmoji()).append(" **")
                    .append(ah.getPlayer().getPlayingTrack().getInfo().title).append("**\n");
        }
        return FormatUtil.filter(sb.append(success).append(" Current "+(backgroundQueue ? "Background Queue" : "Queue")+" | ").append(songslength)
                .append(" entries | `").append(FormatUtil.formatTime(total)).append("` ")
                .append(repeatmode.getEmoji() != null ? "| "+repeatmode.getEmoji() : "").toString());
    }
}
