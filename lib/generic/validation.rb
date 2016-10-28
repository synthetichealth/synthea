module Synthea
  module Generic
    module Validation
      def self.included(base)
        base.extend(ClassMethods)
      end

      module ClassMethods
        def required_fields
          @required_fields ||= []
        end

        def required_field(field)
          required_fields << field
        end
      end

      def validate
        messages = []
        self.class.required_fields.each do |field|
          validate_required_field(field, messages)
        end
        messages
      end

      def validate_field_hash(field, messages)
        raise 'Validation hash must have exactly 1 top-level key' if field.size != 1

        if field[:or]
          valid = field[:or].any? { |f| validate_required_field(f, []) }
          unless valid
            messages << "At least one of [#{to_string(field[:or])}] is required on #{inspect}"
            return false
          end
        elsif field[:and]
          valid = field[:and].all? { |f| validate_required_field(f, []) }
          unless valid
            messages << "All of [#{to_string(field[:and])}] are required on #{inspect}"
            return false
          end
        end

        true
      end

      def validate_required_field(field, messages)
        valid = true

        if field.is_a?(Symbol)
          valid = send(field)
          messages << "Required field #{field} is missing on #{inspect}" unless valid
        elsif field.is_a?(Hash)
          valid = validate_field_hash(field, messages)
        else
          raise "Unexpected Required Field format. Expected Symbol or Hash, got: #{field}"
        end

        valid
      end

      def to_string(field)
        if field.is_a?(Symbol)
          field.to_s
        elsif field.is_a?(Hash)
          key, list = field.first
          # there's only one element, either field[:or] or field[:and]
          operator = " #{key} "
          list.map! { |f| to_string(f) }
          "(#{list.join(operator)})"
        else
          raise "Unexpected Required Field format. Expected Symbol or Hash, got: #{field}"
        end
      end
    end
  end
end
