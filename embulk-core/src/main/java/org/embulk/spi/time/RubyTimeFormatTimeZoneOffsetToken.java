package org.embulk.spi.time;

/**
 * RubyTimeFormatTimeZoneOffsetToken represents a timezone offset-type token in Ruby-compatible time formats.
 *
 * Embulk's timestamp formats are based on Ruby's formats for historical reasons, and kept for compatibility.
 * Embulk maintains its own implementation of Ruby-compatible time parser to be independent from JRuby.
 *
 * This class is intentionally package-private so that plugins do not directly depend.
 */
class RubyTimeFormatTimeZoneOffsetToken extends RubyTimeFormatToken {
    RubyTimeFormatTimeZoneOffsetToken(final int colons) {
        super(RubyTimeFormatDirective.TIME_OFFSET, 'z');
        this.colons = colons;
    }

    /**
     * Returns the string content of the string token.
     */
    int getColons() {
        return this.colons;
    }

    @Override
    public String toString() {
        return "<Token " + this.getFormatDirective().toString() + " " + this.colons + ">";
    }

    private final int colons;
}
