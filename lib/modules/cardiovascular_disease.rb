module Synthea
  module Modules
    class CardiovascularDisease < Synthea::Rules
      # estimate cardiovascular risk of developing coronary heart disease (CHD)
      # http://www.nhlbi.nih.gov/health-pro/guidelines/current/cholesterol-guidelines/quick-desk-reference-html/10-year-risk-framingham-table

      # Indices in the array correspond to these age ranges: 20-24, 25-29, 30-34 35-39, 40-44, 45-49,
      # 50-54, 55-59, 60-64, 65-69, 70-74, 75-79
      age_chd = {
        'M' => [-9, -9, -9, -4, 0, 3, 6, 8, 10, 11, 12, 13],
        'F' => [-7, -7, -7, -3, 0, 3, 6, 8, 10, 12, 14, 16]
      }

      age_chol_chd = {
        'M' => [
          # <160, 160-199, 200-239, 240-279, >280
          [0, 4, 7, 9, 11], # 20-29 years
          [0, 4, 7, 9, 11], # 30-39 years
          [0, 3, 5, 6, 8], # 40-49 years
          [0, 2, 3, 4, 5], # 50-59 years
          [0, 1, 1, 2, 3], # 60-69 years
          [0, 0, 0, 1, 1] # 70-79 years

        ],
        'F' => [
          # <160, 160-199, 200-239, 240-279, >280
          [0, 4, 8, 11, 13], # 20-29 years
          [0, 4, 8, 11, 13], # 30-39 years
          [0, 3, 6, 8, 10], # 40-49 years
          [0, 2, 4, 5, 7], # 50-59 years
          [0, 1, 2, 3, 4], # 60-69 years
          [0, 1, 1, 2, 2] # 70-79 years
        ]
      }
      age_smoke_chd = {
        # 20-29, 30-39, 40-49, 50-59, 60-69, 70-79 age ranges
        'M' => [8, 8, 5, 3, 1, 1],
        'F' => [9, 9, 7, 4, 2, 1]
      }

      hdl_lookup_chd = [2, 1, 0, -1] # <40, 40-49, 50-59, >60

      # true/false refers to whether or not blood pressure is treated
      sys_bp_chd = {
        'M' => [
          { true => 0, false => 0 }, # <120
          { true => 1, false => 0 }, # 120-129
          { true => 2, false => 1 }, # 130-139
          { true => 2, false => 1 }, # 140-149
          { true => 2, false => 1 }, # 150-159
          { true => 3, false => 2 } # >=160
        ],
        'F' => [
          { true => 0, false => 0 }, # <120
          { true => 3, false => 1 }, # 120-129
          { true => 4, false => 2 }, # 130-139
          { true => 5, false => 3 }, # 140-149
          { true => 5, false => 3 }, # 150-159
          { true => 6, false => 4 } # >=160
        ]
      }

      # framingham point scores gives a 10-year risk
      risk_chd = {
        'M' => {
          -1 => 0.005, # '-1' represents all scores <0
          0 => 0.01,
          1 => 0.01,
          2 => 0.01,
          3 => 0.01,
          4 => 0.01,
          5 => 0.02,
          6 => 0.02,
          7 => 0.03,
          8 => 0.04,
          9 => 0.05,
          10 => 0.06,
          11 => 0.08,
          12 => 0.1,
          13 => 0.12,
          14 => 0.16,
          15 => 0.20,
          16 => 0.25,
          17 => 0.3 # '17' represents all scores >16
        },
        'F' => {
          8 => 0.005, # '8' represents all scores <9
          9 => 0.01,
          10 => 0.01,
          11 => 0.01,
          12 => 0.01,
          13 => 0.02,
          14 => 0.02,
          15 => 0.03,
          16 => 0.04,
          17 => 0.05,
          18 => 0.06,
          19 => 0.08,
          20 => 0.11,
          21 => 0.14,
          22 => 0.17,
          23 => 0.22,
          24 => 0.27,
          25 => 0.3 # '25' represents all scores >24
        }
      }

      # 9/10 smokers start before age 18. We will use 16.
      # http://www.cdc.gov/tobacco/data_statistics/fact_sheets/youth_data/tobacco_use/
      rule :start_smoking, [:age], [:smoker] do |time, entity|
        if entity[:smoker].nil? && entity[:age] == 16
          entity[:smoker] = rand < likelihood_of_smoker(time) ? true : false
        end
      end

      def likelihood_of_smoker(time)
        # 16.1% of MA are smokers in 2016. http://www.cdc.gov/tobacco/data_statistics/state_data/state_highlights/2010/states/massachusetts/
        # but the rate is decreasing over time
        # http://www.cdc.gov/tobacco/data_statistics/tables/trends/cig_smoking/
        # selected #s:
        # 1965 - 42.4%
        # 1975 - 37.1%
        # 1985 - 30.1%
        # 1995 - 24.7%
        # 2005 - 20.9%
        # 2015 - 16.1%
        # assume that it was never significantly higher than 42% pre-1960s, but will continue to drop slowly after 2016
        # it's decreasing about .5% per year
        return 0.424 if time.year < 1965

        ((time.year * -0.4865) + 996.41) / 100.0
      end

      rule :calculate_cardio_risk, [:cholesterol, :HDL, :age, :gender, :blood_pressure, :smoker], [:coronary_heart_disease?] do |_time, entity|
        return if entity[:age].nil? || entity[:blood_pressure].nil? || entity[:gender].nil? || entity[:cholesterol].nil?
        age = entity[:age]
        gender = entity[:gender]
        bp_treated = entity[:bp_treated?] || false
        # calculate which index in a lookup array a number corresponds to based on ranges in scoring
        short_age_range = [[(age - 20) / 5, 0].max, 11].min
        long_age_range = [[(age - 20) / 10, 0].max, 5].min
        chol_range = [[(age - 160) / 40 + 1, 0].max, 4].min
        hdl_range = [[(age - 40) / 10 + 1, 0].max, 3].min
        bp_range = [[(age - 120) / 10 + 1, 0].max, 5].min
        framingham_points = 0
        framingham_points += age_chd[gender][short_age_range]
        framingham_points += age_chol_chd[gender][long_age_range][chol_range]
        if entity[:smoker]
          framingham_points += age_smoke_chd[gender][long_age_range]
        end

        framingham_points += hdl_lookup_chd[hdl_range]
        framingham_points += sys_bp_chd[gender][bp_range][bp_treated]
        # restrict lower and upper bound of framingham score
        gender_bounds = { 'M' => { 'low' => 0, 'high' => 17 }, 'F' => { 'low' => 8, 'high' => 25 } }
        framingham_points = [[framingham_points, gender_bounds[gender]['low']].max, gender_bounds[gender]['high']].min

        risk = risk_chd[gender][framingham_points]
        entity[:cardio_risk] = Synthea::Rules.convert_risk_to_timestep(risk, 3650)
      end

      rule :coronary_heart_disease?, [:calculate_cardio_risk], [:coronary_heart_disease] do |time, entity|
        if !entity[:cardio_risk].nil? && entity[:coronary_heart_disease].nil? && rand < entity[:cardio_risk]
          entity[:coronary_heart_disease] = true
          entity.events.create(time, :coronary_heart_disease, :coronary_heart_disease?, true)
        end
      end

      # numbers are from appendix: http://www.ncbi.nlm.nih.gov/pmc/articles/PMC1647098/pdf/amjph00262-0029.pdf
      rule :coronary_heart_disease, [:coronary_heart_disease?], [:myocardial_infarction, :cardiac_arrest, :encounter, :death] do |time, entity|
        index = if entity[:gender] && entity[:gender] == 'M'
                  0
                else
                  1
                end
        annual_risk = Synthea::Config.cardiovascular.chd.coronary_attack_risk[index]
        cardiac_event_chance = Synthea::Rules.convert_risk_to_timestep(annual_risk, 365)
        if entity[:coronary_heart_disease] && rand < cardiac_event_chance
          cardiac_event = if rand < Synthea::Config.cardiovascular.chd.mi_proportion
                            :myocardial_infarction
                          else
                            :cardiac_arrest
                          end
          entity.events.create(time, cardiac_event, :coronary_heart_disease)
          # creates unprocessed emergency encounter. Will be processed at next time step.
          entity.events.create(time, :emergency_encounter, :coronary_heart_disease)
          Synthea::Modules::Encounters.emergency_visit(time, entity)
          survival_rate = Synthea::Config.cardiovascular.chd.survive
          # survival rate triples if a bystander is present
          survival_rate *= 3 if rand < Synthea::Config.cardiovascular.chd.bystander
          if rand > survival_rate
            entity.events.create(time, :death, :coronary_heart_disease, true)
            Synthea::Modules::Lifecycle.record_death(entity, time, cardiac_event)
          end
        end
      end

      # chance of getting a sudden cardiac arrest without heart disease. (Most probable cardiac event w/o cause or history)
      rule :no_coronary_heart_disease, [:coronary_heart_disease?], [:cardiac_arrest, :death] do |time, entity|
        annual_risk = Synthea::Config.cardiovascular.sudden_cardiac_arrest.risk
        cardiac_event_chance = Synthea::Rules.convert_risk_to_timestep(annual_risk, 365)
        if entity[:coronary_heart_disease].nil? && rand < cardiac_event_chance
          entity.events.create(time, :cardiac_arrest, :no_coronary_heart_disease)
          entity.events.create(time, :emergency_encounter, :no_coronary_heart_disease)
          Synthea::Modules::Encounters.emergency_visit(time, entity)
          survival_rate = 1 - Synthea::Config.cardiovascular.sudden_cardiac_arrest.death
          survival_rate *= 3 if rand < Synthea::Config.cardiovascular.chd.bystander
          annual_death_risk = 1 - survival_rate
          if rand < Synthea::Rules.convert_risk_to_timestep(annual_death_risk, 365)
            entity.events.create(time, :death, :no_coronary_heart_disease, true)
            Synthea::Modules::Lifecycle.record_death(entity, time, :cardiac_arrest)
          end
        end
      end
      #-----------------------------------------------------------------------#
      # Framingham score system for calculating atrial fibrillation (significant factor for stroke risk)

      age_af = { # age ranges: 45-49, 50-54, 55-59, 60-64, 65-69, 70-74, 75-79, 80-84, >84
        'M' => [1, 2, 3, 4, 5, 6, 7, 7, 8],
        'F' => [-3, -2, 0, 1, 3, 4, 6, 7, 8]
      }
      # only covers points 1-9. <=0 and >= 10 are in if statement
      risk_af_table = {
        0 => 0.01, # 0 or less
        1 => 0.02, 2 => 0.02, 3 => 0.03,
        4 => 0.04, 5 => 0.06, 6 => 0.08,
        7 => 0.12, 8 => 0.16, 9 => 0.22,
        10 => 0.3 # 10 or greater
      }

      rule :calculate_atrial_fibrillation_risk, [:age, :bmi, :blood_pressure, :gender], [:atrial_fibrillation_risk] do |_time, entity|
        if entity[:atrial_fibrillation] || entity[:age].nil? || entity[:blood_pressure].nil? || entity[:gender].nil? || entity[:bmi].nil? || entity[:age] < 45
          return
        end
        age = entity[:age]
        af_score = 0
        age_range = [(age - 45) / 5, 8].min
        af_score += age_af[entity[:gender]][age_range]
        af_score += 1 if entity[:bmi] >= 30
        af_score += 1 if entity[:blood_pressure][0] >= 160
        af_score += 1 if entity[:bp_treated?]
        af_score = [[af_score, 0].max, 10].min

        af_risk = risk_af_table[af_score]
        entity[:atrial_fibrillation_risk] = Synthea::Rules.convert_risk_to_timestep(af_risk, 3650)
      end

      rule :get_atrial_fibrillation, [:atrial_fibrillation_risk], [:atrial_fibrillation] do |time, entity|
        if entity[:atrial_fibrillation].nil? && entity[:atrial_fibrillation_risk] && rand < entity[:atrial_fibrillation_risk]
          entity.events.create(time, :atrial_fibrillation, :get_atrial_fibrillation)
          entity[:atrial_fibrillation] = true
        end
      end

      #-----------------------------------------------------------------------#

      # Framingham score system for calculating risk of stroke
      # https://www.framinghamheartstudy.org/risk-functions/stroke/stroke.php

      # The index for each range corresponds to the number of points

      # data for men is first array, women in second.
      age_stroke = [
        [(54..56), (57..59), (60..62), (63..65), (66..68), (69..72),
         (73..75), (76..78), (79..81), (82..84), (85..999)],

        [(54..56), (57..59), (60..62), (63..64), (65..67), (68..70),
         (71..73), (74..76), (77..78), (79..81), (82..999)]
      ]

      untreated_sys_bp_stroke = [
        [(0..105), (106..115), (116..125), (126..135), (136..145), (146..155),
         (156..165), (166..175), (176..185), (185..195), (196..205)],

        [(0..95), (95..106), (107..118), (119..130), (131..143), (144..155),
         (156..167), (168..180), (181..192), (193..204), (205..216)]
      ]

      treated_sys_bp_stroke = [
        [(0..105), (106..112), (113..117), (118..123), (124..129), (130..135),
         (136..142), (143..150), (151..161), (162..176), (177..205)],

        [(0..95), (95..106), (107..113), (114..119), (120..125), (126..131),
         (132..139), (140..148), (149..160), (161..204), (205..216)]
      ]

      ten_year_stroke_risk = {
        'M' => {
          0 => 0, 1 => 0.03, 2 => 0.03, 3 => 0.04, 4 => 0.04, 5 => 0.05, 6 => 0.05, 7 => 0.06, 8 => 0.07, 9 => 0.08, 10 => 0.1,
          11 => 0.11, 12 => 0.13, 13 => 0.15, 14 => 0.17, 15 => 0.2, 16 => 0.22, 17 => 0.26, 18 => 0.29, 19 => 0.33, 20 => 0.37,
          21 => 0.42, 22 => 0.47, 23 => 0.52, 24 => 0.57, 25 => 0.63, 26 => 0.68, 27 => 0.74, 28 => 0.79, 29 => 0.84, 30 => 0.88
        },

        'F' => {
          0 => 0, 1 => 0.01, 2 => 0.01, 3 => 0.02, 4 => 0.02, 5 => 0.02, 6 => 0.03, 7 => 0.04, 8 => 0.04, 9 => 0.05, 10 => 0.06,
          11 => 0.08, 12 => 0.09, 13 => 0.11, 14 => 0.13, 15 => 0.16, 16 => 0.19, 17 => 0.23, 18 => 0.27, 19 => 0.32, 20 => 0.37,
          21 => 0.43, 22 => 0.5, 23 => 0.57, 24 => 0.64, 25 => 0.71, 26 => 0.78, 27 => 0.84
        }
      }

      diabetes_stroke = { 'M' => 2, 'F' => 3 }
      chd_stroke_points = { 'M' => 4, 'F' => 2 }
      atrial_fibrillation_stroke_points = { 'M' => 4, 'F' => 6 }

      rule :calculate_stroke_risk, [:age, :diabetes, :coronary_heart_disease, :blood_pressure, :stroke_history, :smoker], [:stroke_risk] do |_time, entity|
        return if entity[:age].nil? || entity[:blood_pressure].nil? || entity[:gender].nil?
        age = entity[:age]
        gender = entity[:gender]
        blood_pressure = entity[:blood_pressure][0]
        # https://www.heart.org/idc/groups/heart-public/@wcm/@sop/@smd/documents/downloadable/ucm_449858.pdf
        # calculate stroke risk based off of prevalence of stroke in age group for people younger than 54. Framingham score system does not cover these.

        gender_index = if gender == 'M'
                         0
                       else
                         1
                       end

        if age < 20
          return
        elsif age < 40 && age >= 20
          rate = Synthea::Config.cardiovascular.stroke.rate_20_39[gender_index]
        elsif age < 55 && age >= 40
          rate = Synthea::Config.cardiovascular.stroke.rate_40_59[gender_index]
        end

        if rate
          entity[:stroke_risk] = Synthea::Rules.convert_risk_to_timestep(rate, 3650)
          return
        end

        stroke_points = 0
        stroke_points += 3 if entity[:smoker]
        stroke_points += 5 if entity[:left_ventricular_hypertrophy]
        stroke_points += age_stroke[gender_index].find_index { |range| range.include?(age) }
        stroke_points += if entity[:bp_treated?] # treating blood pressure currently is not a feature. Modify this for when it is.
                           treated_sys_bp_stroke[gender_index].find_index { |range| range.include?(blood_pressure) }
                         else
                           untreated_sys_bp_stroke[gender_index].find_index { |range| range.include?(blood_pressure) }
                         end
        stroke_points += diabetes_stroke[gender] if entity[:diabetes]
        stroke_points += chd_stroke_points[gender] if entity[:coronary_heart_disease]
        stroke_points += atrial_fibrillation_stroke_points[gender] if entity[:atrial_fibrillation]
        ten_stroke_risk = ten_year_stroke_risk[gender][stroke_points]
        if ten_stroke_risk.nil?
          worst_case = ten_year_stroke_risk[gender].keys.last
          ten_stroke_risk = ten_year_stroke_risk[gender][worst_case]
        end

        # divide 10 year risk by 365 * 10 to get daily risk.
        entity[:stroke_risk] = Synthea::Rules.convert_risk_to_timestep(ten_stroke_risk, 3650)
        entity[:stroke_points] = stroke_points
      end

      rule :get_stroke, [:stroke_risk, :stroke_history], [:stroke, :death, :stroke_history] do |time, entity|
        if entity[:stroke_risk] && rand < entity[:stroke_risk]
          entity.events.create(time, :stroke, :get_stroke)
          entity[:stroke_history] = true
          entity.events.create(time + 10.minutes, :emergency_encounter, :get_stroke)
          Synthea::Modules::Encounters.emergency_visit(time + 15.minutes, entity)
          if rand < Synthea::Config.cardiovascular.stroke.death
            entity.events.create(time, :death, :get_stroke, true)
            Synthea::Modules::Lifecycle.record_death(entity, time, :stroke)
          end
        end
      end
      #-----------------------------------------------------------------------#
      # Treatments and Medications
      #-----------------------------------------------------------------------#

      rule :heart_healthy_lifestyle, [:coronary_heart_disease, :stroke, :cardiac_arrest, :myocardial_infarction], [] do |_time, entity|
        reasons = []
        [:coronary_heart_disease, :stroke, :cardiac_arrest, :myocardial_infarction].each do |disease|
          reasons << disease if entity[disease] || entity.had_event?(disease)
        end
        unless reasons.empty?
          entity[:careplan] ||= {}
          entity[:careplan][:cardiovascular_disease] ||= { 'activities' => [:exercise, :stress_management, :stop_smoking, :healthy_diet] }
          entity[:careplan][:cardiovascular_disease]['reasons'] = reasons
        end
      end

      rule :chd_treatment, [:coronary_heart_disease], [:coronary_heart_disease] do |time, entity|
        meds = [:clopidogrel, :simvastatin, :amlodipine, :nitroglycerin]
        if entity[:coronary_heart_disease]
          entity[:medications] ||= {}
          meds.each { |m| prescribe_medication(m, :coronary_heart_disease, time, entity, entity[:med_changes][:cardiovascular_disease]) }
        elsif entity[:medications]
          meds.each do |m|
            stop_medication(m, :coronary_heart_disease, time, entity, entity[:med_changes][:cardiovascular_disease]) if entity[:medications][m]
          end
        end
      end

      rule :atrial_fibrillation_treatment, [:atrial_fibrillation], [:atrial_fibrillation] do |time, entity|
        meds = [:warfarin, :verapamil, :digoxin]
        if entity[:atrial_fibrillation]
          entity[:medications] ||= {}
          meds.each { |m| prescribe_medication(m, :atrial_fibrillation, time, entity, entity[:med_changes][:cardiovascular_disease]) }

          # catheter ablation is a more extreme measure than electrical cardioversion and is usually only performed
          # when medication and other procedures are not preferred or have failed. As a rough simulation of this,
          # we arbitrarily chose a 20% chance of getting catheter ablation and 80% of getting cardioversion
          afib_procedure = rand < 0.2 ? :catheter_ablation : :electrical_cardioversion
          entity[:cardiovascular_procedures] ||= {}
          entity[:cardiovascular_procedures][:atrial_fibrillation] ||= [afib_procedure]
        elsif entity[:medications]
          meds.each do |m|
            stop_medication(m, :atrial_fibrillation, time, entity, entity[:med_changes][:cardiovascular_disease]) if entity[:medications][m]
          end
        end
      end
      #-----------------------------------------------------------------------#

      def self.perform_encounter(entity, time)
        patient = entity.record_synthea
        [:coronary_heart_disease, :atrial_fibrillation].each do |diagnosis|
          if entity[diagnosis] && !entity.record_synthea.present[diagnosis]
            patient.condition(diagnosis, time, :condition, :condition)
          end
        end

        if entity[:careplan] && entity[:careplan][:cardiovascular_disease]
          if !entity.record_synthea.careplan_active?(:cardiovascular_disease)
            entity.record_synthea.careplan_start(:cardiovascular_disease, entity[:careplan][:cardiovascular_disease]['activities'], time, entity[:careplan][:cardiovascular_disease]['reasons'])
          else
            entity.record_synthea.update_careplan_reasons(:cardiovascular_disease, entity[:careplan][:cardiovascular_disease]['reasons'], time)
          end
        elsif entity.record_synthea.careplan_active?(:cardiovascular_disease)
          entity.record_synthea.careplan_stop(:cardiovascular_disease, time)
        end

        if entity[:medications]
          entity[:med_changes][:cardiovascular_disease].each do |med|
            if entity[:medications][med]
              # Add a prescription to the record if it hasn't been recorded yet
              if !entity.record_synthea.medication_active?(med)
                entity.record_synthea.medication_start(med, time, entity[:medications][med]['reasons'])
              else
                entity.record_synthea.update_med_reasons(med, entity[:medications][med]['reasons'], time)
              end
            elsif entity.record_synthea.medication_active?(med)
              # This prescription can be stopped...
              entity.record_synthea.medication_stop(med, time, :cardiovascular_improved)
            end
          end
          entity[:med_changes][:cardiovascular_disease] = []
        end

        if entity[:cardiovascular_procedures]
          entity[:cardiovascular_procedures].each do |reason, procedures|
            procedures.each do |proc|
              unless entity.record_synthea.present[proc]
                # TODO: assumes a procedure will only be performed once, might need to be revisited
                entity.record_synthea.procedure(proc, time, reason, :procedure, :procedure)
              end
            end
          end
        end
      end

      def self.perform_emergency(entity, event)
        emergency_meds = {
          myocardial_infarction: [:nitroglycerin, :atorvastatin, :captopril, :clopidogrel],
          stroke: [:clopidogrel, :alteplase],
          cardiac_arrest: [:epinephrine, :amiodarone, :atropine]
        }
        emergency_procedures = {
          myocardial_infarction: [:percutaneous_coronary_intervention, :coronary_artery_bypass_grafting],
          stroke: [:mechanical_thrombectomy],
          cardiac_arrest: [:implant_cardioverter_defib, :catheter_ablation]
        }
        history_conditions = {
          myocardial_infarction: [:history_of_myocardial_infarction],
          stroke: [],
          cardiac_arrest: [:history_of_cardiac_arrest]
        }
        time = event.time
        diagnosis = event.type
        patient = entity.record_synthea
        if [:myocardial_infarction, :stroke, :cardiac_arrest].include?(diagnosis)
          patient.condition(diagnosis, time, :condition, :condition)
          emergency_meds[diagnosis].each do |med|
            entity.record_synthea.medication_start(med, time, [diagnosis])
            entity.record_synthea.medication_stop(med, time + 15.minutes, :stop_drug)
          end

          emergency_procedures[diagnosis].each do |proc|
            entity.record_synthea.procedure(proc, time, diagnosis, :procedure, :procedure)
          end

          history_conditions[diagnosis].each do |cond|
            entity.record_synthea.condition(cond, time)
          end
        end
      end
    end
  end
end
