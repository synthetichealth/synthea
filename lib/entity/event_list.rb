module Synthea
  class EventList

    EMPTY = [].freeze

    attr_accessor :events     # Hash of Synthea::Event

    def initialize
      @events = {}
      @events[:all] = []
      @events[:unprocessed] = []
    end

    def unprocessed
      @events[:unprocessed]
    end

    def process(event)
      event.processed = true
      @events[:unprocessed].delete(event)
    end

    def unprocessed_before(date,type)
      @events[:unprocessed].select{|e|e.type==type && e.time <= date}
    end

    def unprocessed_since(date,type)
      @events[:unprocessed].select{|e|e.type==type && e.time >= date}
    end

    def before(date,type=:all)
      list = @events[type]
      return EMPTY if list.nil? || list.empty?
      end_index = list.index{|e|e.time >= date}
      end_index = list.length if end_index.nil?
      if !end_index.nil?
        list[0..(end_index-1)]
      else
        EMPTY
      end
    end

    def since(date,type=:all)
      list = @events[type]
      return EMPTY if list.nil? || list.empty?
      end_index = list.index{|e|e.time >= date}
      if !end_index.nil?
        list[end_index..list.length]
      else
        EMPTY
      end
    end

    def create(time, type, rule, processed=false)
      @events[type] = [] if @events[type].nil?
      event = Synthea::Event.new(time, type, rule, processed)
      @events[type] << event
      @events[:all] << event
      @events[:unprocessed] << event if !processed
    end

  end
end
