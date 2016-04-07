module Clyde
  module Event
    module Basic

      class Birth

        def apply(manager, now)
  
          patient = manager.patient 
          raise "patient cannot be reborn" if patient.birthdate

          #patient.title = Faker::
          patient.first = Faker::Name.first_name
          patient.last = Faker::Name.last_name

          patient.gender = gender
          patient.birthdate = now.to_i
          # patient.religious_affiliation = Faker::
          # patient.effective_time = Faker::

          # patient.race = Faker::
          # patient.ethnicity = Faker::

          # patient.languages = Faker::
          # patient.marital_status = Faker::
          # patient.medical_record_number = Faker::
          # patient.medical_record_assigner = Faker::

          patient.deathdate = nil
          patient.expired = false

          #puts ">>> #{patient.first} #{patient.last} was born on #{now.strftime("%m/%d/%Y")}"
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
      end
    end

  end
end