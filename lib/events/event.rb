module Synthea
  class Event

    attr_accessor :time # seconds since the epoch (Time)
    attr_accessor :type # the type of event (string)
    attr_accessor :processed # if this event was handled or not

    def initialize(time,type,processed=false)
      @time = time
      @type = type
      @processed = processed
    end

    def to_s
      "#{@time}: #{@type}"
    end
  end
end
