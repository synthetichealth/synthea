module Synthea
  class Person < Synthea::Entity

  	# TODO: move the record into a separate class that tracks data
    attr_accessor :record_synthea# Health Data Standards Record

    def initialize
      super
      @record_synthea = Synthea::Output::Record.new
    end

  end
end