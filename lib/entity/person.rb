module Synthea
  class Person < Synthea::Entity

    attr_accessor :record_synthea

    def initialize
      super
      @record_synthea = Synthea::Output::Record.new
    end

  end
end