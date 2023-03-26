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

import com.jagrosh.jdautilities.command.Command;
import com.jagrosh.jdautilities.command.CommandEvent;
import com.jagrosh.jmusicbot.Bot;
import com.jagrosh.jmusicbot.audio.AudioHandler;
import com.jagrosh.jmusicbot.audio.QueuedTrack;
import com.jagrosh.jmusicbot.commands.MusicCommand;
import com.jagrosh.jmusicbot.queue.FairQueue;

/**
 *
 * @author John Grosh <john.a.grosh@gmail.com>
 */
public class ShuffleCmd extends MusicCommand 
{
    public ShuffleCmd(Bot bot)
    {
        super(bot);
        this.name = "shuffle";
        this.arguments = "[background]";
        this.help = "shuffles songs you have added";
        this.aliases = bot.getConfig().getAliases(this.name);
        this.beListening = true;
        this.bePlaying = true;
        this.children = new Command[]{new BackgroundCmd(bot)};
    }

    @Override
    public void doCommand(CommandEvent event) 
    {
        doShuffle(bot, event, false);
    }

    private static void doShuffle(Bot bot, CommandEvent event, boolean background) {
        AudioHandler handler = (AudioHandler)event.getGuild().getAudioManager().getSendingHandler();
        FairQueue<QueuedTrack> queue = (background) ? handler.getBackgroundQueue() : handler.getQueue();
        String queueName = (background) ? "the background queue" : "the queue";
        int s = queue.shuffle(event.getAuthor().getIdLong());
        switch (s) 
        {
            case 0:
                event.reply(bot.getError(event)+"You don't have any music in "+queueName+" to shuffle!");
                break;
            case 1:
                event.reply(bot.getWarning(event)+"You only have one song in "+queueName+"!");
                break;
            default:
                event.reply(bot.getSuccess(event)+"Shuffled the "+s+" entries of "+queueName+".");
                break;
        }
    }

    public class BackgroundCmd extends MusicCommand
    {
        private static final boolean background = true;

        public BackgroundCmd(Bot bot)
        {
            super(bot);
            this.name = "background";
            this.aliases = new String[]{"bg", "b", "back"};
            this.help = "shuffles songs you have added to the background queue";
            this.beListening = true;
            this.bePlaying = true;
        }

        @Override
        public void doCommand(CommandEvent event) 
        {
            doShuffle(bot, event, background);
        }
    }
    
}
