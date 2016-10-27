module Synthea
  class Entity
    attr_accessor :attributes # Hash
    attr_accessor :events

    def initialize
      @attributes = {}
      @events = Synthea::EventList.new

      # We represent symptoms as a hash of hashes; the first level key is the symptom name (ie :fatigue), the
      # second level key is the symptom cause (ie :diabetes), and the value is an integer ranging from 1-100
      # indicating severity; symptoms should only be accessed via the symptom API defined below
      @symptoms = Hash.new { |h, k| h[k] = {} }
    end

    def [](name)
      @attributes[name]
    end

    def []=(name, value)
      @attributes[name] = value
    end

    def had_event?(type, time = nil)
      if time
        !@events.before(time, type).empty?
      else
        @events.events.key?(type)
      end
    end

    def event(type)
      @events.events[type].try(:last)
    end

    def alive?(time = nil)
      event(:birth) && !had_event?(:death, time)
    end

    #-----------------------------------------------------------------------
    # Symptom API
    #-----------------------------------------------------------------------

    # Set value for a symptom, providing cause (ie :diabetes), type (ie :fatigue), and value ranging from 0-100
    def set_symptom_value(cause, type, value)
      raise 'Symptom value out of range' if value < 0 || value > 100
      @symptoms[type][cause] = value
    end

    # Set a random value for a symptom, providing a cause (ie :diabetes), type (ie :fatigue), and a weighting
    # indicating whether lower or higher numbers should be more likely, with a range from 0 (prefer lower
    # numbers) to 5 (even distribution) to 10 (prefer higher numbers)
    def set_symptom_weighted_random_value(cause, type, weighting)
      set_symptom_value(cause, type, Synthea::Modules::Lifecycle.weighted_random_distribution(1..100, weighting))
    end

    # Get the value of a symptom, combining all causes
    def get_symptom_value(type)
      # If multiple conditions cause the same symptom, we just use the worst
      @symptoms[type].values.max
    end

    # Get a list of all symptoms that exceed a provided threshold
    def get_symptoms_exceeding(threshold)
      @symptoms.keys.select { |type| get_symptom_value(type) > threshold } || []
    end
  end
end
