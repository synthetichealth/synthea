module Synthea
  module Modules
    class Lifecycle < Synthea::Rules

      attr_accessor :male_growth, :male_weight, :female_growth, :female_weight
      attr_accessor :races, :ethnicity, :blood_types

      def initialize
        super
        @male_growth = Distribution::Normal.rng(Synthea::Config.lifecycle.growth_rate_male_average,Synthea::Config.lifecycle.growth_rate_male_stddev)
        @male_weight = Distribution::Normal.rng(Synthea::Config.lifecycle.weight_gain_male_average,Synthea::Config.lifecycle.weight_gain_male_stddev)
        @female_growth = Distribution::Normal.rng(Synthea::Config.lifecycle.growth_rate_female_average,Synthea::Config.lifecycle.growth_rate_female_stddev)
        @female_weight = Distribution::Normal.rng(Synthea::Config.lifecycle.weight_gain_female_average,Synthea::Config.lifecycle.weight_gain_female_stddev)     
      
        # https://en.wikipedia.org/wiki/Demographics_of_Massachusetts#Race.2C_ethnicity.2C_and_ancestry
        @races = Pickup.new({
          :white => 75.1,
          :hispanic => 10.5,
          :black => 8.1,
          :asian => 6.0,
          :native => 0.5,
          :other => 0.1
        })
        @ethnicity = {
          :white => Pickup.new({
            :irish => 22.8,
            :italian => 13.9,
            :english => 10.7,
            :french => 7.8,
            :german => 6.4,
            :polish => 5.0,
            :portuguese => 4.7,
            :american => 4.4,
            :french_canadian => 3.8,
            :scottish => 2.4,
            :russian => 1.9,
            :swedish => 1.8,
            :greek => 1.2
            }),
          :hispanic => Pickup.new({
            :puerto_rican => 4.1,
            :mexican => 1,
            :central_american => 1,
            :south_american => 1
            }),
          :black => Pickup.new({
            :african => 1.8,
            :dominican => 1.8,
            :west_indian => 1.8
            }),
          :asian => Pickup.new({
            :chinese => 2.0,
            :asian_indian => 1.1
            }),
          :native => Pickup.new({
            :american_indian => 1
            }),
          :other => Pickup.new({
            :arab => 1
            })
        }
        # blood type data from http://www.redcrossblood.org/learn-about-blood/blood-types
        # data for :native and :other from https://en.wikipedia.org/wiki/Blood_type_distribution_by_country
        @blood_types = {
          :white => Pickup.new({
            :o_positive => 37,
            :o_negative => 8,
            :a_positive => 33,
            :a_negative => 7,
            :b_positive => 9,
            :b_negative => 2,
            :ab_positive => 3,
            :ab_negative => 1            
            }),
          :hispanic => Pickup.new({
            :o_positive => 53,
            :o_negative => 4,
            :a_positive => 29,
            :a_negative => 2,
            :b_positive => 9,
            :b_negative => 1,
            :ab_positive => 2,
            :ab_negative => 1 
            }),
          :black => Pickup.new({
            :o_positive => 47,
            :o_negative => 4,
            :a_positive => 24,
            :a_negative => 2,
            :b_positive => 18,
            :b_negative => 1,
            :ab_positive => 4,
            :ab_negative => 1
            }),
          :asian => Pickup.new({
            :o_positive => 39,
            :o_negative => 1,
            :a_positive => 27,
            :a_negative => 1,
            :b_positive => 25,
            :b_negative => 1,
            :ab_positive => 7,
            :ab_negative => 1 
            }),
          :native => Pickup.new({
            :o_positive => 37.4,
            :o_negative => 6.6,
            :a_positive => 35.7,
            :a_negative => 6.3,
            :b_positive => 8.5,
            :b_negative => 1.5,
            :ab_positive => 3.4,
            :ab_negative => 0.6 
            }),
          :other => Pickup.new({
            :o_positive => 37.4,
            :o_negative => 6.6,
            :a_positive => 35.7,
            :a_negative => 6.3,
            :b_positive => 8.5,
            :b_negative => 1.5,
            :ab_positive => 3.4,
            :ab_negative => 0.6 
            })
        }

      end

      # People are born
      rule :birth, [], [:age,:is_alive] do |time, entity|
        unless entity.had_event?(:birth)
          entity[:age] = 0
          entity[:name_first] = Faker::Name.first_name
          entity[:name_first] = "#{entity[:name_first]}#{(entity[:name_first].hash % 999)}"
          entity[:name_last] = Faker::Name.last_name
          entity[:name_last] = "#{entity[:name_last]}#{(entity[:name_last].hash % 999)}"
          entity[:gender] = gender
          entity[:race] = @races.pick
          entity[:ethnicity] = @ethnicity[ entity[:race] ].pick
          entity[:blood_type] = @blood_types[ entity[:race] ].pick
          # new babies are average weight and length for American newborns
          entity[:height] = 51 # centimeters
          entity[:weight] = 3.5 # kilograms
          entity[:is_alive] = true
          entity.events.create(time, :birth, :birth, true)
          entity.events.create(time, :encounter, :birth)

          #determine lat/long coordinates of address within Bedford
          location_data = Synthea::Location.selectPoint
          entity[:coordinates_address] = location_data['point']
          zip_code = Synthea::Location.get_zipcode(location_data['city'])
          #zip_code = "#{location_data['city']}, MA".to_zip[0]
          #zip = Area.zip_codes.find{|x|x.first == zip_code}
          #zip = Area.zip_codes.sample if zip.nil?
          entity[:address] = {
            'line' => [ Faker::Address.street_address ],
            'city' => location_data['city'],
            'state' => "MA",
            'postalCode' => zip_code
          }
          entity[:address]['line'] << Faker::Address.secondary_address if (rand < 0.5)
          
          
          # TODO update awareness
        end
      end

      # People age
      rule :age, [:birth,:age,:is_alive], [:age] do |time, entity|
        if entity[:is_alive]
          birthdate = entity.event(:birth).time
          age = entity[:age]
          entity[:age] = ((time.to_i - birthdate.to_i)/1.year).floor
          if(entity[:age] > age)
            dt = nil
            begin
              dt = DateTime.new(time.year,birthdate.month,birthdate.mday,birthdate.hour,birthdate.min,birthdate.sec,birthdate.formatted_offset)
            rescue Exception => e
              # this person was born on a leap-day
              dt = time
            end
            entity.events.create(dt.to_time, :grow, :age)
          end
          # TODO update awareness
        end
      end

      # People grow
      rule :grow, [:age,:is_alive,:gender], [:height,:weight,:bmi] do |time, entity|
        # Assume a linear growth rate until average size is achieved at age 20
        # TODO consider genetics, social determinants of health, etc
        if entity[:is_alive]
          unprocessed_events = entity.events.unprocessed.select{|e|e.type==:grow}
          unprocessed_events.each do |event|
            entity.events.process(event)
            age = entity[:age]
            gender = entity[:gender]
            if(age <= 20)
              if(gender=='M')
                entity[:height] += @male_growth.call # centimeters
                entity[:weight] += @male_weight.call # kilograms
              elsif(gender=='F')
                entity[:height] += @female_growth.call # centimeters
                entity[:weight] += @female_weight.call # kilograms
              end
            elsif(age <= Synthea::Config.lifecycle.adult_max_weight_age)
              # getting older and fatter
              if(gender=='M')
                entity[:weight] *= (1 + Synthea::Config.lifecycle.adult_male_weight_gain)
              elsif(gender=='F')
                entity[:weight] *= (1 + Synthea::Config.lifecycle.adult_female_weight_gain)
              end           
            else
              # TODO random change in weight?
            end
            # set the BMI
            entity[:bmi] = calculate_bmi(entity[:height],entity[:weight])
          end
        end   
      end

      # People die
      rule :death, [:age], [] do |time, entity|
        unless entity.had_event?(:death)
          if(rand <= likelihood_of_death(entity[:age]))
            entity[:is_alive] = false
            entity.events.create(time, :death, :death, true)
            self.class.record_death(entity,time)
          end
        end
      end
      
      def gender(ratios = {male: 0.5})
        value = rand
        case 
          when value < ratios[:male]
            'M'
          else
            'F'
        end
      end

      # height in centimeters
      # weight in kilograms
      def calculate_bmi(height,weight)
        ( weight / ( (height/100) * (height/100) ) )
      end

      def likelihood_of_death(age)
        # http://www.cdc.gov/nchs/nvss/mortality/gmwk23r.htm: 820.4/100000
        case 
        when age < 1
          #508.1/100000/365
          x = 508.1/100000
        when age >= 1  && age <=4
          #15.6/100000/365
          x = 15.6/100000
        when age >= 5  && age <=14
          #10.6/100000/365
          x = 10.6/100000
        when age >= 15 && age <=24
          #56.4/100000/365
          x = 56.4/100000
        when age >= 25 && age <=34
          #74.7/100000/365
          x = 74.7/100000
        when age >= 35 && age <=44
          #145.7/100000/365
          x = 145.7/100000
        when age >= 45 && age <=54
          #326.5/100000/365
          x = 326.5/100000
        when age >= 55 && age <=64
          #737.8/100000/365
          x = 737.8/100000
        when age >= 65 && age <=74
          #1817.0/100000/365
          x = 1817.0/100000
        when age >= 75 && age <=84
          #4877.3/100000/365
          x = 4877.3/100000
        when age >= 85 && age <=94
          #13499.4/100000/365
          x = 13499.4/100000
        else
          #50000/100000/365
          x = 50000.to_f/100000
        end
        return Synthea::Rules.convert_risk_to_timestep(x,365)
      end

      def self.age(time, birthdate, deathdate, unit=:years)
        case unit
        when :months
          left = deathdate.nil? ? time : deathdate
          (left.month - birthdate.month) + (12 * (left.year - birthdate.year)) + (left.day < birthdate.day ? -1 : 0)
        else
          divisor = 1.method(unit).call

          left = deathdate.nil? ? time : deathdate
          ((left - birthdate)/divisor).floor
        end
      end

      #------------------------------------------------------------------------# begin class record functions
      def self.record_death(entity, time)
        entity.record_synthea.death(time)
      end

      def self.record_height_weight(entity, time)
        entity.record_synthea.observation(:weight, time, entity[:weight], :observation, :vital_sign)
        entity.record_synthea.observation(:height, time, entity[:height], :observation, :vital_sign)
      end
    end
  end
end
