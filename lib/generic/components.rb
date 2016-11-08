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
      end
    end
  end
end
