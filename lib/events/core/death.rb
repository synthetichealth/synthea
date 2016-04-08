module Synthea
  module Events
    module Core
      class Death < Synthea::Event

        def initialize(now)
          super(now,self.class.to_s)
        end

        def apply(manager)
  
          patient = manager.patient 
          raise "patient cannot die twice" if patient.expired

          patient.deathdate = @time.to_i
          patient.expired = true

          #puts "     >>> #{patient.first} #{patient.last} died #{now.strftime("%m/%d/%Y")} at age: #{manager.age(now)}"
        end

      end
    end
  end
end