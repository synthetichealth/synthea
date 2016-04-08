module Synthea
  module Modules
    class Lifecycle < Synthea::Rules

      # People are born
      rule :birth, [], [:age,:is_alive] do
        unless @@entity.had_event?(:birth)
          @@entity.attributes[:age] = 0
          @@entity.attributes[:name_first] = Faker::Name.first_name
          @@entity.attributes[:name_last] = Faker::Name.last_name
          @@entity.attributes[:gender] = Synthea::Modules::Lifecycle.gender
          @@entity.attributes[:height] = 51 # centimeters
          @@entity.attributes[:weight] = 3.5 # kilograms
          @@entity.components[:is_alive] = true
          @@entity.events << Synthea::Event.new(@@time,:birth,true)
          # TODO update record
          # TODO update awareness
        end
      end

      # People age
      rule :age, [:birth,:age,:is_alive], [:age] do
        if @@entity.components[:is_alive]
          birthdate = @@entity.get_event(:birth).time
          age = @@entity.attributes[:age]
          @@entity.attributes[:age] = ((@@time.to_i - birthdate.to_i)/1.year).floor
          if(@@entity.attributes[:age] > age)
            @@entity.events << Synthea::Event.new(@@time,:grow)
          end
          # TODO update record
          # TODO update awareness
        end
      end

      # People grow
      rule :grow, [:age,:is_alive,:gender], [:height,:weight] do
        # Assume a linear growth rate until average size is achieved at age 20
        # TODO consider genetics, social determinants of health, etc
        while @@entity.components[:is_alive] && @@entity.has_unprocessed_event?(:grow)
          event = @@entity.get_unprocessed_event(:grow)
          event.processed=true
          age = @@entity.attributes[:age]
          gender = @@entity.attributes[:gender]
          if(age <= 20)
            if(gender=='M')
              @@entity.attributes[:height] += 6.3 # centimeters
              @@entity.attributes[:weight] += 3.325 # kilograms
            elsif(gender=='F')
              @@entity.attributes[:height] += 5.6 # centimeters
              @@entity.attributes[:weight] += 2.725 # kilograms
            end
          else
            # getting old and fat
            @@entity.attributes[:weight] += 0.25 # kilograms            
          end
        end        
      end

      # People die
      rule :death, [:age], [] do
        unless @@entity.had_event?(:death)
          if(rand <= Synthea::Modules::Lifecycle.likelihood_of_death(@@entity.attributes[:age]))
            @@entity.components.delete(:is_alive)
            @@entity.events << Synthea::Event.new(@@time,:death,true)
            # TODO update record
            # TODO update awareness
          end
        end
      end

      def self.gender(ratios = {male: 0.5})
        value = rand
        case 
          when value < ratios[:male]
            'M'
          else
            'F'
        end
      end

      def self.likelihood_of_death(age)
        # http://www.cdc.gov/nchs/nvss/mortality/gmwk23r.htm: 820.4/100000
        case 
        when age < 1
          #508.1/1/365
          0.00001392054794520548
        when age >= 1  && age <=4
          #15.6/100000/365
          0.000004273972602739726
        when age >= 5  && age <=14
          #10.6/100000/365
          0.000002904109589041096
        when age >= 15 && age <=24
          #56.4/100000/365
          0.000015452054794520548
        when age >= 25 && age <=34
          #74.7/100000/365
          0.000020465753424657535
        when age >= 35 && age <=44
          #145.7/100000/365
          0.00003991780821917808
        when age >= 45 && age <=54
          #326.5/100000/365
          0.00008945205479452055
        when age >= 55 && age <=64
          #737.8/100000/365
          0.000020213698630136987
        when age >= 65 && age <=74
          #1817.0/100000/365
          0.00004978082191780822
        when age >= 75 && age <=84
          #4877.3/100000/365
          0.00013362465753424658
        when age >= 85 && age <=94
          #13499.4/100000/365
          0.00036984657534246574
        else
          #50000/100000/365
          0.0013698630136986301
        end
      end

    end
  end
end
