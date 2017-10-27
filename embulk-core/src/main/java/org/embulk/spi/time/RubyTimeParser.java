/*
 * The following license block applies to parts of this source code as described in the Javadoc.
 */
/***** BEGIN LICENSE BLOCK *****
 * Version: EPL 1.0/GPL 2.0/LGPL 2.1
 *
 * The contents of this file are subject to the Eclipse Public
 * License Version 1.0 (the "License"); you may not use this file
 * except in compliance with the License. You may obtain a copy of
 * the License at http://www.eclipse.org/legal/epl-v10.html
 *
 * Software distributed under the License is distributed on an "AS
 * IS" basis, WITHOUT WARRANTY OF ANY KIND, either express or
 * implied. See the License for the specific language governing
 * rights and limitations under the License.
 *
 * Copyright (C) 2002, 2009 Jan Arne Petersen <jpetersen@uni-bonn.de>
 * Copyright (C) 2004 Charles O Nutter <headius@headius.com>
 * Copyright (C) 2004 Anders Bengtsson <ndrsbngtssn@yahoo.se>
 * Copyright (C) 2004 Stefan Matthias Aust <sma@3plus4.de>
 *
 * Alternatively, the contents of this file may be used under the terms of
 * either of the GNU General Public License Version 2 or later (the "GPL"),
 * or the GNU Lesser General Public License Version 2.1 or later (the "LGPL"),
 * in which case the provisions of the GPL or the LGPL are applicable instead
 * of those above. If you wish to allow use of your version of this file only
 * under the terms of either the GPL or the LGPL, and not to allow others to
 * use your version of this file under the terms of the EPL, indicate your
 * decision by deleting the provisions above and replace them with the notice
 * and other provisions required by the GPL or the LGPL. If you do not delete
 * the provisions above, a recipient may use your version of this file under
 * the terms of any one of the EPL, the GPL or the LGPL.
 ***** END LICENSE BLOCK *****/
package org.embulk.spi.time;

import java.io.IOException;
import java.io.Reader;
import java.io.StringReader;
import java.util.EnumSet;
import java.util.LinkedList;
import java.util.List;
import java.util.Arrays;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * RubyTimeParser is a Ruby-compatible time parser.
 *
 * Embulk's timestamp formats are based on Ruby's formats for historical reasons, and kept for compatibility.
 * Embulk maintains its own implementation of Ruby-compatible time parser to be independent from JRuby.
 *
 * This class is intentionally package-private so that plugins do not directly depend.
 *
 * This class is almost reimplementation of Ruby v2.3.1's ext/date/date_strptime.c. See its COPYING for license.
 *
 * @see <a href="https://svn.ruby-lang.org/cgi-bin/viewvc.cgi/tags/v2_3_1/ext/date/date_strptime.c?view=markup">ext/date/date_strptime.c</a>
 * @see <a href="https://svn.ruby-lang.org/cgi-bin/viewvc.cgi/tags/v2_3_1/COPYING?view=markup">COPYING</a>
 *
 * This class is contributed to the JRuby project before it is refactored on the Embulk side.
 *
 * @see <a href="https://github.com/jruby/jruby/pull/4635">Implement RubyDateParser in Java by muga - Pull Request #4635 - jruby/jruby</a>
 *
 * Some components are imported from JRuby 9.1.5.0's core/src/main/java/org/jruby/util/RubyDateFormatter.java and
 * lib/ruby/stdlib/date/format.rb with modification. Eclipse Public License version 1.0 is applied for the import.
 * See its COPYING for license.
 *
 * @see <a href="https://github.com/jruby/jruby/blob/9.1.5.0/core/src/main/java/org/jruby/util/RubyDateFormatter.java">core/src/main/java/org/jruby/util/RubyDateFormatter.java</a>
 * @see <a href="https://github.com/jruby/jruby/blob/9.1.5.0/COPYING">COPYING</a>
 */
class RubyTimeParser
{
    // day_names
    private static final String[] DAY_NAMES = new String[] {
            "Sunday", "Monday", "Tuesday", "Wednesday", "Thursday", "Friday", "Saturday",
            "Sun", "Mon", "Tue", "Wed", "Thu", "Fri", "Sat"
    };

    // month_names
    private static final String[] MONTH_NAMES = new String[] {
            "January", "February", "March", "April", "May", "June", "July", "August", "September",
            "October", "November", "December", "Jan", "Feb", "Mar", "Apr", "May", "Jun",
            "Jul", "Aug", "Sep", "Oct", "Nov", "Dec"
    };

