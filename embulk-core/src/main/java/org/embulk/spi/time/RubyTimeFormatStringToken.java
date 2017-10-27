package org.embulk.spi.time;

/**
 * RubyTimeFormatStringToken represents a string-type token in Ruby-compatible time formats.
 *
 * Embulk's timestamp formats are based on Ruby's formats for historical reasons, and kept for compatibility.
 * Embulk maintains its own implementation of Ruby-compatible time parser to be independent from JRuby.
 *
 * This class is intentionally package-private so that plugins do not directly depend.
 */
class RubyTimeFormatStringToken extends RubyTimeFormatToken {
    RubyTimeFormatStringToken(final String stringContent) {
        super(RubyTimeFormatDirective.STRING, '\0');
        this.stringContent = stringContent;
    }

    /**
     * Returns the string content of the string token.
     */
    String getStringContent() {
        return this.stringContent;
    }

    @Override
    public String toString() {
        return "<Token " + this.getFormatDirective().toString() + " " + this.stringContent + ">";
    }

    private final String stringContent;
}
