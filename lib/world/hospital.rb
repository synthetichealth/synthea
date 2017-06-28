module Synthea
  class Hospital < Synthea::Provider
    cattr_accessor :hospital_list # Array - all hospitals that are imported

    def initialize(properties, coordinates)
      super(properties, coordinates)
      Hospital.hospital_list.push(self)
    end

    # rubocop:disable Style/ClassVars
    # from module.rb
    def self.hospital_list
      @@hospital_list ||= []
    end

    # finds closest ambulatory hospital to person based on geographical location
    def self.find_closest(person_location)
      closest_distance = 100
      closest_hospital = nil
      @@services[:ambulatory].each do |h|
        hospital_location = h.attributes[:coordinates]
        distance = Math.sqrt((person_location[0] - hospital_location[0])**2 + (person_location[1] - hospital_location[1])**2)
        if distance < closest_distance
          closest_distance = distance
          closest_hospital = h
        end
      end
      closest_hospital
    end

    def self.clear
      super
      Hospital.hospital_list.clear
    end
  end
end