    // merid_names
    private static final String[] MERID_NAMES = new String[] {
            "am", "pm", "a.m.", "p.m."
    };

    private final RubyTimeFormatLexer lexer;

    public RubyTimeParser()
    {
        this.lexer = new RubyTimeFormatLexer((Reader) null);
    }

    /**
     * This method is imported from JRuby 9.1.5.0's core/src/main/java/org/jruby/util/RubyDateFormatter.java.
     * Eclipse Public License version 1.0 is applied for the import. See its COPYING for license.
     *
     * @see <a href="https://github.com/jruby/jruby/blob/9.1.5.0/core/src/main/java/org/jruby/util/RubyDateFormatter.java">core/src/main/java/org/jruby/util/RubyDateFormatter.java</a>
     * @see <a href="https://github.com/jruby/jruby/blob/9.1.5.0/COPYING">COPYING</a>
     */
    private void addToPattern(final List<RubyTimeFormatToken> compiledPattern, final String str)
    {
        for (int i = 0; i < str.length(); i++) {
            final char c = str.charAt(i);
            if (('A' <= c && c <= 'Z') || ('a' <= c && c <= 'z')) {
                compiledPattern.add(RubyTimeFormatToken.getFormatDirectiveToken(c));
            }
            else {
                compiledPattern.add(new RubyTimeFormatStringToken(Character.toString(c)));
            }
        }
    }

    /**
     * This method is imported from JRuby 9.1.5.0's core/src/main/java/org/jruby/util/RubyDateFormatter.java.
     * Eclipse Public License version 1.0 is applied for the import. See its COPYING for license.
     *
     * @see <a href="https://github.com/jruby/jruby/blob/9.1.5.0/core/src/main/java/org/jruby/util/RubyDateFormatter.java">core/src/main/java/org/jruby/util/RubyDateFormatter.java</a>
     * @see <a href="https://github.com/jruby/jruby/blob/9.1.5.0/COPYING">COPYING</a>
     */
    public List<RubyTimeFormatToken> compilePattern(final String pattern)
    {
        final List<RubyTimeFormatToken> compiledPattern = new LinkedList<>();
        final Reader reader = new StringReader(pattern); // TODO Use try-with-resource statement
        lexer.yyreset(reader);

        RubyTimeFormatToken token;
        try {
            while ((token = lexer.yylex()) != null) {
                if (token.getFormatDirective() != RubyTimeFormatDirective.SPECIAL) {
                    compiledPattern.add(token);
                }
                else {
                    char c = token.getSpecifier();
                    switch (c) {
                        case 'c':
                            addToPattern(compiledPattern, "a b e H:M:S Y");
                            break;
                        case 'D':
                        case 'x':
                            addToPattern(compiledPattern, "m/d/y");
                            break;
                        case 'F':
                            addToPattern(compiledPattern, "Y-m-d");
                            break;
                        case 'n':
                            compiledPattern.add(new RubyTimeFormatStringToken("\n"));
                            break;
                        case 'R':
                            addToPattern(compiledPattern, "H:M");
                            break;
                        case 'r':
                            addToPattern(compiledPattern, "I:M:S p");
                            break;
                        case 'T':
                        case 'X':
                            addToPattern(compiledPattern, "H:M:S");
                            break;
                        case 't':
                            compiledPattern.add(new RubyTimeFormatStringToken("\t"));
                            break;
                        case 'v':
                            addToPattern(compiledPattern, "e-b-Y");
                            break;
                        case 'Z':
                            // +HH:MM in 'date', never zone name
                            compiledPattern.add(new RubyTimeFormatTimeZoneOffsetToken(1));
                            break;
                        case '+':
                            addToPattern(compiledPattern, "a b e H:M:S ");
                            // %Z: +HH:MM in 'date', never zone name
                            compiledPattern.add(new RubyTimeFormatTimeZoneOffsetToken(1));
                            addToPattern(compiledPattern, " Y");
                            break;
                        default:
                            throw new Error("Unknown special char: " + c);
                    }
                }
            }
        }
        catch (IOException e) {
            e.printStackTrace();
        }

        return compiledPattern;
    }

    public TimeParseResult parse(final List<RubyTimeFormatToken> compiledPattern, final String text)
    {
        return new StringParser(text).parse(compiledPattern);
    }

