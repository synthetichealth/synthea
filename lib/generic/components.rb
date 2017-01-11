module Synthea
  module Generic
    module Components
      # generic "Components" that don't fit anywhere else
      class Component
        include Synthea::Generic::Metadata
        include Synthea::Generic::Hashable

        def initialize(hash)
          from_hash(hash)
        end
      end

      class Range < Component
        attr_accessor :low, :high
        required_field and: [:low, :high]

        def value
          rand(low..high)
        end
      end

      class RangeWithUnit < Range
        attr_accessor :unit
        required_field :unit

        def value
          rand(low.send(unit)..high.send(unit))
        end
      end

      class Exact < Component
        attr_accessor :quantity
        required_field :quantity

        def value
          quantity
        end
      end

      class ExactWithUnit < Exact
        attr_accessor :unit
        required_field :unit

        def value
          quantity.send(unit)
        end
      end

      class Code < Component
        attr_accessor :code, :system, :display
        required_field and: [:code, :system, :display]

        def to_sym
          raise 'Code must have a display value to convert to a symbol' if display.nil?
          display.gsub(/\s+/, '_').downcase.to_sym
        end
      end

      class Dosage < Component
        attr_accessor :amount, :frequency, :period, :unit
        required_field and: [:amount, :frequency, :period, :unit]

        def period_value
          period.send(unit)
        end
      end

      class Prescription < Component
        attr_accessor :as_needed, :dosage, :duration, :instructions, :refills
        required_field or: [:as_needed, and: [:dosage, :duration]]

        metadata 'dosage', type: 'Components::Dosage', min: 1, max: 1
        metadata 'duration', type: 'Components::ExactWithUnit', min: 1, max: 1
        metadata 'instructions', type: 'Components::Code', min: 0, max: Float::INFINITY

        def doses
          # Returns the total number of doses based on the dosage and duration.
          # This is used to infer the total number of doses in a prescription.
          if @as_needed
            0
          else
            (duration.value / dosage.period_value) * dosage.amount * dosage.frequency
          end
        end

        def patient_instructions
          # Returns a single string representing any and all instructions.
          # Format: "<instr_1>; <instr_2>; <instr_3>" etc.
          # Used primarilly for CCDA export.
          fi = ''
          unless @instructions.length.zero?
            fi += @instructions[0].display
            additional_instrs = @instructions.drop(1)
            additional_instrs.each do |instr|
              fi += '; ' + instr.display
            end
          end
          fi
        end
      end
    end
  end
end
