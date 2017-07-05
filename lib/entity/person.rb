module Synthea
  class Person < Synthea::Entity
    attr_accessor :record_synthea
    attr_accessor :hospital

    def initialize
      super
      @record_synthea = Synthea::Output::Record.new
      @hospital = {}
    end

    def assign_ambulatory_provider
      location = attributes[:coordinates_address].to_coordinates
      provider = Synthea::Hospital.find_closest_ambulatory(location)
      @hospital[:ambulatory] = provider
    end

    def assign_inpatient_provider
      location = attributes[:coordinates_address].to_coordinates
      provider = Synthea::Hospital.find_closest_inpatient(location)
      @hospital[:inpatient] = provider
    end

    def assign_emergency_provider
      location = attributes[:coordinates_address].to_coordinates
      provider = Synthea::Hospital.find_closest_emergency(location)
      @hospital[:emergency] = provider
    end
  end
end
