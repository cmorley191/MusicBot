package com.jagrosh.jmusicbot.commands.music;

import com.sedmelluq.discord.lavaplayer.track.AudioTrack;

import com.jagrosh.jdautilities.command.Command;
import com.jagrosh.jdautilities.command.CommandEvent;
import com.jagrosh.jmusicbot.Bot;
import com.jagrosh.jmusicbot.audio.AudioHandler;
import com.jagrosh.jmusicbot.commands.MusicCommand;

import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class SeekCmd extends MusicCommand {
  
  private final Bot bot;

  public SeekCmd(Bot bot)
  {
    super(bot);
    this.bot = bot;
    this.name = "seek";
    this.arguments = "(<target time>) | (<direction> <time>)";
    this.help = "starts playing at a different timestamp in the current song";
    this.aliases = new String[0];
    this.beListening = true;
    this.bePlaying = true;
    this.children = new Command[]{new ForwardCmd(bot), new BackwardCmd(bot)};
  }

  private String getUsage(CommandEvent event) {
    StringBuilder builder = new StringBuilder(bot.getWarning(event)+" Seek Commands:\n");
    builder.append("\n`").append(event.getClient().getPrefix()).append(name).append(" <target time>` - jumps the current song to the specified timestamp (e.g. '1:30', '90', '1m30s')");
    builder.append("\n`").append(event.getClient().getPrefix()).append(name).append(" <direction> <time>` - jumps forward or backward in the current song (e.g. 'forward 90s', 'backward 1m30s', 'f 1:30'");
    return builder.toString();
  }

  @Override
  public void doCommand(CommandEvent event) {
    long[] parsedArgs = parseArgsFromFriendlyInputTimestampString(event.getArgs());
    if (parsedArgs == null) {
      event.reply(getUsage(event));
      return;
    }

    doSeek(bot, event, parsedArgs[0], parsedArgs[1]);
  }

  public static String timeMsToFriendlyOutputDurationString(long timeMs) {
    StringBuilder builder = new StringBuilder();
    if (timeMs < 0) builder.append("-");
    timeMs = Math.abs(timeMs);
    if (timeMs == 0) {
      builder.append("0 seconds");
    } else if (timeMs == 1) {
      builder.append("1 millisecond");
    } else if (timeMs == 1000) {
      builder.append("1 second");
    } else if (timeMs == 60 * 1000) {
      builder.append("1 minute");
    } else if (timeMs == 60 * 60 * 1000) {
      builder.append("1 hour");
    } else {
      if (timeMs >= 24 * 60 * 60 * 1000) {
        builder.append(Long.toString(timeMs / (24 * 60 * 60 * 1000))+" day");
        if (timeMs >= 2 * 24 * 60 * 60 * 1000) builder.append("s");
        timeMs %= 24 * 60 * 60 * 1000;
        if (timeMs > 0) builder.append(", ");
      }
      if (timeMs >= 60 * 60 * 1000) {
        builder.append(Long.toString(timeMs / (60 * 60 * 1000))+"h");
        timeMs %= 60 * 60 * 1000;
      }
      if (timeMs >= 60 * 1000) {
        builder.append(Long.toString(timeMs / (60 * 1000))+"m");
        timeMs %= 60 * 1000;
      }
      if (timeMs >= 1000) {
        builder.append(Long.toString(timeMs / 1000)+"s");
        timeMs %= 1000;
      }
      if (timeMs > 0) {
        builder.append(Long.toString(timeMs)+"ms");
      }
    }
    return builder.toString();
  }

  public static String timeMsToFriendlyOutputTimestampString(long timeMs) {
    StringBuilder builder = new StringBuilder();
    boolean firstBlood = true;
    if (timeMs >= 24 * 60 * 60 * 1000) {
      builder.append(Long.toString(timeMs / (24 * 60 * 60 * 1000))+":");
      timeMs %= 24 * 60 * 60 * 1000;
      firstBlood = false;
    }
    if (timeMs >= 60 * 60 * 1000) {
      if (!firstBlood && timeMs < (10 * 60 * 60 * 1000)) builder.append("0");
      builder.append(Long.toString(timeMs / (60 * 60 * 1000))+":");
      timeMs %= 60 * 60 * 1000;
      firstBlood = false;
    } else if (!firstBlood) {
      builder.append("00:");
    }
    if (!firstBlood && timeMs < (10 * 60 * 1000)) builder.append("0");
    if (timeMs >= 60 * 1000) {      
      builder.append(Long.toString(timeMs / (60 * 1000))+":");
      timeMs %= 60 * 1000;
    } else {
      builder.append("0:");
    }
    if (timeMs < 10 * 1000) builder.append("0");
    builder.append(Long.toString(timeMs / 1000));
    timeMs %= 1000;
    if (timeMs > 0) {
      builder.append(".");
      if (timeMs < 100) builder.append("0");
      if (timeMs < 10) builder.append("0");
      builder.append(Long.toString(timeMs));
    }

    return builder.toString();
  }

  private static Long parseLongOrNull(String value) {
    if (value == null) return null;
    if (value.equals("")) return null;
    try {
        return Long.parseLong(value);
    } catch (NumberFormatException e) {
        return null;
    }
  }

  private static final Pattern durationPattern = Pattern.compile("^(([0-9]+)[dD]|)[:,;\\s]*(([0-9]+)[hH]|)[:,;\\s]*(([0-9]+)[mM]|)[:,;\\s]*(([0-9]+)([sS][:,;\\s]*(([0-9]+)[mM][sS]|)|))$");
  private static final Pattern timestampPattern = Pattern.compile("^([0-9]+|)\\s*([:;]|)\\s*([0-9]+|)\\s*([:;]|)\\s*([0-9]+|)\\s*([:;]|)\\s*([0-9]+|)\\s*([.,]|)\\s*([0-9]+|)$");

  public static Long parseTimeMsFromFriendlyInputDurationString(String timeString) {
    timeString = timeString.trim();
    boolean negative = false;
    if (timeString.startsWith("-")) {
      negative = true;
      timeString = timeString.substring(1).trim();
    } else if (timeString.startsWith("+")) {
      timeString = timeString.substring(1).trim();
    }
    if (timeString.isEmpty()) {
      return null;
    }

    Matcher durationMatcher = durationPattern.matcher(timeString);
    if (durationMatcher.find()) {
      long value = 0;
      Long day = parseLongOrNull(durationMatcher.group(2));
      if (day != null) {
        value += day * 24 * 60 * 60 * 1000;
      }
      Long hour = parseLongOrNull(durationMatcher.group(4));
      if (hour != null) {
        value += hour * 60 * 60 * 1000;
      }
      Long minute = parseLongOrNull(durationMatcher.group(6));
      if (minute != null) {
        value += minute * 60 * 1000;
      }
      Long second = parseLongOrNull(durationMatcher.group(8));
      if (second != null) {
        value += second * 1000;
      }
      Long milli = parseLongOrNull(durationMatcher.group(11));
      if (milli != null) {
        value += milli;
      }

      if (day == null && hour == null && minute == null && second == null && milli == null) return null;

      return value * (negative ? -1 : 1);
    }

    Matcher timestampMatcher = timestampPattern.matcher(timeString);
    if (timestampMatcher.find()) {
      Long first = parseLongOrNull(timestampMatcher.group(1));
      Long second = parseLongOrNull(timestampMatcher.group(3));
      Long third = parseLongOrNull(timestampMatcher.group(5));
      Long fourth = parseLongOrNull(timestampMatcher.group(7));
      String firstSep = timestampMatcher.group(2);
      String secondSep = timestampMatcher.group(4);
      String thirdSep = timestampMatcher.group(6);
      String millisString = timestampMatcher.group(9);
      Long millis = parseLongOrNull(millisString);
      if (millis != null) {
        if (millisString.length() == 1) millis *= 100;
        else if (millisString.length() == 2) millis *= 10;
        else if (millisString.length() > 3) millis /= (long)Math.pow(10, millisString.length() - 3);
      }

      if (first == null && second == null && third == null && fourth == null && millis == null) return null;

      if (first == null) first = 0L;
      if (second == null) second = 0L;
      if (third == null) third = 0L;
      if (fourth == null) fourth = 0L;

      int sepCount = 0;
      if (!firstSep.equals("")) sepCount++;
      if (!secondSep.equals("")) sepCount++;
      if (!thirdSep.equals("")) sepCount++;

      long days = 0, hours = 0, minutes = 0, seconds = 0;
      if (sepCount == 0) { 
        seconds = first;
      } else if (sepCount == 1) {
        minutes = first;
        seconds = second;
      } else if (sepCount == 2) {
        hours = first;
        minutes = second;
        seconds = third;
      } else if (sepCount == 3) {
        days = first;
        hours = second;
        minutes = third;
        seconds = fourth;
      }

      long value = (millis == null) ? 0L : millis;
      value += days * 24 * 60 * 60 * 1000;
      value += hours * 60 * 60 * 1000;
      value += minutes * 60 * 1000;
      value += seconds * 1000;
      if (negative) value *= -1;
      return value;
    }

    return null;
  }

  /**
   * @return array {direction(-1|0|1), time}
   */
  public static long[] parseArgsFromFriendlyInputTimestampString(String timeString) {
    timeString = timeString.trim();
    if (timeString.isEmpty()) {
      return null;
    }
    Long time = parseTimeMsFromFriendlyInputDurationString(timeString);
    if (time == null) return null;
    if (timeString.startsWith("+")) {
      return new long[] { 1, time };
    } else if (timeString.startsWith("-")) {
      return new long[] { 1, -time };
    } else {
      return new long[] { 0, time };
    }
  }

  public static void doSeek(Bot bot, CommandEvent event, long direction, long timeMs) {
    AudioHandler handler = (AudioHandler)event.getGuild().getAudioManager().getSendingHandler();
    if (handler == null) {
      event.reply(bot.getError(event)+" There must be music playing to use that");
      return;
    }

    AudioTrack track = handler.getPlayer().getPlayingTrack();
    if (track == null) {
      event.reply(bot.getError(event)+" There must be music playing to use that.");
      return;
    }

    if (!track.isSeekable()) {
      event.reply(bot.getError(event)+" Cannot seek due to the track's file format.");
      return;
    }

    long originalPosition = track.getPosition();
    long targetTimeMs = originalPosition;
    if (direction == 0) {
      targetTimeMs = timeMs;
    } else if (direction > 0) {
      targetTimeMs += timeMs;
    } else {
      targetTimeMs -= timeMs;
    }


    if (targetTimeMs < 0) {
      if (direction == 0) {
        event.reply(bot.getError(event)+" Cannot seek to a negative timestamp: `"+timeMsToFriendlyOutputTimestampString(targetTimeMs)+"`.");
      } else {
        event.reply(bot.getError(event)+" Cannot seek backward "+timeMsToFriendlyOutputDurationString(Math.abs(timeMs))+" to a negative timestamp: `"+timeMsToFriendlyOutputTimestampString(targetTimeMs)+"`.");
      }
      return;
    }
    if (targetTimeMs >= track.getDuration()) {
      if (direction == 0) {
        event.reply(bot.getError(event)+" Requested seek timestamp is past the end of the song: `"+timeMsToFriendlyOutputTimestampString(targetTimeMs)+"`.");
      } else {
        event.reply(bot.getError(event)+" Cannot seek forward "+timeMsToFriendlyOutputDurationString(Math.abs(timeMs))+" to a timestamp past the end of the song: `"+timeMsToFriendlyOutputTimestampString(targetTimeMs)+"`.");
      }
      return;
    }

    track.setPosition(targetTimeMs);
    if (direction == 0) {
      event.reply(bot.getSuccess(event)+" "+(targetTimeMs > originalPosition ? "Fast forwarded" : "Rewound")+" to `"+timeMsToFriendlyOutputTimestampString(targetTimeMs)+"`.");
    } else {
      event.reply(bot.getSuccess(event)+" "+(direction > 0 ? "Fast forwarded" : "Rewound")+" "+timeMsToFriendlyOutputDurationString(timeMs)+" to `"+timeMsToFriendlyOutputTimestampString(targetTimeMs)+"`.");
    }
  }

  public class ForwardCmd extends MusicCommand {
  
    public ForwardCmd(Bot bot)
    {
      super(bot);
      this.name = "forward";
      this.aliases = new String[]{"f", "fo", "fw", "foreward", "fastforward", "fastforeward", "ff", "ahead", "+", "right", "ri"};
      this.arguments = "<relative_time>";
      this.help = "jumps ahead in the current song";
      this.beListening = true;
      this.bePlaying = true;
    }

    @Override
    public void doCommand(CommandEvent event) {
      Long parsedTimeMs = parseTimeMsFromFriendlyInputDurationString(event.getArgs());
      if (parsedTimeMs == null) {
        event.reply(getUsage(event));
        return;
      }
  
      doSeek(bot, event, 1, parsedTimeMs);
    }
  }

  public class BackwardCmd extends MusicCommand {
  
    public BackwardCmd(Bot bot)
    {
      super(bot);
      this.name = "backward";
      this.aliases = new String[]{"b", "ba", "bac", "back", "bw", "rewind", "r", "re", "rw", "reverse", "rv", "behind", "-", "left", "l", "le"};
      this.arguments = "<relative_time>";
      this.help = "jumps back in the current song";
      this.beListening = true;
      this.bePlaying = true;
    }

    @Override
    public void doCommand(CommandEvent event) {
      Long parsedTimeMs = parseTimeMsFromFriendlyInputDurationString(event.getArgs());
      if (parsedTimeMs == null) {
        event.reply(getUsage(event));
        return;
      }
  
      doSeek(bot, event, -1, parsedTimeMs);
    }
  }
}
