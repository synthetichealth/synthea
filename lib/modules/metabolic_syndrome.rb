module Synthea
  module Modules
    class MetabolicSyndrome < Synthea::Rules

      # People have a BMI that we can roughly use to estimate
      # blood glucose and diabetes
      rule :metabolic_syndrome, [:bmi], [:blood_glucose,:prediabetic,:diabetes] do |time, entity|
        bmi = entity.attributes[:bmi]
        if bmi
          entity.attributes[:blood_glucose] = blood_glucose(bmi)
          if(entity.attributes[:blood_glucose] < 5.7)
            # normal person
          elsif(entity.attributes[:blood_glucose] < 6.5)
            entity.attributes[:prediabetic]={} 
            entity.events << Synthea::Event.new(time,:prediabetic,:metabolic_syndrome,false) if !entity.had_event?(:prediabetic)
          elsif(entity.attributes[:blood_glucose] < 7.5)
            update_diabetes(1,time,entity)
          elsif(entity.attributes[:blood_glucose] < 9)
            update_diabetes(2,time,entity)
          else  
            update_diabetes(3,time,entity)
          end
        end
      end

      def update_diabetes(severity,time,entity)
        diabetes = entity.attributes[:diabetes]
        if diabetes.nil?
          diabetes = {}
          diabetes[:duration] = 0
          entity.attributes[:diabetes]=diabetes
          entity.events << Synthea::Event.new(time,:diabetes,:metabolic_syndrome,false) if !entity.had_event?(:diabetes)
        end
        diabetes[:severity] = severity
        diabetes[:duration] += 1
      end

      # prediabetics have symptoms
      rule :prediabetes?, [:prediabetic], [:hunger,:fatigue,:vision_blurred,:tingling_hands_feet] do |time,entity|
        prediabetes = entity.attributes[:prediabetic]
        if prediabetes
          prediabetes[:hunger] = rand
          prediabetes[:fatigue] = rand
          prediabetes[:vision_blurred] = rand
          prediabetes[:tingling_hands_feet] = rand
        end
      end

      # diabetics have symptoms
      rule :diabetes?, [:diabetes], [:hunger,:fatigue,:vision_blurred,:tingling_hands_feet,:urination_frequent,:thirst] do |time,entity|
        diabetes = entity.attributes[:diabetes]
        if diabetes
          diabetes[:hunger] = rand * diabetes[:severity]
          diabetes[:fatigue] = rand * diabetes[:severity]
          diabetes[:vision_blurred] = rand * diabetes[:severity]
          diabetes[:tingling_hands_feet] = rand * diabetes[:severity]
          diabetes[:urination_frequent] = rand * diabetes[:severity]
          diabetes[:thirst] = rand * diabetes[:severity]      
        end
      end

      # TODO diabetics have nephropathy (kidney failure) -> transplant or death
      # TODO diabetics have retinopathy (eye failure) -> blindness
      # TODO diabetics have neuropathy (nerve damage) -> amputations

      # rough linear fit seen in Figure 1
      # http://www.microbecolhealthdis.net/index.php/mehd/article/viewFile/22857/34046/125897
      def blood_glucose(bmi)
        ((bmi - 6) / 6.5)
      end

      class Record < BaseRecord
        def self.diagnoses(entity, time)
          patient = entity.record
          if entity.attributes[:prediabetic] && !entity.record_conditions[:prediabetes]
            # create the ongoing diagnosis
            entity.record_conditions[:prediabetes] = Condition.new(condition_hash(:prediabetes, time))
            patient.conditions << entity.record_conditions[:prediabetes]
          elsif !entity.attributes[:prediabetic] && entity.record_conditions[:prediabetes]
            # end the diagnosis
            entity.record_conditions[:prediabetes].end_time = time.to_i
            entity.record_conditions[:prediabetes] = nil
          end

          if entity.attributes[:diabetes] && !entity.record_conditions[:diabetes]
            # create the ongoing diagnosis
            entity.record_conditions[:diabetes] = Condition.new(condition_hash(:diabetes, time))
            patient.conditions << entity.record_conditions[:diabetes]
          elsif !entity.attributes[:diabetes] && entity.record_conditions[:diabetes]
            # end the diagnosis
            entity.record_conditions[:diabetes].end_time = time.to_i
            entity.record_conditions[:diabetes] = nil
          end          
        end
      end

    end
  end
end
