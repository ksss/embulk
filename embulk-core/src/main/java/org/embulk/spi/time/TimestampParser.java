package org.embulk.spi.time;

import com.google.common.annotations.VisibleForTesting;
import com.google.common.base.Optional;
import java.text.SimpleDateFormat;  // For default year/month/day if absent
import java.text.ParseException;  // For default year/month/day if absent
import java.util.Calendar;  // For default year/month/day if absent
import java.util.Date;  // For default year/month/day if absent
import java.util.List;
import java.util.Locale;  // For default year/month/day if absent
import java.util.TimeZone;  // For default year/month/day if absent
import org.embulk.config.Config;
import org.embulk.config.ConfigDefault;
import org.embulk.config.ConfigException;  // For default year/month/day if absent
import org.embulk.config.ConfigInject;
import org.joda.time.DateTime;
import org.joda.time.DateTimeZone;
import org.jruby.embed.ScriptingContainer;

import static com.google.common.base.Strings.isNullOrEmpty;

public class TimestampParser
{
    @Deprecated
    public interface ParserTask
            extends org.embulk.config.Task
    {
        @Config("default_timezone")
        @ConfigDefault("\"UTC\"")
        public DateTimeZone getDefaultTimeZone();

        @ConfigInject
        @Deprecated
        public ScriptingContainer getJRuby();
    }

    public interface Task
    {
        @Config("default_timezone")
        @ConfigDefault("\"UTC\"")
        public DateTimeZone getDefaultTimeZone();

        @Config("default_timestamp_format")
        @ConfigDefault("\"%Y-%m-%d %H:%M:%S.%N %z\"")
        public String getDefaultTimestampFormat();

        @Config("default_date")
        @ConfigDefault("\"1970-01-01\"")
        public String getDefaultDate();

        @ConfigInject
        @Deprecated
        public ScriptingContainer getJRuby();
    }

    public interface TimestampColumnOption
    {
        @Config("timezone")
        @ConfigDefault("null")
        public Optional<DateTimeZone> getTimeZone();

        @Config("format")
        @ConfigDefault("null")
        public Optional<String> getFormat();

        @Config("date")
        @ConfigDefault("null")
        public Optional<String> getDate();
    }

    private final DateTimeZone defaultTimeZone;
    private final String format;
    private final RubyTimeParser parser;
    private final Calendar calendar;
    private final List<RubyTimeFormatToken> compiledPattern;

    @Deprecated
    public TimestampParser(String format, ParserTask task)
    {
        this(format, task.getDefaultTimeZone());
        // NOTE: Its deprecation is not actually from ScriptingContainer, though.
        // TODO: Notify users about deprecated calls through the notification reporter.
        if (!deprecationWarned) {
            System.err.println("[WARN] Plugin uses deprecated constructor of org.embulk.spi.time.TimestampParser.");
            System.err.println("[WARN] Report plugins in your config at: https://github.com/embulk/embulk/issues/745");
            // The |deprecationWarned| flag is used only for warning messages.
            // Even in case of race conditions, messages are just duplicated -- should be acceptable.
            deprecationWarned = true;
        }
    }

    @VisibleForTesting
    static TimestampParser createTimestampParserForTesting(Task task)
    {
        return new TimestampParser(task.getDefaultTimestampFormat(), task.getDefaultTimeZone(), task.getDefaultDate());
    }

    public TimestampParser(Task task, TimestampColumnOption columnOption)
    {
        this(
                columnOption.getFormat().or(task.getDefaultTimestampFormat()),
                columnOption.getTimeZone().or(task.getDefaultTimeZone()),
                columnOption.getDate().or(task.getDefaultDate()));
    }

    @Deprecated
    public TimestampParser(ScriptingContainer jruby, String format, DateTimeZone defaultTimeZone)
    {
        this(format, defaultTimeZone);
        // TODO: Notify users about deprecated calls through the notification reporter.
        if (!deprecationWarned) {
            System.err.println("[WARN] Plugin uses deprecated constructor of org.embulk.spi.time.TimestampParser.");
            System.err.println("[WARN] Report plugins in your config at: https://github.com/embulk/embulk/issues/745");
            // The |deprecationWarned| flag is used only for warning messages.
            // Even in case of race conditions, messages are just duplicated -- should be acceptable.
            deprecationWarned = true;
        }
    }

    public TimestampParser(String format, DateTimeZone defaultTimeZone)
    {
        this(format, defaultTimeZone, "1970-01-01");
    }

    @Deprecated
    public TimestampParser(ScriptingContainer jruby, String format, DateTimeZone defaultTimeZone, String defaultDate)
    {
        this(format, defaultTimeZone, defaultDate);
        // TODO: Notify users about deprecated calls through the notification reporter.
        if (!deprecationWarned) {
            System.err.println("[WARN] Plugin uses deprecated constructor of org.embulk.spi.time.TimestampParser.");
            System.err.println("[WARN] Report plugins in your config at: https://github.com/embulk/embulk/issues/745");
            // The |deprecationWarned| flag is used only for warning messages.
            // Even in case of race conditions, messages are just duplicated -- should be acceptable.
            deprecationWarned = true;
        }
    }

    public TimestampParser(final String format, final DateTimeZone defaultTimeZone, final String defaultDate)
    {
        // TODO get default current time from ExecTask.getExecTimestamp
        this.format = format;
        this.parser = new RubyTimeParser();
        this.compiledPattern = this.parser.compilePattern(format);
        this.defaultTimeZone = defaultTimeZone;

        // calculate default date
        SimpleDateFormat df = new SimpleDateFormat("yyyy-MM-dd", Locale.ENGLISH);
        df.setTimeZone(TimeZone.getTimeZone("UTC"));
        Date utc;
        try {
            utc = df.parse(defaultDate);
        }
        catch (ParseException ex) {
            throw new ConfigException("Invalid date format. Expected yyyy-MM-dd: " + defaultDate);
        }
        this.calendar = Calendar.getInstance(TimeZone.getTimeZone("UTC"), Locale.ENGLISH);
        this.calendar.setTime(utc);
    }

    public DateTimeZone getDefaultTimeZone()
    {
        return defaultTimeZone;
    }

    public Timestamp parse(String text) throws TimestampParseException
    {
        if (isNullOrEmpty(text)) {
            throw new TimestampParseException("text is null or empty string.");
        }

        final TimeParseResult parseResult = parser.parse(compiledPattern, text);
        if (parseResult == null) {
            throw new TimestampParseException("Cannot parse '" + text + "' by '" + format + "'");
        }
        return parseResult.toTimestamp(this.calendar.get(Calendar.YEAR),
                                       this.calendar.get(Calendar.MONTH) + 1,
                                       this.calendar.get(Calendar.DAY_OF_MONTH),
                                       this.defaultTimeZone);
    }

    // TODO: Remove this once deprecated constructors are finally removed.
    private static boolean deprecationWarned = false;
}
