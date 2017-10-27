package org.embulk.spi.time;

/**
 * RubyTimeFormatToken represents a token in Ruby-compatible time formats.
 *
 * Embulk's timestamp formats are based on Ruby's formats for historical reasons, and kept for compatibility.
 * Embulk maintains its own implementation of Ruby-compatible time parser to be independent from JRuby.
 *
 * This class is intentionally package-private so that plugins do not directly depend.
 */
class RubyTimeFormatToken {
    RubyTimeFormatToken(final RubyTimeFormatDirective formatDirective, final char specifier) {
        this.formatDirective = formatDirective;
        this.specifier = specifier;
    }

    static RubyTimeFormatToken getFormatDirectiveToken(final char specifier) {
        final RubyTimeFormatDirective directive = RubyTimeFormatDirective.of(specifier);
        if (directive == null) {
            return null;
        }
        return directive.toToken(specifier);
    }

    RubyTimeFormatDirective getFormatDirective() {
        return this.formatDirective;
    }

    char getSpecifier() {
        return this.specifier;
    }

    @Override
    public String toString() {
        return "<Token " + formatDirective.toString() + " >";
    }

    private final RubyTimeFormatDirective formatDirective;
    private final char specifier;
}
