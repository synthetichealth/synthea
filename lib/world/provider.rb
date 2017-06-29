module Synthea
  class Provider
    cattr_accessor :provider_list # Array - all providers that are imported
    cattr_accessor :services # Hash of services to hospitals that provide them

    attr_accessor :attributes # Hash
    attr_accessor :utilization # Hash
    attr_accessor :services_provided # Array - services include ambulatory, emergency, and inpatient

    def initialize(properties, coordinates)
      @attributes = properties
      @attributes[:coordinates] = coordinates

      # labs = lipid_panel, basic_metabolic_panel, electrolytes_panel
      # diabetes_labs = lipid_panel
      @utilization = { encounters: 0, procedures: 0, labs: 0, diabetes_labs: 0, prescriptions: 0 }

      Provider.provider_list.push(self)

      @services_provided = []
      services_split = properties['services_provided'].split
      services_split.each do |service|
        # add service to list of services provided by provider
        @services_provided.push(service.to_sym)

        # add provider to hash of services
        if Provider.services.key?(service.to_sym)
          Provider.services[service.to_sym].push(self)
        else
          Provider.services[service.to_sym] = [self]
        end
      end
    end

    # rubocop:disable Style/ClassVars
    # from module.rb
    def self.provider_list
      @@provider_list ||= []
    end

    # from module.rb
    def self.services
      @@services ||= {}
    end

    def self.find_closest_service(entity, service)
      if service == 'outpatient' || service == :outpatient
        service = 'ambulatory'
      end
      # if service is provided by default hospital, go there
      return entity.hospital if entity.hospital.service?(service.to_sym)

      person_location = entity.attributes[:coordinates_address].to_coordinates
      person_point = GeoRuby::SimpleFeatures::Point.from_x_y(person_location[0], person_location[1])
      closest_distance = 100_000_000
      closest_service = entity.hospital

      # if service is nil or not supproted by simulation, patient goes to default hospital
      if Provider.services.key?(service.to_sym)
        Provider.services[service.to_sym].each do |h|
          service_location = h.attributes[:coordinates]
          service_point = GeoRuby::SimpleFeatures::Point.from_x_y(service_location[0], service_location[1])
          spherical_distance = service_point.spherical_distance(person_point)
          if spherical_distance < closest_distance
            closest_distance = spherical_distance
            closest_service = h
          end
        end
      end
      closest_service
    end

    def service?(service)
      services_provided.include?(service)
    end

    def distance(entity)
      # TODO: finish implementation
      # person_location = entity.attributes[:coordinates_address].to_coordinates
      # person_point = GeoRuby::SimpleFeatures::Point.from_x_y(person_location[0], person_location[1])
    end

    def increment_encounters
      @utilization[:encounters] += 1
    end

    def increment_procedures
      @utilization[:procedures] += 1
    end

    def increment_labs
      @utilization[:labs] += 1
    end

    def increment_diabetes_labs
      @utilization[:diabetes_labs] += 1
    end

    def increment_prescriptions
      @utilization[:prescriptions] += 1
    end

    def reset_utilization
      @utilization.each_key do |u|
        @utilization[u] = 0
      end
    end

    def self.clear
      Provider.provider_list.clear
      Provider.services.clear
    end
  end
end