    private static class StringParser
    {
        private static final Pattern ZONE_PARSE_REGEX = Pattern.compile("\\A(" +
                        "(?:gmt|utc?)?[-+]\\d+(?:[,.:]\\d+(?::\\d+)?)?" +
                        "|(?-i:[[\\p{Alpha}].\\s]+)(?:standard|daylight)\\s+time\\b" +
                        "|(?-i:[[\\p{Alpha}]]+)(?:\\s+dst)?\\b" +
                        ")", Pattern.CASE_INSENSITIVE);

        private final String text;

        private int pos;
        private boolean fail;

        private StringParser(String text)
        {
            this.text = text;

            this.pos = 0;
            this.fail = false;
        }

        private TimeParseResult parse(final List<RubyTimeFormatToken> compiledPattern)
        {
            final TimeParseResult.RubyStyleBuilder builder = TimeParseResult.rubyStyleBuilder(this.text);

            for (int tokenIndex = 0; tokenIndex < compiledPattern.size(); tokenIndex++) {
                final RubyTimeFormatToken token = compiledPattern.get(tokenIndex);

                switch (token.getFormatDirective()) {
                    case STRING: {
                        final RubyTimeFormatStringToken stringToken = (RubyTimeFormatStringToken) token;
                        final String str = stringToken.getStringContent();
                        for (int i = 0; i < str.length(); i++) {
                            final char c = str.charAt(i);
                            if (isSpace(c)) {
                                while (!isEndOfText(text, pos) && isSpace(text.charAt(pos))) {
                                    pos++;
                                }
                            }
                            else {
                                if (isEndOfText(text, pos) || c != text.charAt(pos)) {
                                    fail = true;
                                }
                                pos++;
                            }
                        }
                        break;
                    }
                    case DAY_OF_WEEK_FULL_NAME: // %A - The full weekday name (``Sunday'')
                    case DAY_OF_WEEK_ABBREVIATED_NAME: { // %a - The abbreviated name (``Sun'')
                        final int dayIndex = findIndexInPatterns(DAY_NAMES);
                        if (dayIndex >= 0) {
                            builder.setDayOfWeekStartingWithSunday0(dayIndex % 7);
                            pos += DAY_NAMES[dayIndex].length();
                        }
                        else {
                            fail = true;
                        }
                        break;
                    }
                    case MONTH_OF_YEAR_FULL_NAME: // %B - The full month name (``January'')
                    case MONTH_OF_YEAR_ABBREVIATED_NAME:  // %b, %h - The abbreviated month name (``Jan'')
                    case MONTH_OF_YEAR_ABBREVIATED_NAME_ALIAS_SMALL_H: {
                        final int monIndex = findIndexInPatterns(MONTH_NAMES);
                        if (monIndex >= 0) {
                            builder.setMonthOfYear(monIndex % 12 + 1);
                            pos += MONTH_NAMES[monIndex].length();
                        }
                        else {
                            fail = true;
                        }
                        break;
                    }
                    case CENTURY: { // %C - year / 100 (round down.  20 in 2009)
                        final long cent;
                        if (isNumberPattern(compiledPattern, tokenIndex)) {
                            cent = readDigits(2);
                        }
                        else {
                            cent = readDigitsMax();
                        }
                        builder.setCentury((int)cent);
                        break;
                    }
                    case DAY_OF_MONTH_ZERO_PADDED: // %d, %Od - Day of the month, zero-padded (01..31)
                    case DAY_OF_MONTH_BLANK_PADDED: { // %e, %Oe - Day of the month, blank-padded ( 1..31)
                        final long day;
                        if (isBlank(text, pos)) {
                            pos += 1; // blank
                            day = readDigits(1);
                        }
                        else {
                            day = readDigits(2);
                        }

                        if (!validRange(day, 1, 31)) {
                            fail = true;
                        }
                        builder.setDayOfMonth((int)day);
                        break;
                    }
                    case WEEK_BASED_YEAR_WITH_CENTURY: { // %G - The week-based year
                        final long year;
                        if (isNumberPattern(compiledPattern, tokenIndex)) {
                            year = readDigits(4);
                        }
                        else {
                            year = readDigitsMax();
                        }
                        builder.setWeekBasedYear((int)year);
                        break;
                    }
                    case WEEK_BASED_YEAR_WITHOUT_CENTURY: { // %g - The last 2 digits of the week-based year (00..99)
                        final long v = readDigits(2);
                        if (!validRange(v, 0, 99)) {
                            fail = true;
                        }
                        builder.setWeekBasedYearWithoutCentury((int)v);
                        break;
                    }
                    case HOUR_OF_DAY_ZERO_PADDED: // %H, %OH - Hour of the day, 24-hour clock, zero-padded (00..23)
                    case HOUR_OF_DAY_BLANK_PADDED: { // %k - Hour of the day, 24-hour clock, blank-padded ( 0..23)
                        final long hour;
                        if (isBlank(text, pos)) {
                            pos += 1; // blank
                            hour = readDigits(1);
                        }
                        else {
                            hour = readDigits(2);
                        }

                        if (!validRange(hour, 0, 24)) {
                            fail = true;
                        }
                        builder.setHour((int)hour);
                        break;
                    }
                    case HOUR_OF_AMPM_ZERO_PADDED: // %I, %OI - Hour of the day, 12-hour clock, zero-padded (01..12)
                    case HOUR_OF_AMPM_BLANK_PADDED: { // %l - Hour of the day, 12-hour clock, blank-padded ( 1..12)
                        final long hour;
                        if (isBlank(text, pos)) {
                            pos += 1; // blank
                            hour = readDigits(1);
                        }
                        else {
                            hour = readDigits(2);
                        }

                        if (!validRange(hour, 1, 12)) {
                            fail = true;
                        }
                        builder.setHour((int)hour);
                        break;
                    }
                    case DAY_OF_YEAR: { // %j - Day of the year (001..366)
                        final long day = readDigits(3);
                        if (!validRange(day, 1, 365)) {
                            fail = true;
                        }
                        builder.setDayOfYear((int)day);
                        break;
                    }
                    case MILLI_OF_SECOND: // %L - Millisecond of the second (000..999)
                    case NANO_OF_SECOND: { // %N - Fractional seconds digits, default is 9 digits (nanosecond)
                        boolean negative = false;
                        if (isSign(text, pos)) {
                            negative = text.charAt(pos) == '-';
                            pos++;
                        }

                        final long v;
                        final int initPos = pos;
                        if (isNumberPattern(compiledPattern, tokenIndex)) {
                            if (token.getFormatDirective() == RubyTimeFormatDirective.MILLI_OF_SECOND) {
                                v = readDigits(3);
                            }
                            else {
                                v = readDigits(9);
                            }
                        }
                        else {
                            v = readDigitsMax();
                        }

                        builder.setNanoOfSecond((!negative ? v : -v) * (int) Math.pow(10, 9 - (pos - initPos)));
                        break;
                    }
                    case MINUTE_OF_HOUR: { // %M, %OM - Minute of the hour (00..59)
                        final long min = readDigits(2);
                        if (!validRange(min, 0, 59)) {
                            fail = true;
                        }
                        builder.setMinuteOfHour((int)min);
                        break;
                    }
                    case MONTH_OF_YEAR: { // %m, %Om - Month of the year, zero-padded (01..12)
                        final long mon = readDigits(2);
                        if (!validRange(mon, 1, 12)) {
                            fail = true;
                        }
                        builder.setMonthOfYear((int)mon);
                        break;
                    }
                    case AMPM_OF_DAY_UPPER_CASE: // %P - Meridian indicator, lowercase (``am'' or ``pm'')
                    case AMPM_OF_DAY_LOWER_CASE: { // %p - Meridian indicator, uppercase (``AM'' or ``PM'')
                        final int meridIndex = findIndexInPatterns(MERID_NAMES);
                        if (meridIndex >= 0) {
                            builder.setAmPmOfDay(meridIndex % 2 == 0 ? 0 : 12);
                            pos += MERID_NAMES[meridIndex].length();
                        }
                        else {
                            fail = true;
                        }
                        break;
                    }
                    case MICROSECOND_SINCE_EPOCH: { // %Q - Number of microseconds since 1970-01-01 00:00:00 UTC.
                        boolean negative = false;
                        if (isMinus(text, pos)) {
                            negative = true;
                            pos++;
                        }

                        final long sec = (negative ? -readDigitsMax() : readDigitsMax());

                        builder.setSecondSinceEpoch(sec / 1000L, sec % 1000L * (long) Math.pow(10, 6));
                        break;
                    }
                    case SECOND_OF_MINUTE: { // %S - Second of the minute (00..59)
                        final long sec = readDigits(2);
                        if (!validRange(sec, 0, 60)) {
                            fail = true;
                        }
                        builder.setSecondOfMinute((int)sec);
                        break;
                    }
                    case SECOND_SINCE_EPOCH: { // %s - Number of seconds since 1970-01-01 00:00:00 UTC.
                        boolean negative = false;
                        if (isMinus(text, pos)) {
                            negative = true;
                            pos++;
                        }

                        final long sec = readDigitsMax();
                        builder.setSecondSinceEpoch(!negative ? sec : -sec, 0);
                        break;
                    }
                    case WEEK_OF_YEAR_STARTING_WITH_SUNDAY: // %U, %OU - Week number of the year.  The week starts with Sunday.  (00..53)
                    case WEEK_OF_YEAR_STARTING_WITH_MONDAY: { // %W, %OW - Week number of the year.  The week starts with Monday.  (00..53)
                        final long week = readDigits(2);
                        if (!validRange(week, 0, 53)) {
                            fail = true;
                        }

                        if (token.getFormatDirective() == RubyTimeFormatDirective.WEEK_OF_YEAR_STARTING_WITH_SUNDAY) {
                            builder.setWeekOfYearStartingWithSunday((int)week);
                        } else {
                            builder.setWeekOfYearStartingWithMonday((int)week);
                        }
                        break;
                    }
                    case DAY_OF_WEEK_STARTING_WITH_MONDAY_1: { // %u, %Ou - Day of the week (Monday is 1, 1..7)
                        final long day = readDigits(1);
                        if (!validRange(day, 1, 7)) {
                            fail = true;
                        }
                        builder.setDayOfWeekStartingWithMonday1((int)day);
                        break;
                    }
                    case WEEK_OF_WEEK_BASED_YEAR: { // %V, %OV - Week number of the week-based year (01..53)
                        final long week = readDigits(2);
                        if (!validRange(week, 1, 53)) {
                            fail = true;
                        }
                        builder.setWeekOfWeekBasedYear((int)week);
                        break;
                    }
                    case DAY_OF_WEEK_STARTING_WITH_SUNDAY_0: { // %w - Day of the week (Sunday is 0, 0..6)
                        final long day = readDigits(1);
                        if (!validRange(day, 0, 6)) {
                            fail = true;
                        }
                        builder.setDayOfWeekStartingWithSunday0((int)day);
                        break;
                    }
                    case YEAR_WITH_CENTURY: {
                        // %Y, %EY - Year with century (can be negative, 4 digits at least)
                        //           -0001, 0000, 1995, 2009, 14292, etc.
                        boolean negative = false;
                        if (isSign(text, pos)) {
                            negative = text.charAt(pos) == '-';
                            pos++;
                        }

                        final long year;
                        if (isNumberPattern(compiledPattern, tokenIndex)) {
                            year = readDigits(4);
                        } else {
                            year = readDigitsMax();
                        }

                        builder.setYear((int)(!negative ? year : -year));
                        break;
                    }
                    case YEAR_WITHOUT_CENTURY: { // %y, %Ey, %Oy - year % 100 (00..99)
                        final long y = readDigits(2);
                        if (!validRange(y, 0, 99)) {
                            fail = true;
                        }
                        builder.setYearWithoutCentury((int)y);
                        break;
                    }
                    case TIME_ZONE_NAME: // %Z - Time zone abbreviation name
                    case TIME_OFFSET: {
                        // %z - Time zone as hour and minute offset from UTC (e.g. +0900)
                        //      %:z - hour and minute offset from UTC with a colon (e.g. +09:00)
                        //      %::z - hour, minute and second offset from UTC (e.g. +09:00:00)
                        //      %:::z - hour, minute and second offset from UTC
                        //          (e.g. +09, +09:30, +09:30:30)
                        if (isEndOfText(text, pos)) {
                            fail = true;
                            break;
                        }

                        final Matcher m = ZONE_PARSE_REGEX.matcher(text.substring(pos));
                        if (m.find()) {
                            // zone
                            String zone = text.substring(pos, pos + m.end());
                            builder.setTimeOffset(zone);
                            pos += zone.length();
                        } else {
                            fail = true;
                        }
                        break;
                    }
                    case SPECIAL:
                    {
                        throw new Error("SPECIAL is a special token only for the lexer.");
                    }
                }
            }

            if (fail) {
                return null;
            }

            if (text.length() > pos) {
                builder.setLeftover(text.substring(pos, text.length()));
            }

            return builder.build();
        }

