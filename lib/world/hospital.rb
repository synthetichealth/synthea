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
    def self.find_closest(entity, person_location)
      person_point = GeoRuby::SimpleFeatures::Point.from_x_y(person_location[0], person_location[1])

      # find closest hospital with ambulatory service
      closest_distance = 100_000_000
      closest_hospital = nil
      @@services[:ambulatory].each do |h|
        hospital_location = h.attributes[:coordinates]
        hospital_point = GeoRuby::SimpleFeatures::Point.from_x_y(hospital_location[0], hospital_location[1])
        spherical_distance = hospital_point.spherical_distance(person_point)
        if spherical_distance < closest_distance
          closest_distance = spherical_distance
          closest_hospital = h
        end
      end
      entity.hospital[:ambulatory] = closest_hospital

      # find closest hospital with inpatient service
      closest_distance = 100_000_000
      closest_hospital = nil
      @@services[:inpatient].each do |h|
        hospital_location = h.attributes[:coordinates]
        hospital_point = GeoRuby::SimpleFeatures::Point.from_x_y(hospital_location[0], hospital_location[1])
        spherical_distance = hospital_point.spherical_distance(person_point)
        if spherical_distance < closest_distance
          closest_distance = spherical_distance
          closest_hospital = h
        end
      end
      entity.hospital[:inpatient] = closest_hospital

      # find closest hopital with emergency service
      closest_distance = 100_000_000
      closest_hospital = nil
      @@services[:emergency].each do |h|
        hospital_location = h.attributes[:coordinates]
        hospital_point = GeoRuby::SimpleFeatures::Point.from_x_y(hospital_location[0], hospital_location[1])
        spherical_distance = hospital_point.spherical_distance(person_point)
        if spherical_distance < closest_distance
          closest_distance = spherical_distance
          closest_hospital = h
        end
      end
      entity.hospital[:emergency] = closest_hospital
    end

    def self.clear
      super
      Hospital.hospital_list.clear
    end
  end
end
