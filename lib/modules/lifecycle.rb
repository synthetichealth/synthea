module Synthea
  module Modules
    class Lifecycle < Synthea::Rules

      # People are born
      rule :birth, [], [:age,:is_alive] do |time, entity|
        unless entity.had_event?(:birth)
          entity.attributes[:age] = 0
          entity.attributes[:name_first] = Faker::Name.first_name
          entity.attributes[:name_last] = Faker::Name.last_name
          entity.attributes[:gender] = gender
          # new babies are average weight and length for American newborns
          entity.attributes[:height] = 51 # centimeters
          entity.attributes[:weight] = 3.5 # kilograms
          entity.attributes[:is_alive] = true
          entity.events << Synthea::Event.new(time,:birth,:birth,true)
          entity.events << Synthea::Event.new(time,:encounter_ordered,:birth)
          # TODO update record
          # TODO update awareness
        end
      end

      # People age
      rule :age, [:birth,:age,:is_alive], [:age] do |time, entity|
        if entity.attributes[:is_alive]
          birthdate = entity.event(:birth).time
          age = entity.attributes[:age]
          entity.attributes[:age] = ((time.to_i - birthdate.to_i)/1.year).floor
          if(entity.attributes[:age] > age)
            entity.events << Synthea::Event.new(time,:grow,:age)
          end
          # TODO update record
          # TODO update awareness
        end
      end

      # People grow
      rule :grow, [:age,:is_alive,:gender], [:height,:weight,:bmi] do |time, entity|
        # Assume a linear growth rate until average size is achieved at age 20
        # TODO consider genetics, social determinants of health, etc
        while entity.attributes[:is_alive] && entity.events(:grow).unprocessed.next?
          event = entity.events(:grow).unprocessed.next
          event.processed=true
          age = entity.attributes[:age]
          gender = entity.attributes[:gender]
          # these growth numbers are based on internet data to produce average height/weight men and women
          # they are not "good numbers"
          if(age <= 20)
            if(gender=='M')
              entity.attributes[:height] += 6.3 # centimeters
              entity.attributes[:weight] += 3.325 * (1 + rand) # kilograms
            elsif(gender=='F')
              entity.attributes[:height] += 5.6 # centimeters
              entity.attributes[:weight] += 2.725 * (1 + rand) # kilograms
            end
          else
            # getting old and fat
            entity.attributes[:weight] += rand # kilograms            
          end
          # set the BMI
          entity.attributes[:bmi] = calculate_bmi(entity.attributes[:height],entity.attributes[:weight])
        end        
      end

      # People die
      rule :death, [:age], [] do |time, entity|
        unless entity.had_event?(:death)
          if(rand <= likelihood_of_death(entity.attributes[:age]))
            entity.attributes.delete(:is_alive)
            entity.events << Synthea::Event.new(time,:death,:death,true)
            # TODO update record
            # TODO update awareness
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
          0.00001392054794520548
        when age >= 1  && age <=4
          #15.6/100000/365
          0.0000004273972602739726
        when age >= 5  && age <=14
          #10.6/100000/365
          0.0000002904109589041096
        when age >= 15 && age <=24
          #56.4/100000/365
          0.0000015452054794520548
        when age >= 25 && age <=34
          #74.7/100000/365
          0.0000020465753424657535
        when age >= 35 && age <=44
          #145.7/100000/365
          0.000003991780821917808
        when age >= 45 && age <=54
          #326.5/100000/365
          0.000008945205479452055
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


    end
  end
end