        /**
         * Ported read_digits in MRI 2.3.1's ext/date/date_strptime.c
         * @see <a href="https://github.com/ruby/ruby/blob/394fa89c67722d35bdda89f10c7de5c304a5efb1/ext/date/date_strftime.c">date_strftime.c</a>
         */
        private long readDigits(final int len)
        {
            char c;
            long v = 0;
            final int initPos = pos;

            for (int i = 0; i < len; i++) {
                if (isEndOfText(text, pos)) {
                    break;
                }

                c = text.charAt(pos);
                if (!isDigit(c)) {
                    break;
                }
                else {
                    v = v * 10 + toInt(c);
                }
                pos += 1;
            }

            if (pos == initPos) {
                fail = true;
            }

            return v;
        }

        /**
         * Ported from READ_DIGITS_MAX in MRI 2.3.1's ext/date/date_strptime.c under BSDL.
         * @see <a href="https://github.com/ruby/ruby/blob/394fa89c67722d35bdda89f10c7de5c304a5efb1/ext/date/date_strftime.c">date_strftime.c</a>
         */
        private long readDigitsMax()
        {
            return readDigits(Integer.MAX_VALUE);
        }

        /**
         * Returns -1 if text doesn't match with patterns.
         */
        private int findIndexInPatterns(final String[] patterns)
        {
            if (isEndOfText(text, pos)) {
                return -1;
            }

            for (int i = 0; i < patterns.length; i++) {
                final String pattern = patterns[i];
                final int len = pattern.length();
                if (!isEndOfText(text, pos + len - 1)
                        && pattern.equalsIgnoreCase(text.substring(pos, pos + len))) { // strncasecmp
                    return i;
                }
            }

            return -1; // text doesn't match at any patterns.
        }

