module Synthea
  class Person < Synthea::Entity
    attr_accessor :record_synthea
    attr_accessor :hospital

    def initialize
      super
      @record_synthea = Synthea::Output::Record.new
      @hospital = {}
    end

    def assign_default_hospital
      location = attributes[:coordinates_address].to_coordinates
      @hospital = Synthea::Hospital.find_closest(location)
    end
  end
end
