module Synthea
  module Modules
    class MetabolicSyndrome < Synthea::Rules

      # People have a BMI that we can roughly use to estimate
      # blood glucose and diabetes
      rule :metabolic_syndrome, [:bmi], [:blood_glucose,:prediabetes,:diabetes,:hypertension,:blood_pressure] do |time, entity|
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
            update_diabetes(3,time,entity)
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
      # Without intervention, 20-40 percent of patients with type 2 diabetes/microalbuminuria, will evolve to macroalbuminuria.
      # - Shlipak, Michael. "Clinical Evidence Handbook: Diabetic Nephropathy: Preventing Progression - American Family Physician". www.aafp.org.
      rule :end_stage_renal_disease, [:proteinuria], [:kidney_dialysis,:kidney_transplant,:death] do |time,entity|
        diabetes = entity[:diabetes]
        if diabetes && diabetes[:end_stage_renal_disease] && (rand < (0.0001 * diabetes[:severity]))
          entity[:is_alive] = false
          entity.events.create(time, :death, :end_stage_renal_disease, true)
          Synthea::Modules::Lifecycle::Record.death(entity, time)
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
        def self.perform_encounter(entity, time)
          [:prediabetes,:diabetes,:hypertension].each do |diagnosis|
            process_diagnosis(diagnosis,entity,entity,time)
          end

          # record blood pressure
          record_blood_pressure(entity,time) if entity[:blood_pressure]

          if entity[:prediabetes] || entity[:diabetes]
            # process any labs
            record_h1ac(entity,time)
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
          elsif entity[:age] > 30 && entity.events(:lipid_panel).since( time-3.years ).empty?
            # run a lipid panel for non-diabetics if it has been more than 3 years
            record_lipid_panel(entity,time)
          end
        end

        def self.record_blood_pressure(entity, time)
          patient = entity.record
          patient.vital_signs << VitalSign.new(lab_hash(:systolic_blood_pressure, time, entity[:blood_pressure].first))
          patient.vital_signs << VitalSign.new(lab_hash(:diastolic_blood_pressure, time, entity[:blood_pressure].last))

          #last encounter inserted into fhir_record entry is assumed to correspond with what's being recorded
          encounter = entity.fhir_record.entry.reverse.find {|e| e.resource.is_a?(FHIR::Encounter)}
          patient = entity.fhir_record.entry.find {|e| e.resource.is_a?(FHIR::Patient)}

          observation = FHIR::Observation.new({
            'status'=>'final',
            'code'=>{
              'coding'=>[{'system'=>'http://loinc.org','code'=>'55284-4','display'=>'Blood Pressure'}]
            },
            'subject'=> { 'reference'=> "Patient/#{patient.fullUrl}"},
            'encounter'=> { 'reference'=> "Encounter/#{encounter.fullUrl}"},
            'effectiveDateTime' => convertFhirDateTime(time,'time'),
            'component'=>[
              {
                'code'=>{'coding'=>[{'system'=>'http://loinc.org','code'=>'8480-6','display'=>'Systolic blood pressure'}]},
                'valueQuantity'=>{'value'=>entity[:blood_pressure].first,'unit'=>'mmHg'}
              },{
                'code'=>{'coding'=>[{'system'=>'http://loinc.org','code'=>'8462-4','display'=>'Diastolic blood pressure'}]},
                'valueQuantity'=>{'value'=>entity[:blood_pressure].last,'unit'=>'mmHg'}
              }
            ]
          })
          entry = FHIR::Bundle::Entry.new
          entry.resource = observation
          entity.fhir_record.entry << entry
        end

        def self.record_h1ac(entity,time)
          patient = entity.record
          patient.vital_signs << VitalSign.new(lab_hash(:ha1c, time, entity[:blood_glucose]))

          #last encounter inserted into fhir_record entry is assumed to correspond with what's being recorded
          encounter = entity.fhir_record.entry.reverse.find {|e| e.resource.is_a?(FHIR::Encounter)}
          patient = entity.fhir_record.entry.find {|e| e.resource.is_a?(FHIR::Patient)}

          entity.fhir_record.entry << create_basic_obs('4548-4','Hemoglobin A1c/Hemoglobin.total in Blood',patient,encounter,time,entity[:blood_glucose],'%')
        end

        def self.create_basic_obs(code,description,patientEntry,encounterEntry,time,value,unit)
          entry = FHIR::Bundle::Entry.new
          entry.fullUrl = SecureRandom.uuid
          entry.resource = FHIR::Observation.new({
            'status'=>'final',
            'code'=>{
              'coding'=>[{'system'=>'http://loinc.org','code'=>code,'display'=>description}]
            },
            'subject'=> { 'reference'=> "Patient/#{patientEntry.fullUrl}"},
            'encounter'=> { 'reference'=> "Encounter/#{encounterEntry.fullUrl}"},
            'effectiveDateTime' => convertFhirDateTime(time,'time'),
            'valueQuantity'=>{'value'=>value,'unit'=>unit}
          })
          entry
        end

        def self.create_basic_diagnostic_report(code,description,patientEntry,encounterEntry,time,obsEntries)
          entry = FHIR::Bundle::Entry.new
          entry.fullUrl = SecureRandom.uuid
          entry.resource = FHIR::DiagnosticReport.new({
            'status'=>'final',
            'code'=>{
              'coding'=>[{'system'=>'http://loinc.org','code'=>code,'display'=>description}]
            },
            'subject'=> { 'reference'=> "Patient/#{patientEntry.fullUrl}"},
            'encounter'=> { 'reference'=> "Encounter/#{encounterEntry.fullUrl}"},
            'effectiveDateTime' => convertFhirDateTime(time,'time'),
            'issued' => convertFhirDateTime(time,'time'),
            'performer' => { 'display' => 'Hospital Lab'}
          })
          entry.resource.result = []
          obsEntries.each do |e|
            entry.resource.result << FHIR::Reference.new({'reference'=>"Observation/#{e.fullUrl}",'display'=>e.resource.code.coding.first.display})
          end
          entry
        end

        def self.record_lipid_panel(entity, time)
          return if entity[:cholesterol].nil?
          
          entity.events.create(time, :lipid_panel, :encounter, true)
          # cholesterol: { description: 'Total Cholesterol', code: '2093-3', unit: 'mg/dL'},
          # triglycerides: { description: 'Triglycerides', code: '2571-8', unit: 'mg/dL'},
          # hdl: { description: 'High Density Lipoprotein Cholesterol', code: '2085-9', unit: 'mg/dL'},
          # ldl: { description: 'Low Density Lipoprotein Cholesterol', code: '18262-6', unit: 'mg/dL'}          
          entity.record.vital_signs << VitalSign.new(lab_hash(:cholesterol, time, entity[:cholesterol][:total]))
          entity.record.vital_signs << VitalSign.new(lab_hash(:triglycerides, time, entity[:cholesterol][:triglycerides]))
          entity.record.vital_signs << VitalSign.new(lab_hash(:hdl, time, entity[:cholesterol][:hdl]))
          entity.record.vital_signs << VitalSign.new(lab_hash(:ldl, time, entity[:cholesterol][:ldl]))

          #last encounter inserted into fhir_record entry is assumed to correspond with what's being recorded
          encounter = entity.fhir_record.entry.reverse.find {|e| e.resource.is_a?(FHIR::Encounter)}
          patient = entity.fhir_record.entry.find {|e| e.resource.is_a?(FHIR::Patient)}

          obs1 = create_basic_obs('2093-3','Total Cholesterol',patient,encounter,time,entity[:cholesterol][:total],'mg/dL')
          obs2 = create_basic_obs('2571-8','Triglycerides',patient,encounter,time,entity[:cholesterol][:triglycerides],'mg/dL')
          obs3 = create_basic_obs('2085-9','High Density Lipoprotein Cholesterol',patient,encounter,time,entity[:cholesterol][:hdl],'mg/dL')
          obs4 = create_basic_obs('18262-6','Low Density Lipoprotein Cholesterol',patient,encounter,time,entity[:cholesterol][:ldl],'mg/dL')
          report = create_basic_diagnostic_report('57698-3','Lipid Panel',patient,encounter,time,[obs1,obs2,obs3,obs4])
          
          entity.fhir_record.entry << obs1
          entity.fhir_record.entry << obs2
          entity.fhir_record.entry << obs3
          entity.fhir_record.entry << obs4
          entity.fhir_record.entry << report
        end

        def self.process_amputations(amputations, entity, time)
          amputations.each do |amputation|
            key = "amputation_#{amputation.to_s}".to_sym
            description = "Amputation of #{amputation.to_s.gsub('_',' ')}."
            if !entity.record_conditions[key]
              # Add amputation procedure to HDS
              entity.record_conditions[key] = Procedure.new({
                "codes" => {'SNOMED-CT' => ['81723002']},
                "description" => description,
                "start_time" => time.to_i,
                "end_time" => time.to_i + 15.minutes,
              })
              entity.record.procedures << entity.record_conditions[key]

              # Add amputation procedure to FHIR record
              patient = entity.fhir_record.entry.find{|e| e.resource.is_a?(FHIR::Patient)}
              encounter = entity.fhir_record.entry.reverse.find {|e| e.resource.is_a?(FHIR::Encounter)}
              reason = entity.fhir_record.entry.find{|e| e.resource.is_a?(FHIR::Condition) && e.resource.code.coding.find{|c|c.code=='368581000119106'} }

              procedure = FHIR::Procedure.new({
                'subject' => { 'reference' => patient.resource.id },
                'status' => 'completed',
                'code' => { 
                  'coding' => [{'code'=>'81723002', 'display'=>description, 'system'=>'http://snomed.info/sct'}],
                  'text' => description },
                # 'reasonReference' => { 'reference' => reason.resource.id },
                # 'performer' => { 'reference' => doctor_no_good },
                'performedDateTime' => convertFhirDateTime(time,'time'),
                'encounter' => { 'reference' => encounter.resource.id },
              })
              procedure.reasonReference = FHIR::Reference.new({'reference'=>reason.resource.id,'display'=>reason.resource.code.text}) if reason

              entry = FHIR::Bundle::Entry.new
              entry.resource = procedure
              entity.fhir_record.entry << entry
            end
          end
        end

        def self.process_diagnosis(diagnosis, diagnosis_hash, entity, time)
          if diagnosis_hash[diagnosis] && !entity.record_conditions[diagnosis]
            # create the ongoing diagnosis
            entity.record_conditions[diagnosis] = Condition.new(condition_hash(diagnosis, time))
            entity.record.conditions << entity.record_conditions[diagnosis]

            #write to fhir record
            patient = entity.fhir_record.entry.find{|e| e.resource.is_a?(FHIR::Patient)}
            encounter = entity.fhir_record.entry.reverse.find {|e| e.resource.is_a?(FHIR::Encounter)}
            conditionData = condition_hash(diagnosis, time)
            condition = FHIR::Condition.new({
              'id' => SecureRandom.uuid,
              'patient' => {'reference'=>"Patient/#{patient.fullUrl}"},
              'code' => {'coding'=>[{
                'code'=>conditionData['codes']['SNOMED-CT'][0],
                'display'=>conditionData['description'], 
                'system' => 'http://snomed.info/sct'}],
                'text'=>conditionData['description']
                },
              'verificationStatus' => 'confirmed',
              'onsetDateTime' => convertFhirDateTime(time,'time'),
              'encounter' => {'reference'=>"Encounter/#{encounter.fullUrl}"}
            })

            entry = FHIR::Bundle::Entry.new
            entry.resource = condition
            entity.fhir_record.entry << entry

          elsif !diagnosis_hash[diagnosis] && entity.record_conditions[diagnosis]
            # end the diagnosis
            entity.record_conditions[diagnosis].end_time = time.to_i
            entity.record_conditions[diagnosis] = nil

            condition = entity.fhir_record.entry.find{|e| e.resource.is_a?(FHIR::Condition) && e.resource.code.coding[0].display == condition_hash(diagnosis,time)['description']}
            condition.resource.abatementDateTime = convertFhirDateTime(time,'time')
          end  
        end
      end

    end
  end
end
