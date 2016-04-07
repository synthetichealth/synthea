module Clyde
  module Event
    module Condition

      class Diabetes

        def apply(manager, now)
          patient = manager.patient
   
          puts ">>> #{patient.first} #{patient.last} was diagnosed with Diabetes on #{now.strftime("%m/%d/%Y")}"

        end

      end
    end

  end
end