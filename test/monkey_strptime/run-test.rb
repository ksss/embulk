base_dir = File.expand_path(File.join(File.dirname(__FILE__), "..", ".."))
lib_dir = File.join(base_dir, "lib")
test_dir = File.join(base_dir, "test")

$LOAD_PATH.unshift(lib_dir)
$LOAD_PATH.unshift(test_dir)

require "helper"
require "date"
require "test/unit"

module DateExt
  require "java"

  java_package "org.embulk.spi.time"

  def self.included base
    base.instance_eval do
      def _strptime(str, fmt='%F')
        map = parse_with_embulk_ruby_time_parser(fmt, str)
        return map.nil? ? nil : map.to_hash.inject({}){|hash,(k,v)| hash[k.to_sym] = v; hash}
      end

      def parse_with_embulk_ruby_time_parser(fmt, str)
        parser = org.embulk.spi.time.RubyTimeParser.new
        compiled_pattern = parser.compilePattern(fmt)
        time_parse_result = parser.parse(compiled_pattern, str)
        if time_parse_result.nil?
          return nil
        end
        return convert_time_parse_result_to_ruby_hash(time_parse_result)
      end

      def convert_time_parse_result_to_ruby_hash(time_parse_result)
        ruby_hash = {}
        time_parse_result.asMapLikeRubyHash().each do |key, value|
          if value.kind_of?(Java::java.math.BigDecimal)
            nanosecond = value.multiply(Java::java.math.BigDecimal::TEN.pow(9)).longValue();
            ruby_hash[key.to_s] = Rational(nanosecond, 10 ** 9)
          else
            ruby_hash[key.to_s] = value
          end
        end
        return ruby_hash
      end
    end
  end
end
Date.send(:include, DateExt)

Dir.glob("#{base_dir}/test/monkey_strptime/**/test{_,-}*.rb") do |file|
  require file.sub(/\.rb$/,"")
end

exit Test::Unit::AutoRunner.run
