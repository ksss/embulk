package org.embulk.spi.time;

/**
 * RubyTimeFormatSpecialToken represents a special-type token in Ruby-compatible time formats.
 *
 * Embulk's timestamp formats are based on Ruby's formats for historical reasons, and kept for compatibility.
 * Embulk maintains its own implementation of Ruby-compatible time parser to be independent from JRuby.
 *
 * This class is intentionally package-private so that plugins do not directly depend.
 */
class RubyTimeFormatSpecialToken extends RubyTimeFormatToken {
    RubyTimeFormatSpecialToken(final char specialCharacter) {
        super(RubyTimeFormatDirective.SPECIAL, specialCharacter);
    }

    @Override
    public String toString() {
        return "<Token " + this.getFormatDirective().toString() + " " + this.getSpecifier() + ">";
    }
}