        /**
         * Ported from num_pattern_p in MRI 2.3.1's ext/date/date_strptime.c under BSDL.
         * @see <a href="https://github.com/ruby/ruby/blob/394fa89c67722d35bdda89f10c7de5c304a5efb1/ext/date/date_strftime.c">date_strftime.c</a>
         */
        private static boolean isNumberPattern(final List<RubyTimeFormatToken> compiledPattern, final int i)
        {
            if (compiledPattern.size() <= i + 1) {
                return false;
            }
            else {
                final RubyTimeFormatToken nextToken = compiledPattern.get(i + 1);
                final RubyTimeFormatDirective f = nextToken.getFormatDirective();
                if (f == RubyTimeFormatDirective.STRING &&
                    isDigit(((RubyTimeFormatStringToken) nextToken).getStringContent().charAt(0))) {
                    return true;
                }
                else if (RubyTimeFormatDirective.NUMERIC_DIRECTIVES.contains(f)) {
                    return true;
                }
                else {
                    return false;
                }
            }
        }

        /**
         * Ported from valid_pattern_p in MRI 2.3.1's ext/date/date_strptime.c under BSDL.
         * @see <a href="https://github.com/ruby/ruby/blob/394fa89c67722d35bdda89f10c7de5c304a5efb1/ext/date/date_strftime.c">date_strftime.c</a>
         */
        private static boolean validRange(long v, int lower, int upper)
        {
            return lower <= v && v <= upper;
        }

        private static boolean isSpace(char c)
        {
            return c == ' ' || c == '\t' || c == '\n' ||
                    c == '\u000b' || c == '\f' || c == '\r';
        }

        private static boolean isDigit(char c)
        {
            return '0' <= c && c <= '9';
        }

        private static boolean isEndOfText(String text, int pos)
        {
            return pos >= text.length();
        }

        private static boolean isSign(String text, int pos)
        {
            return !isEndOfText(text, pos) && (text.charAt(pos) == '+' || text.charAt(pos) == '-');
        }

        private static boolean isMinus(String text, int pos)
        {
            return !isEndOfText(text, pos) && text.charAt(pos) == '-';
        }

        private static boolean isBlank(String text, int pos)
        {
            return !isEndOfText(text, pos) && text.charAt(pos) == ' ';
        }

        private static int toInt(char c)
        {
            return c - '0';
        }
    }
}
