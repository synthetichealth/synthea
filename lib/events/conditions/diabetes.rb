module Synthea
  module Events
    module Conditions
      class Diabetes < Synthea::Event
 
        def initialize(now)
          super(now,self.class.to_s)
        end

        def apply(manager, now)
          patient = manager.patient
   
          puts ">>> #{patient.first} #{patient.last} was diagnosed with Diabetes on #{now.strftime("%m/%d/%Y")}"
        end

      end
    end
  end
end