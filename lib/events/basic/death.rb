module Clyde
  module Event
    module Basic

      class Death

        def apply(manager, now)
  
          patient = manager.patient 
          raise "patient cannot die twice" if patient.expired

          patient.deathdate = now.to_i
          patient.expired = true

          #puts "     >>> #{patient.first} #{patient.last} died #{now.strftime("%m/%d/%Y")} at age: #{manager.age(now)}"
        end

      end
    end

  end
end