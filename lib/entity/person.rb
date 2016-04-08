module Synthea
  class Person < Synthea::Entity

    attr_accessor :record # Health Data Standards Record

    def initialize
      super
      @record = Record.new
    end

  end
end