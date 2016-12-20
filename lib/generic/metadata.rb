module Synthea
  module Generic
    module Metadata
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

        def class_metadata
          @class_metadata ||= {}
        end

        def metadata(field, options)
          class_metadata[field] = options
        end

        def inherited(klass)
          super
          # this is needed because otherwise the children classes don't get the parent required fields
          klass.required_fields.push(*required_fields)
          klass.class_metadata.merge!(class_metadata)
        end
      end

      # context: the parent context (module) to which this object belongs
      # path: array of parent objects that lead to this object (ie, state, transition/condition/etc)
      def validate(context, path)
        messages = []
        path = path.dup << self
        self.class.required_fields.each do |field|
          validate_required_field(field, messages, path)
        end
        self.class.class_metadata.each do |field, config|
          next if config[:ignore]
          field = config[:store_as] if config[:store_as]
          value = send(field)

          # TODO: Update validation of references to states to accomodate submodules.
          # Disabling validation of references to states for the time being.
          #
          # if value && config[:reference_to_state_type]
          #   state = context.config['states'][value]
          #   if state.nil?
          #     messages << build_message("#{field} references state '#{value}' which does not exist", path)
          #   elsif config[:reference_to_state_type] != 'State' && config[:reference_to_state_type] != state['type']
          #     messages << build_message("#{field} is expected to refer to a '#{config[:reference_to_state_type]}' but value '#{value}' is actually a '#{state['type']}'", path)
          #   end
          # end

          if value.is_a?(Array)
            value.each { |v| messages.push(*v.validate(context, path)) if v.respond_to?(:validate) }
          elsif value.respond_to?(:validate)
            messages.push(*value.validate(context, path))
          end
        end
        messages.uniq
      end

      def validate_field_hash(field, messages, path)
        raise "Validation hash must have exactly 1 top-level key, got (#{field.keys}) on #{self}" if field.size != 1

        if field[:or]
          valid = field[:or].any? { |f| validate_required_field(f, [], path) }
          # messages passed as [] because we don't care about the messages we get back here
          unless valid
            messages << build_message("At least one of #{to_string(field)} is required on #{self}", path)
            return false
          end
        elsif field[:and]
          valid = field[:and].all? { |f| validate_required_field(f, [], path) }
          unless valid
            messages << build_message("All of #{to_string(field)} are required on #{self}", path)
            return false
          end
        end

        true
      end

      def validate_required_field(field, messages, path)
        valid = true

        if field.is_a?(Symbol)
          valid = send(field)
          messages << build_message("Required '#{field}' is missing on #{self}", path) unless valid
        elsif field.is_a?(Hash)
          valid = validate_field_hash(field, messages, path)
        else
          raise "Unexpected Required Field format. Expected Symbol or Hash, got: #{field} on #{self}"
        end

        valid
      end

      def build_message(msg, path)
        "#{msg}\n  Path: #{path.join(';')}"
      end

      def to_string(field)
        if field.is_a?(Symbol)
          field.to_s
        elsif field.is_a?(Hash)
          raise 'Validation hash must have exactly 1 top-level key' if field.size != 1
          key, list = field.first
          # there's only one element, either field[:or] or field[:and]
          operator = " #{key} "
          list = list.map { |f| to_string(f) }
          "(#{list.join(operator)})"
        else
          raise "Unexpected Required Field format. Expected Symbol or Hash, got: #{field.inspect} on #{self}"
        end
      end
    end
  end
end
