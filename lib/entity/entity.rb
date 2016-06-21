module Synthea
  class Entity
    attr_accessor :attributes # Hash
    attr_accessor :events

    def initialize
      @attributes = {}
      @events = Synthea::EventList.new
    end

    def [](name)
      @attributes[name]
    end

    def []=(name, value)
      @attributes[name] = value
    end

    def had_event?(type)
      @events.events.has_key?(type)
    end

    def event(type)
      @events.events[type].try(:last)
    end

  end
end
