module Synthea
  module Modules
    class MetabolicSyndrome < Synthea::Rules

      # People have a BMI that we can roughly use to estimate
      # blood glucose and diabetes
      rule :metabolic_syndrome, [:bmi], [:blood_glucose,:prediabetes,:diabetes,:hypertension,:blood_pressure] do |time, entity|
        return if entity[:age].nil?
        # check for hypertension at adulthood
        if entity[:hypertension].nil? && entity[:age] > 18
          entity[:hypertension] = (rand < Synthea::Config.metabolic.hypertension.probability)
          entity.events.create(time, :hypertension, :metabolic_syndrome, true) if entity[:hypertension] 
        end

        # check for diabetes given BMI
        bmi = entity[:bmi]
        if bmi
          entity[:blood_glucose] = blood_glucose(bmi)
          if(entity[:blood_glucose] < Synthea::Config.metabolic.blood_glucose.normal)
            # normal person
          elsif(entity[:blood_glucose] < Synthea::Config.metabolic.blood_glucose.prediabetic)
            update_prediabetes(time,entity)
          elsif(entity[:blood_glucose] < Synthea::Config.metabolic.blood_glucose.diabetic)
            update_diabetes(1,time,entity)
          elsif(entity[:blood_glucose] < Synthea::Config.metabolic.blood_glucose.severe)
            update_diabetes(2,time,entity)
          else  
            if !entity[:diabetes]
              update_diabetes(3,time,entity) 
            elsif (entity[:diabetes][:severity]>=3 && ((entity[:diabetes][:duration] / 365) >= 1))
              update_diabetes(4,time,entity)
            else
              update_diabetes(3,time,entity)
            end
          end
        end

        # estimate values
        if entity[:hypertension]
          entity[:blood_pressure] = [ pick(Synthea::Config.metabolic.blood_pressure.hypertensive.systolic), pick(Synthea::Config.metabolic.blood_pressure.hypertensive.diastolic)]
        else
          entity[:blood_pressure] = [ pick(Synthea::Config.metabolic.blood_pressure.normal.systolic), pick(Synthea::Config.metabolic.blood_pressure.normal.diastolic)]
        end
        # calculate the components of a lipid panel
        index = 0
        index = 1 if entity[:prediabetes]
        index = entity[:diabetes][:severity] if entity[:diabetes]
        cholesterol = Synthea::Config.metabolic.lipid_panel.cholesterol
        triglycerides = Synthea::Config.metabolic.lipid_panel.triglycerides
        hdl = Synthea::Config.metabolic.lipid_panel.hdl
        entity[:cholesterol] = {
          :total => rand(cholesterol[index]..cholesterol[index+1]),
          :triglycerides => rand(triglycerides[index]..triglycerides[index+1]),
          :hdl => rand(hdl[index+1]..hdl[index])
        }
        entity[:cholesterol][:ldl] = entity[:cholesterol][:total] - entity[:cholesterol][:hdl] - (0.2 * entity[:cholesterol][:triglycerides])
        entity[:cholesterol][:ldl] = entity[:cholesterol][:ldl].to_i

        # calculate the components of a metabolic panel and associated observations
        normal = Synthea::Config.metabolic.basic_panel.normal
        entity[:metabolic] = {
          urea_nitrogen: rand(normal.urea_nitrogen.first..normal.urea_nitrogen.last),
          carbon_dioxide: rand(normal.co2.first..normal.co2.last),
          creatinine: rand(normal.creatinine.first..normal.creatinine.last),
          glucose: rand(normal.glucose.first..normal.glucose.last),
          chloride: rand(normal.chloride.first..normal.chloride.last),
          potassium: rand(normal.potassium.first..normal.potassium.last),
          sodium: rand(normal.sodium.first..normal.sodium.last),
          calcium: rand(normal.calcium.first..normal.calcium.last)
        }
        creatinine_clearance = 100
        if entity[:gender] && entity[:gender]=='M'
          range = Synthea::Config.metabolic.basic_panel.creatinine_clearance.normal.male
          creatinine_clearance = rand(range.first..range.last)
        else
          range = Synthea::Config.metabolic.basic_panel.creatinine_clearance.normal.female
          creatinine_clearance = rand(range.first..range.last)          
        end
        entity[:metabolic][:creatinine_clearance] = creatinine_clearance
        entity[:metabolic][:creatinine] = reverse_calculate_creatine(entity) rescue 1.0
        range = Synthea::Config.metabolic.basic_panel.microalbumin_creatine_ratio.normal
        entity[:metabolic][:microalbumin_creatine_ratio] = rand(range.first..range.last) 
      end

      def update_prediabetes(time,entity)
        prediabetes = entity[:prediabetes]
        if prediabetes.nil?
          prediabetes = {}
          prediabetes[:duration] = 0
          entity[:prediabetes]=prediabetes
          entity.events.create(time, :prediabetes, :metabolic_syndrome, false) if !entity.had_event?(:prediabetes)
        end
        prediabetes[:duration] += Synthea::Config.time_step
      end

      def update_diabetes(severity,time,entity)
        diabetes = entity[:diabetes]
        if diabetes.nil?
          # Add diabetes
          diabetes = {}
          diabetes[:duration] = 0
          entity[:diabetes]=diabetes
          entity.events.create(time, :diabetes, :metabolic_syndrome, false) if !entity.had_event?(:diabetes)
          # check for hypertension at onset of diabetes
          if entity[:hypertension].nil? || entity[:hypertension]==false
            entity[:hypertension] = (rand < Synthea::Config.metabolic.hypertension.probability_given_diabetes)
            entity.events.create(time, :hypertension, :metabolic_syndrome, true) if entity[:hypertension] 
          end
        end
        diabetes[:severity] = severity
        diabetes[:duration] += Synthea::Config.time_step
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
        if diabetes && diabetes[:nephropathy]
          # update the creatinine levels...
          range = Synthea::Config.metabolic.basic_panel.creatinine_clearance.mild_kidney_damage
          entity[:metabolic][:creatinine_clearance] = rand(range.first..range.last)
          entity[:metabolic][:creatinine] = reverse_calculate_creatine(entity) rescue 1.0
          # see if the disease progresses another stage...
          if diabetes[:microalbuminuria].nil? && (rand < (0.01 * diabetes[:severity]))
            diabetes[:microalbuminuria] = true
            entity.events.create(time, :microalbuminuria, :nephropathy, true)
          end
        end
      end

      # KIDNEY FAILURE: microalbhuminuria - a moderate increase in the level of albumin in urine
      rule :microalbuminuria, [:nephropathy], [:proteinuria] do |time,entity|
        diabetes = entity[:diabetes]
        if diabetes && diabetes[:microalbuminuria]
          # update the creatinine levels...
          range = Synthea::Config.metabolic.basic_panel.creatinine_clearance.moderate_kidney_damage
          entity[:metabolic][:creatinine_clearance] = rand(range.first..range.last)
          entity[:metabolic][:creatinine] = reverse_calculate_creatine(entity) rescue 1.0
          # update the microalbumin levels...
          range = Synthea::Config.metabolic.basic_panel.microalbumin_creatine_ratio.microalbuminuria_uncontrolled
          entity[:metabolic][:microalbumin_creatine_ratio] = rand(range.first..range.last) 
          # see if the disease progresses another stage...
          if diabetes[:proteinuria].nil? && (rand < (0.01 * diabetes[:severity]))
            diabetes[:proteinuria] = true
            entity.events.create(time, :proteinuria, :microalbuminuria, true)
          end
        end
      end

      # KIDNEY FAILURE: proteinuria - excess serum proteins in the urine
      rule :proteinuria, [:microalbuminuria], [:end_stage_renal_disease] do |time,entity|
        diabetes = entity[:diabetes]
        if diabetes && diabetes[:proteinuria]
          # update the creatinine levels...
          range = Synthea::Config.metabolic.basic_panel.creatinine_clearance.severe_kidney_damage
          entity[:metabolic][:creatinine_clearance] = rand(range.first..range.last)
          entity[:metabolic][:creatinine] = reverse_calculate_creatine(entity) rescue 1.0
          # update the microalbumin levels...
          range = Synthea::Config.metabolic.basic_panel.microalbumin_creatine_ratio.proteinuria
          entity[:metabolic][:microalbumin_creatine_ratio] = rand(range.first..range.last) 
          # see if the disease progresses another stage...
          if diabetes[:end_stage_renal_disease].nil? && (rand < (0.01 * diabetes[:severity]))
            diabetes[:end_stage_renal_disease] = true 
            entity.events.create(time, :end_stage_renal_disease, :proteinuria, true)
          end
        end
      end

      # KIDNEY FAILURE: End-Stage Renal Disease (ESRD), this is the end...
      # Without intervention, 20-40 percent of patients with type 2 diabetes/microalbuminuria, will evolve to macroalbuminuria.
      # - Shlipak, Michael. "Clinical Evidence Handbook: Diabetic Nephropathy: Preventing Progression - American Family Physician". www.aafp.org.
      rule :end_stage_renal_disease, [:proteinuria], [:kidney_dialysis,:kidney_transplant,:death] do |time,entity|
        diabetes = entity[:diabetes]
        if diabetes && diabetes[:end_stage_renal_disease]
          # update the creatinine levels...
          range = Synthea::Config.metabolic.basic_panel.creatinine_clearance.esrd
          entity[:metabolic][:creatinine_clearance] = rand(range.first..range.last)
          entity[:metabolic][:creatinine] = reverse_calculate_creatine(entity) rescue 1.0
          # see if the disease progresses another stage...
          if (rand < (0.0001 * diabetes[:severity]))
            entity[:is_alive] = false
            entity.events.create(time, :death, :end_stage_renal_disease, true)
            Synthea::Modules::Lifecycle.record_death(entity, time)
          end
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
      # Treatments and Medications
      #-----------------------------------------------------------------------#
      @@med_changes = []

      rule :diet_and_exercise, [:prediabetes,:diabetes], [:monotherapy] do |time,entity|
        if entity[:prediabetes] || entity[:diabetes]
          entity[:careplan] = Hash.new if entity[:careplan].nil?
          # Add diet and exercise to the list of careplans
          entity[:careplan][:diabetes] = [ :diabetic_diet, :exercise ] if entity[:careplan][:diabetes].nil?
        end
      end

      rule :monotherapy, [:diet_and_exercise], [:bitherapy] do |time,entity|
        if entity[:diabetes] && entity[:diabetes][:severity]==1
          entity[:medications] = Hash.new if entity[:medications].nil?
          # stop medications above this stage...
          [:glp1ra,:sglt2i,:basal_insulin,:prandial_insulin].each{|m| stopMedication(m, :diabetes, time, entity, @@med_changes)}
          # prescribe metformin if it isn't already there...
          prescribeMedication(:metformin, :diabetes, time, entity, @@med_changes)
        end
      end

      rule :bitherapy, [:monotherapy], [:tritherapy,:insulin] do |time,entity|
        if entity[:diabetes] && entity[:diabetes][:severity]==2
          entity[:medications] = Hash.new if entity[:medications].nil?
          # delete medications above this stage...
          [:sglt2i,:basal_insulin,:prandial_insulin].each{|m| stopMedication(m, :diabetes, time, entity, @@med_changes)}
          # prescribe metformin and glp1ra if they aren't already there...
          prescribeMedication(:metformin, :diabetes, time, entity, @@med_changes)
          prescribeMedication(:glp1ra, :diabetes, time, entity, @@med_changes)
        end
      end

      rule :tritherapy, [:bitherapy], [:insulin] do |time,entity|
        if entity[:diabetes] && entity[:diabetes][:severity]==3
          entity[:medications] = Hash.new if entity[:medications].nil?
          # delete medications above this stage...
          [:basal_insulin,:prandial_insulin].each{|m| stopMedication(m, :diabetes, time, entity, @@med_changes)}
          # prescribe metformin and cocktail if they aren't already there...
          prescribeMedication(:metformin, :diabetes, time, entity, @@med_changes)
          prescribeMedication(:glp1ra, :diabetes, time, entity, @@med_changes)
          prescribeMedication(:sglt2i, :diabetes, time, entity, @@med_changes)
        end
      end

      rule :insulin, [:tritherapy], [:insulin] do |time,entity|
        if entity[:diabetes] && entity[:diabetes][:severity]==4
          entity[:medications] = Hash.new if entity[:medications].nil?
          # prescribe metformin and cocktail if they aren't already there...
          prescribeMedication(:metformin, :diabetes, time, entity, @@med_changes)
          prescribeMedication(:glp1ra, :diabetes, time, entity, @@med_changes)
          prescribeMedication(:sglt2i, :diabetes, time, entity, @@med_changes)
          # prescribe insulin          
          if entity[:medications][:basal_insulin]
            # if basal insulin was prescribed at the last enounter, escalate to prandial
            basal_added = entity[:medications][:basal_insulin]['time']
            encounters = entity.events.events[:encounter].select do |x|
              (x.time > basal_added) && (x.processed==true)
            end
            if !encounters.empty?
              stopMedication(:basal_insulin, :diabetes, time, entity, @@med_changes)
              prescribeMedication(:prandial_insulin, :diabetes, time, entity, @@med_changes)
            end
          elsif !entity[:medications][:prandial_insulin]
            prescribeMedication(:basal_insulin, :diabetes, time, entity, @@med_changes)
          end     
        end
      end

      #-----------------------------------------------------------------------#

      # rough linear fit seen in Figure 1
      # http://www.microbecolhealthdis.net/index.php/mehd/article/viewFile/22857/34046/125897
      def blood_glucose(bmi)
        ((bmi - 6) / 6.5)
      end

      # http://www.mcw.edu/calculators/creatinine.htm
      def reverse_calculate_creatine(entity)
        age = entity[:age] # years
        female = (entity[:gender]=='F')
        weight = entity[:weight] # kilograms
        crcl = entity[:metabolic][:creatinine_clearance] # mg/dL
        crcl = 100 if crcl.nil?
        crcl = 1 if crcl < 1
        creatine = ((140-age)*weight) / (72*crcl)
        creatine *= 0.85 if female
        creatine
      end

      def self.perform_encounter(entity, time)
        [:prediabetes,:diabetes,:hypertension].each do |diagnosis|
          process_diagnosis(diagnosis,entity,entity,time)
        end

        # record blood pressure
        record_blood_pressure(entity,time) if entity[:blood_pressure]

        if entity[:prediabetes] || entity[:diabetes]
          # process any labs
          record_ha1c(entity,time)
          record_metabolic_panel(entity,time)
        end

        if entity[:diabetes]
          # process any diagnoses
          [:nephropathy,:microalbuminuria,:proteinuria,:end_stage_renal_disease,
            :retinopathy,:nonproliferative_retinopathy,:proliferative_retinopathy,:macular_edema,:blindness,
            :neuropathy,:amputation
          ].each do |diagnosis|
            process_diagnosis(diagnosis,entity[:diabetes],entity,time)
          end

          # process any necessary amputations
          amputations = entity[:diabetes][:amputation]
          process_amputations(amputations, entity, time) if amputations

          # process any labs
          record_lipid_panel(entity,time)
          record_microalbumin_creatinine_ratio(entity,time)
        elsif entity[:age] > 30 && entity.events.since( time-3.years, :lipid_panel ).empty?
          # run a lipid panel for non-diabetics if it has been more than 3 years
          record_lipid_panel(entity,time)
        end

        if entity[:diabetes] || entity[:hypertension]
          record_egfr(entity,time)
        end

        if entity[:careplan] && entity[:careplan][:diabetes]
          # Add a diabetes self-management careplan if one isn't active
          entity[:diabetes] ? reason = :diabetes : reason = :prediabetes
          if !entity.record_synthea.careplan_active?(:diabetes)
            entity.record_synthea.careplan_start(:diabetes, entity[:careplan][:diabetes], time, [reason])
          else 
            entity.record_synthea.update_careplan_reasons(:diabetes, [reason],time)
          end
        elsif entity.record_synthea.careplan_active?(:diabetes)
          # We need to stop the current diabetes careplan
          entity.record_synthea.careplan_stop(:diabetes,time)
        end

        if entity[:medications]
          @@med_changes.each do |med|
            if entity[:medications][med]
              # Add a prescription to the record if it hasn't been recorded yet
              if !entity.record_synthea.medication_active?(med)
                entity.record_synthea.medication_start(med, time, entity[:medications][med]['reasons'])
              end
            elsif entity.record_synthea.medication_active?(med)
              # This prescription can be stopped...
              entity.record_synthea.medication_stop(med, time, :diabetes_well_controlled)
            end
          end
          @@med_changes = []
        end
      end

      def self.process_diagnosis(diagnosis, diagnosis_hash, entity, time)
        if diagnosis_hash[diagnosis] && !entity.record_synthea.present[diagnosis]
          # create the ongoing diagnosis
          entity.record_synthea.condition(diagnosis, time, :condition, :condition)
        elsif !diagnosis_hash[diagnosis] && entity.record_synthea.present[diagnosis]
          # end the diagnosis
          entity.record_synthea.end_condition(diagnosis, time)
        end  
      end

      def self.record_blood_pressure(entity, time)
        patient = entity.record_synthea
        patient.observation(:systolic_blood_pressure, time, entity[:blood_pressure].first, :observation, :vital_sign)
        patient.observation(:diastolic_blood_pressure, time, entity[:blood_pressure].last, :observation, :vital_sign)
        #This dummy 'Observation' indicates the two previous are linked together into one for fhir.
        patient.observation(:blood_pressure, time, 2, :multi_observation, :no_action)
      end

      def self.record_ha1c(entity,time)
        patient = entity.record_synthea
        patient.observation(:ha1c, time, entity[:blood_glucose], :observation, :vital_sign)
      end

      def self.record_metabolic_panel(entity,time)
        patient = entity.record_synthea

        # basic metabolic panel
        patient.observation(:glucose, time, entity[:metabolic][:glucose])
        patient.observation(:urea_nitrogen, time, entity[:metabolic][:urea_nitrogen])
        patient.observation(:creatinine, time, entity[:metabolic][:creatinine])
        patient.observation(:calcium, time, entity[:metabolic][:calcium])
        patient.observation(:sodium, time, entity[:metabolic][:sodium])
        patient.observation(:potassium, time, entity[:metabolic][:potassium])
        patient.observation(:chloride, time, entity[:metabolic][:chloride])
        patient.observation(:carbon_dioxide, time, entity[:metabolic][:carbon_dioxide])        
        patient.diagnostic_report(:basic_metabolic_panel, time, 8)
      end

      def self.record_microalbumin_creatinine_ratio(entity,time)
        patient = entity.record_synthea
        patient.observation(:microalbumin_creatine_ratio, time, entity[:metabolic][:microalbumin_creatine_ratio])
      end

      def self.record_egfr(entity,time)
        patient = entity.record_synthea
        patient.observation(:egfr, time, entity[:metabolic][:creatinine_clearance])
      end

      def self.process_amputations(amputations, entity, time)
        amputations.each do |amputation|
          amp_str = amputation.to_s
          key = "amputation_#{amp_str}".to_sym
          reason_code = '368581000119106'
          if !entity.record_synthea.present[key]
            entity.record_synthea.procedure(key, time, reason_code, :procedure, :procedure)
          end

          body_part = amp_str.split('_')[1]

          cond_key =  case body_part
                      when 'leg' 
                        :history_of_lower_limb_amputation
                      when 'foot'
                        :history_of_amputation_of_foot
                      when 'hand'
                        :history_of_disarticulation_at_wrist
                      when 'arm'
                        :history_of_upper_limb_amputation
                      end
          
          if !entity.record_synthea.present[cond_key]
            entity.record_synthea.condition(cond_key, time)
          end
        end
      end

      def self.record_lipid_panel(entity, time)
        return if entity[:cholesterol].nil?
        
        entity.events.create(time, :lipid_panel, :encounter, true)
        patient = entity.record_synthea

        patient.observation(:cholesterol, time, entity[:cholesterol][:total], :observation, :vital_sign)
        patient.observation(:triglycerides, time, entity[:cholesterol][:triglycerides], :observation, :vital_sign)
        patient.observation(:hdl, time, entity[:cholesterol][:hdl], :observation, :vital_sign)
        patient.observation(:ldl, time, entity[:cholesterol][:ldl], :observation, :vital_sign)
        patient.diagnostic_report(:lipid_panel, time, 4, :diagnostic_report, :no_action)
      end
    end
  end
end
