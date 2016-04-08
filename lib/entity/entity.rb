module Synthea
  class Entity
    attr_accessor :attributes # Hash
    attr_accessor :components # Hash
    attr_accessor :events     # Array of Synthea::Event

    def initialize
      @attributes = {}
      @components = {}
      @events = []
    end

    def had_event?(type)
      !events.select{|x|x.type==type}.first.nil?
    end

    def get_events(type)
      events.select{|x|x.type==type}
    end

    def get_event(type)
      get_events(type).first
    end

    def has_unprocessed_event?(type)
      !get_events(type).select{|x|x.processed==false}.first.nil?
    end

    def get_unprocessed_event(type)
      get_events(type).select{|x|x.processed==false}.first
    end

  end
end
