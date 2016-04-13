module Synthea
  class Entity
    attr_accessor :attributes # Hash
    attr_accessor :components # Hash
    attr_accessor :event_list     # Array of Synthea::Event

    def initialize
      @attributes = {}
      @components = {}
      @event_list = EventList.new
    end

    def had_event?(type)
      !event_list.select{|x|x.type==type}.first.nil?
    end

    def event(type)
      events(type).next
    end

    def events(type=nil)
      if type.nil?
        event_list
      else
        event_list.select{|x| x.type==type}
      end
    end

  end
end
