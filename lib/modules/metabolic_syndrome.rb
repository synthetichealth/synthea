module Synthea
  module Modules
    class MetabolicSyndrome < Synthea::Rules

      # People have a BMI that we can roughly use to estimate
      # blood glucose and diabetes
      rule :metabolic_syndrome, [:bmi], [:blood_glucose,:prediabetes,:diabetes] do |time, entity|
        bmi = entity[:bmi]
        if bmi
          entity[:blood_glucose] = blood_glucose(bmi)
          if(entity[:blood_glucose] < 5.7)
            # normal person
          elsif(entity[:blood_glucose] < 6.5)
            update_prediabetes(time,entity)
          elsif(entity[:blood_glucose] < 7.5)
            update_diabetes(1,time,entity)
          elsif(entity[:blood_glucose] < 9)
            update_diabetes(2,time,entity)
          else  
            update_diabetes(3,time,entity)
          end
        end
      end

      def update_prediabetes(time,entity)
        prediabetes = entity[:prediabetes]
        if prediabetes.nil?
          prediabetes = {}
          prediabetes[:duration] = 0
          entity[:prediabetes]=prediabetes
          entity.events.create(time, :prediabetes, :metabolic_syndrome, false) if !entity.had_event?(:prediabetes)
        end
        prediabetes[:duration] += 1
      end

      def update_diabetes(severity,time,entity)
        diabetes = entity[:diabetes]
        if diabetes.nil?
          diabetes = {}
          diabetes[:duration] = 0
          entity[:diabetes]=diabetes
          entity.events.create(time, :diabetes, :metabolic_syndrome, false) if !entity.had_event?(:diabetes)
        end
        diabetes[:severity] = severity
        diabetes[:duration] += 1   
      end

      rule :prediabetes, [:metabolic_syndrome], [:diabetes] do |time, entity|
        prediabetes = entity[:prediabetes]
        if prediabetes
          prediabetes[:hunger] = rand
          prediabetes[:fatigue] = rand
          prediabetes[:vision_blurred] = rand
          prediabetes[:tingling_hands_feet] = rand
        end
      end

      rule :diabetes, [:metabolic_syndrome,:prediabetes],[:nephropathy,:retinopathy,:neuropathy] do |time, entity|
        diabetes = entity[:diabetes]
        if diabetes
          diabetes[:nephropathy] = true
          diabetes[:retinopathy] = true
          diabetes[:neuropathy] = true
          # symptoms
          diabetes[:hunger] = rand * diabetes[:severity]
          diabetes[:fatigue] = rand * diabetes[:severity]
          diabetes[:vision_blurred] = rand * diabetes[:severity]
          diabetes[:tingling_hands_feet] = rand * diabetes[:severity]
          diabetes[:urination_frequent] = rand * diabetes[:severity]
          diabetes[:thirst] = rand * diabetes[:severity]  
        end
      end

      #-----------------------------------------------------------------------#

      # KIDNEY FAILURE: diabetics have nephropathy which can lead to transplant or death
      rule :nephropathy, [:diabetes], [:microalbuminuria] do |time,entity|
        diabetes = entity[:diabetes]
        if diabetes && diabetes[:nephropathy] && diabetes[:microalbuminuria].nil? && (rand < (0.01 * diabetes[:severity]))
          diabetes[:microalbuminuria] = true
          entity.events.create(time, :microalbuminuria, :nephropathy, true)
        end
      end

      # KIDNEY FAILURE: microalbhuminuria - a moderate increase in the level of albumin in urine
      rule :microalbuminuria, [:nephropathy], [:proteinuria] do |time,entity|
        diabetes = entity[:diabetes]
        if diabetes && diabetes[:microalbuminuria] && diabetes[:proteinuria].nil? && (rand < (0.01 * diabetes[:severity]))
          diabetes[:proteinuria] = true
          entity.events.create(time, :proteinuria, :microalbuminuria, true)
        end
      end

      # KIDNEY FAILURE: proteinuria - excess serum proteins in the urine
      rule :proteinuria, [:microalbuminuria], [:end_stage_renal_disease] do |time,entity|
        diabetes = entity[:diabetes]
        if diabetes && diabetes[:proteinuria] && diabetes[:end_stage_renal_disease].nil? && (rand < (0.01 * diabetes[:severity]))
          diabetes[:end_stage_renal_disease] = true 
          entity.events.create(time, :end_stage_renal_disease, :proteinuria, true)
        end
      end

      # KIDNEY FAILURE: End-Stage Renal Disease (ESRD), this is the end...
      rule :end_stage_renal_disease, [:proteinuria], [:kidney_dialysis,:kidney_transplant,:death] do |time,entity|
        diabetes = entity[:diabetes]
        if diabetes && diabetes[:end_stage_renal_disease] && (rand < (0.01 * diabetes[:severity]))
          entity[:is_alive] = false
          entity.events.create(time, :death, :end_stage_renal_disease, true)
          entity.record.deathdate = time.to_i
          entity.record.expired = true
        end
      end

      # TODO Add kidney dialysis treatments into Encounters and records
      # TODO Add kidney transplant into Encounters and records

      #-----------------------------------------------------------------------#

      # EYE FAILURE: diabetics have retinopathy (eye failure)
      rule :retinopathy, [:diabetes], [:nonproliferative_retinopathy] do |time,entity|
        diabetes = entity[:diabetes]
        if diabetes && diabetes[:retinopathy] && diabetes[:nonproliferative_retinopathy].nil? && (rand < (0.01 * diabetes[:severity]))
          diabetes[:nonproliferative_retinopathy] = true
          entity.events.create(time, :nonproliferative_retinopathy, :retinopathy, true)
        end
      end

      # EYE FAILURE: diabetics have retinopathy (eye failure)
      rule :nonproliferative_retinopathy, [:retinopathy], [:proliferative_retinopathy, :macular_edema, :blindness] do |time,entity|
        diabetes = entity[:diabetes]
        if diabetes && diabetes[:nonproliferative_retinopathy]
          if diabetes[:proliferative_retinopathy].nil? && (rand < (0.01 * diabetes[:severity]))
            diabetes[:proliferative_retinopathy] = true
            entity.events.create(time, :proliferative_retinopathy, :nonproliferative_retinopathy, true)
          elsif diabetes[:macular_edema].nil? && (rand < (0.01 * diabetes[:severity]))
            diabetes[:macular_edema] = true
            entity.events.create(time, :macular_edema, :nonproliferative_retinopathy, true)
          elsif diabetes[:blindness].nil? && (rand < (0.01 * diabetes[:severity]))
            diabetes[:blindness] = true
            entity.events.create(time, :blindness, :nonproliferative_retinopathy, true)
          end
        end
      end

      # EYE FAILURE: diabetics have retinopathy (eye failure)
      rule :proliferative_retinopathy, [:nonproliferative_retinopathy], [:macular_edema,:blindness] do |time,entity|
        diabetes = entity[:diabetes]
        if diabetes && diabetes[:proliferative_retinopathy]
          if diabetes[:macular_edema].nil? && (rand < (0.01 * diabetes[:severity]))
            diabetes[:macular_edema] = true
            entity.events.create(time, :macular_edema, :proliferative_retinopathy, true)
          elsif diabetes[:blindness].nil? && (rand < (0.01 * diabetes[:severity]))
            diabetes[:blindness] = true
            entity.events.create(time, :blindness, :proliferative_retinopathy, true)
          end
        end
      end

      # EYE FAILURE: diabetics have retinopathy (eye failure)
      rule :macular_edema, [:nonproliferative_retinopathy,:proliferative_retinopathy], [:blindness] do |time,entity|
        diabetes = entity[:diabetes]
        if diabetes && diabetes[:macular_edema]
          if diabetes[:macular_edema].nil? && (rand < (0.01 * diabetes[:severity]))
            diabetes[:blindness] = true
            entity.events.create(time, :blindness, :macular_edema, true)
          end
        end
      end

      #-----------------------------------------------------------------------#

      # NERVE DAMAGE: diabetics have neuropathy (nerve damage) -> amputations
      rule :neuropathy, [:diabetes], [:amputation] do |time,entity|
        diabetes = entity[:diabetes]
        if diabetes && diabetes[:neuropathy]
          diabetes[:amputation] = [] if diabetes[:amputation].nil? 
          if (rand < (0.01 * diabetes[:severity]))
            body_part = [:left_hand,:left_arm,:left_foot,:left_leg,:right_hand,:right_arm,:right_foot,:right_leg].sample
            unless diabetes[:amputation].include?(body_part)
              diabetes[:amputation] << body_part
              entity.events.create(time, "amputation_#{body_part.to_s}".to_sym, :neuropathy, true)
            end
          end
        end
      end

      #-----------------------------------------------------------------------#

      # rough linear fit seen in Figure 1
      # http://www.microbecolhealthdis.net/index.php/mehd/article/viewFile/22857/34046/125897
      def blood_glucose(bmi)
        ((bmi - 6) / 6.5)
      end

      class Record < BaseRecord
        def self.diagnoses(entity, time)
          patient = entity.record
          if entity[:prediabetes] && !entity.record_conditions[:prediabetes]
            # create the ongoing diagnosis
            entity.record_conditions[:prediabetes] = Condition.new(condition_hash(:prediabetes, time))
            patient.conditions << entity.record_conditions[:prediabetes]
          elsif !entity[:prediabetes] && entity.record_conditions[:prediabetes]
            # end the diagnosis
            entity.record_conditions[:prediabetes].end_time = time.to_i
            entity.record_conditions[:prediabetes] = nil
          end

          if entity[:diabetes] && !entity.record_conditions[:diabetes]
            # create the ongoing diagnosis
            entity.record_conditions[:diabetes] = Condition.new(condition_hash(:diabetes, time))
            patient.conditions << entity.record_conditions[:diabetes]
          elsif !entity[:diabetes] && entity.record_conditions[:diabetes]
            # end the diagnosis
            entity.record_conditions[:diabetes].end_time = time.to_i
            entity.record_conditions[:diabetes] = nil
          end          
        end
      end

    end
  end
end
