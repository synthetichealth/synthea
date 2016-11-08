module Synthea
  module Generic
    module Hashable
      # inspired by FHIR::Hashable

      def from_hash(hash)
        metadata = self.class.class_metadata
        hash.each do |key, value|
          next if key == 'remarks' # ignore remarks throughout
          config = metadata[key]
          # puts "Config for #{self.class}[#{key}] == #{config}"
          if config
            next if config[:ignore]
            key = config[:store_as] if config[:store_as]
            value = build_value(value, config)
          end
          begin
            # puts "Setting #{key}=#{value} on #{self}"
            send("#{key}=", value)
          rescue
            raise "Could not set field '#{key}' on #{inspect}.\nHash is #{hash}"
          end
        end
      end

      def build_value(value, config)
        type = config[:type]
        polymorphism = config[:polymorphism]

        if type || polymorphism
          value = if value.is_a?(Array) && (config[:max] || 0) > 1
                    value.map { |v| construct(v, type, polymorphism) }
                  else
                    construct(value, type, polymorphism)
                  end
        end

        if value && (config[:max] || 0) > 1 && !value.is_a?(Array)
          # if there is only one of these, but cardinality allows more, we need to wrap it in an array.
          value = [value]
        end

        value
      end

      def construct(value, type, polymorphism)
        if polymorphism && value.is_a?(Hash) && value[polymorphism[:key]]
          type = value[polymorphism[:key]].gsub(/\s+/, '_').camelize
          type = "#{polymorphism[:package]}::#{type}" if polymorphism[:package]
        end

        if type
          Object.const_get("Synthea::Generic::#{type}").new(value)
        else
          value
        end
      end
    end
  end
end
