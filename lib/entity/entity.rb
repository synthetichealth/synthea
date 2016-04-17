module Synthea
  class Entity
    attr_accessor :attributes # Hash
    attr_accessor :event_list # Array of Synthea::Event

    def initialize
      @attributes = {}
      @event_list = EventList.new
    end

    def [](name)
      @attributes[name]
    end

    def []=(name, value)
      @attributes[name] = value
    end

    def had_event?(type)
      @event_list.any? { |e| e.type == type }
    end

    def event(type)
      events(type).next
    end

    def events(type = nil)
      if type.nil?
        @event_list
      else
        @event_list.select { |e| e.type == type }
      end
    end

  end
end
