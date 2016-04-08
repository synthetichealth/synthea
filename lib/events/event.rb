module Synthea
  class Event

    attr_accessor :time # seconds since the epoch (Time)
    attr_accessor :type # the type of event (string)

    def initialize(time,type)
      @time = time
      @type = type
    end

    def to_s
      "#{@time}: #{@type}"
    end
  end
end
