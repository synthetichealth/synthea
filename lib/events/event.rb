module Synthea
  class Event
    attr_accessor :time       # seconds since the epoch (Time)
    attr_accessor :type       # the type of event (symbol)
    attr_accessor :rule       # the rule that created this event (symbol)
    attr_accessor :processed  # if this event was handled or not (boolean)
    attr_accessor :attributes # key/value pairs (Hash)

    def initialize(time, type, rule, processed = false)
      @time = time
      @type = type
      @rule = rule
      @processed = processed
    end

    def to_s
      "#{@time}: #{@type}"
    end
  end
end
