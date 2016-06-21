module Synthea
	module Modules
    	class CardiovascularDisease < Synthea::Rules

            #estimate cardiovascular risk of developing coronary heart disease (CHD)
            #http://www.nhlbi.nih.gov/health-pro/guidelines/current/cholesterol-guidelines/quick-desk-reference-html/10-year-risk-framingham-table#men
    		m_age_chd = {'20-34' => -9, '35-39' => -4, '40-44' => 0, '45-49' => 3, '50-54' => 6,
    			'55-59' => 8, '60-64' => 10, '65-69' => 11, '70-74' => 12, '75-79' => 13
    		}

    		f_age_chd = {'20-34' => -7, '35-39' => -3, '40-44' => 0, '45-49' => 3, '50-54' => 6,
    			'55-59' => 8, '60-64' => 10, '65-69' => 12, '70-74' => 14, '75-79' => 16
    		}

    		m_age_chol_chd = {
    			'20-39' => {'<160' => 0, '160-199' => 4, '200-239' => 7, '240-279' => 9, '>280' => 11},
    			'40-49' => {'<160' => 0, '160-199' => 3, '200-239' => 5, '240-279' => 6, '>280' => 8},
    			'50-59' => {'<160' => 0, '160-199' => 2, '200-239' => 3, '240-279' => 4, '>280' => 5},
    			'60-69' => {'<160' => 0, '160-199' => 1, '200-239' => 1, '240-279' => 2, '>280' => 3},
    			'70-79' => {'<160' => 0, '160-199' => 0, '200-239' => 0, '240-279' => 1, '>280' => 1}
    		}

    		f_age_chol_chd = {
    			'20-39' => {'<160' => 0, '160-199' => 4, '200-239' => 8, '240-279' => 11, '>280' => 13},
    			'40-49' => {'<160' => 0, '160-199' => 3, '200-239' => 6, '240-279' => 8, '>280' => 10},
    			'50-59' => {'<160' => 0, '160-199' => 2, '200-239' => 4, '240-279' => 5, '>280' => 7},
    			'60-69' => {'<160' => 0, '160-199' => 1, '200-239' => 2, '240-279' => 3, '>280' => 4},
    			'70-79' => {'<160' => 0, '160-199' => 1, '200-239' => 1, '240-279' => 2, '>280' => 2}
    		}

    		m_age_smoke_chd = {
    			'20-39' => 8,
    			'40-49' => 5,
    			'50-59' => 3,
    			'60-69' => 1,
    			'70-79' => 1
    		}

    		f_age_smoke_chd = {
    			'20-39' => 9,
    			'40-49' => 7,
    			'50-59' => 4,
    			'60-69' => 2,
    			'70-79' => 1
    		}

    		hdl_lookup_chd = {
    			'>60' => -1,
    			'50-59' => 0,
    			'40-49' => 1,
    			'<40' => 2
    		}

            #true/false refers to whether or not blood pressure is treated
    		m_sys_bp_chd = {
    			'<120' => {true => 0, false => 0},
    			'120-129' => {true => 1, false => 0},
    			'130-139' => {true => 2, false => 1},
    			'140-159' => {true => 2, false => 1},
    			'>160' => {true => 3, false => 2}
    		}

    		f_sys_bp_chd = {
    			'<120' => {true => 0, false => 0},
    			'120-129' => {true => 3, false => 1},
    			'130-139' => {true => 4, false => 2},
    			'140-159' => {true => 5, false => 3},
    			'>160' => {true => 6, false => 4}
    		}

    		#framingham point scores gives a 10-year risk
    		m_risk_chd = {
    			'<0' => 0.005,
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
    			'>17' => 0.3
    		}

    		f_risk_chd = {
    			'<9' => 0.005,
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
    			'>25' => 0.3
    		}

            # 9/10 smokers start before age 18. We will use 16.
            #http://www.cdc.gov/tobacco/data_statistics/fact_sheets/youth_data/tobacco_use/
            rule :start_smoking, [:age], [:smoke] do |time, entity|
                if entity[:smoke].nil? && entity[:age] == 16
                    rand < Synthea::Config.cardiovascular.smoke ? entity[:smoke] = true : entity[:smoke] = false
                end
            end

    		
            rule :calculate_cardio_risk, [:cholesterol, :HDL, :age, :gender, :blood_pressure, :smoke], [:coronary_heart_disease?] do |time, entity|
    			
    			if entity[:age].nil? || entity[:blood_pressure].nil? || entity[:gender].nil? || entity[:cholesterol].nil?
    				return
    			end	
				age = entity[:age]
    			cholesterol = entity[:cholesterol][:total]
    			hdl_level = entity[:cholesterol][:hdl]
    			blood_pressure = entity[:blood_pressure][0]
                bp_treated = entity[:bp_treated?] || false
    			#assign age bracket
    			if age < 40
    				long_age_range = '20-39'
    				age < 35 ? short_age_range = '20-34' : short_age_range = '35-39'
    			elsif age < 50
    				long_age_range = '40-49'
    				age < 45 ? short_age_range = '40-44' : short_age_range = '45-49'
    			elsif age < 60
    				long_age_range = '50-59' 
    				age < 55 ? short_age_range = '50-54' : short_age_range = '55-59'
    			elsif age < 70
    				long_age_range = '60-69'
    				age < 65 ? short_age_range = '60-64' : short_age_range = '65-69'
    			else 
    				long_age_range = '70-79'
    				age < 75 ? short_age_range = '70-74' : short_age_range = '75-79'
    			end

    			#assign cholesterol range
    			if cholesterol < 160
    				chol_range = '<160'
    			elsif cholesterol < 200
    				chol_range = '160-199'
    			elsif cholesterol < 240
    				chol_range = '200-239'
    			elsif cholesterol < 280
    				chol_range = '240-279'
    			else
    				chol_range = '>280'
				end

				#assign HDL range
				if hdl_level < 40
					hdl_range = '<40'
				elsif hdl_level < 50
					hdl_range = '40-49'
				elsif hdl_level < 60
					hdl_range = '50-59'
				else
					hdl_range = '>60'
				end

				if blood_pressure < 120
					bp_range = '<120'
				elsif blood_pressure < 130
					bp_range = '120-129'
				elsif blood_pressure < 140
					bp_range = '130-139'
				elsif blood_pressure < 160
					bp_range = '140-159'
				else
					bp_range = '>160'
				end

    			framingham_points = 0
    			framingham_points += hdl_lookup_chd[hdl_range]
    			if entity[:gender] == 'M'
    				framingham_points += m_age_chd[short_age_range]
    				framingham_points += m_age_chol_chd[long_age_range][chol_range]
    				if entity[:smoke]
    					framingham_points += m_age_smoke_chd[long_age_range]
    				end
                    #the second variable refers to treated or untreated blood pressure
    				framingham_points += m_sys_bp_chd[bp_range][bp_treated]
    				if framingham_points < 0
    					framingham_points = '<0'
    				elsif framingham_points >= 17
    					framingham_points = '>17'
    				end

                    risk = m_risk_chd[framingham_points]
    				#cardio risk per time step
	    		else
	    			framingham_points += f_age_chd[short_age_range]
	    			framingham_points += f_age_chol_chd[long_age_range][chol_range]
	    			if entity[:smoke]
    					framingham_points += f_age_smoke_chd[long_age_range]
    				end
    				framingham_points += f_sys_bp_chd[bp_range][bp_treated]
    				if framingham_points < 9
    					framingham_points = '<9'
    				elsif framingham_points >= 25
    					framingham_points = '>25'
    				end
                    risk = f_risk_chd[framingham_points]
	    		end
                entity[:cardio_risk] = Synthea::Rules.convert_risk_to_timestep(risk,3650)
    		end

    		rule :coronary_heart_disease?, [:calculate_cardio_risk], [:coronary_heart_disease] do |time, entity|
    			if !entity[:cardio_risk].nil? && entity[:coronary_heart_disease].nil? && rand < entity[:cardio_risk]
    				entity[:coronary_heart_disease] = true 
    				entity.events.create(time, :coronary_heart_disease, :coronary_heart_disease?, true)
    			end
    		end 

            #numbers are from appendix: http://www.ncbi.nlm.nih.gov/pmc/articles/PMC1647098/pdf/amjph00262-0029.pdf
    		rule :coronary_heart_disease, [:coronary_heart_disease?], [:myocardial_infarction, :cardiac_arrest, :encounter, :death] do |time, entity|
    			if entity[:gender] && entity[:gender] == 'M'
                    index = 0
    			else
                    index = 1
    			end
                annual_risk = Synthea::Config.cardiovascular.chd.coronary_attack_risk[index]
                cardiac_event_chance = Synthea::Rules.convert_risk_to_timestep(annual_risk,365)
		        if entity[:coronary_heart_disease] && rand < cardiac_event_chance
                    if rand < Synthea::Config.cardiovascular.chd.mi_proportion
                        entity.events.create(time, :myocardial_infarction, :coronary_heart_disease)
                    else
                        entity.events.create(time, :cardiac_arrest, :coronary_heart_disease)
                    end
                    #creates unprocessed emergency encounter. Will be processed at next time step.
                    entity.events.create(time, :emergency_encounter, :coronary_heart_disease)
                    Synthea::Modules::Encounters.emergency_visit(time, entity)
                    survival_rate = Synthea::Config.cardiovascular.chd.survive
                    #survival rate triples if a bystander is present
                    survival_rate *= 3 if rand < Synthea::Config.cardiovascular.chd.bystander
    	        	if rand > survival_rate
    					entity[:is_alive] = false
    					entity.events.create(time, :death, :coronary_heart_disease, true)
    					Synthea::Modules::Lifecycle::Record.death(entity, time)
    				end
		        end
    		end

            #chance of getting a sudden cardiac arrest without heart disease. (Most probable cardiac event w/o cause or history)
            rule :no_coronary_heart_disease, [:coronary_heart_disease?], [:cardiac_arrest, :death] do |time, entity|
                annual_risk = Synthea::Config.cardiovascular.sudden_cardiac_arrest.risk
                cardiac_event_chance = Synthea::Rules.convert_risk_to_timestep(annual_risk,365)
                if entity[:coronary_heart_disease].nil? && rand < cardiac_event_chance
                    entity.events.create(time, :cardiac_arrest, :no_coronary_heart_disease)
                    entity.events.create(time, :emergency_encounter, :no_coronary_heart_disease)
                    Synthea::Modules::Encounters.emergency_visit(time, entity)
                    survival_rate = 1 - Synthea::Config.cardiovascular.sudden_cardiac_arrest.death
                    survival_rate *= 3 if rand < Synthea::Config.cardiovascular.chd.bystander
                    annual_death_risk = 1 - survival_rate
                    if rand < Synthea::Rules.convert_risk_to_timestep(annual_death_risk,365)
                        entity[:is_alive] = false
                        entity.events.create(time, :death, :no_coronary_heart_disease, true)
                        Synthea::Modules::Lifecycle::Record.death(entity, time)
                    end
                end
            end

            #-----------------------------------------------------------------------#
            #Framingham score system for calculating atrial fibrillation (significant factor for stroke risk)

            age_af = { #age ranges: 45-49, 50-54, 55-59, 60-64, 65-69, 70-74, 75-79, 80-84, >84 
                'M' => [1, 2, 3, 4, 5, 6, 7, 7, 8],
                'F' => [-3, -2, 0, 1, 3, 4, 6, 7, 8]
            }
            # only covers points 1-9. <=0 and >= 10 are in if statement
            risk_af_table = {
                0 => 0.01, #0 or less
                1 => 0.02, 2 => 0.02, 3 => 0.03,
                4 => 0.04, 5 => 0.06, 6 => 0.08,
                7 => 0.12, 8 => 0.16, 9 => 0.22,
                10 => 0.3 #10 or greater
            }

            rule :calculate_atrial_fibrillation_risk, [:age, :bmi, :blood_pressure, :gender], [:atrial_fibrillation_risk] do |time, entity|
                if entity[:atrial_fibrillation] || entity[:age].nil? || entity[:blood_pressure].nil? || entity[:gender].nil? || entity[:bmi].nil? || entity[:age] < 45
                    return
                end 
                age = entity[:age]
                af_score = 0
                age_range = [(age-45)/5,8].min
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

            #Framingham score system for calculating risk of stroke
            #https://www.framinghamheartstudy.org/risk-functions/stroke/stroke.php

            #The index for each range corresponds to the number of points
            m_age_stroke = [(54..56), (57..59), (60..62), (63..65), (66..68), (69..72),
                (73..75), (76..78), (79..81), (82..84), (85..999)]

            f_age_stroke = [(54..56), (57..59), (60..62), (63..64), (65..67), (68..70),
                (71..73), (74..76), (77..78), (79..81), (82..999)]

            m_untreated_sys_bp_stroke = [(0..105), (106..115), (116..125), (126..135), (136..145), (146..155),
                (156..165), (166..175), (176..185), (185..195), (196..205)]

            f_untreated_sys_bp_stroke = [(0..95), (95..106), (107..118), (119..130), (131..143), (144..155),
                (156..167), (168..180), (181..192), (193..204), (205..216)]

            m_treated_sys_bp_stroke = [(0..105), (106..112), (113..117), (118..123), (124..129), (130..135),
                (136..142), (143..150), (151..161), (162..176), (177..205)]

            f_treated_sys_bp_stroke = [(0..95), (95..106), (107..113), (114..119), (120..125), (126..131),
                (132..139), (140..148), (149..160), (161..204), (205..216)]

            m_10_year_stroke_risk = {
                0 => 0, 1 => 0.03, 2 => 0.03, 3 => 0.04, 4 => 0.04, 5 => 0.05, 6 => 0.05, 7 => 0.06, 8 => 0.07, 9 =>0.08, 10 => 0.1,
                11 => 0.11, 12 => 0.13, 13 => 0.15, 14 => 0.17, 15 => 0.2, 16 => 0.22, 17 => 0.26, 18 => 0.29, 19 => 0.33, 20 => 0.37,
                21 => 0.42, 22 => 0.47, 23 => 0.52, 24 => 0.57, 25 => 0.63, 26 => 0.68, 27 => 0.74, 28 => 0.79, 29 => 0.84, 30 => 0.88
            }

            f_10_year_stroke_risk = {
                0 => 0, 1 => 0.01, 2 => 0.01, 3 => 0.02, 4 => 0.02, 5 => 0.02, 6 => 0.03, 7 => 0.04, 8 => 0.04, 9 =>0.05, 10 => 0.06,
                11 => 0.08, 12 => 0.09, 13 => 0.11, 14 => 0.13, 15 => 0.16, 16 => 0.19, 17 => 0.23, 18 => 0.27, 19 => 0.32, 20 => 0.37,
                21 => 0.43, 22 => 0.5, 23 => 0.57, 24 => 0.64, 25 => 0.71, 26 => 0.78, 27 => 0.84
            }

            rule :calculate_stroke_risk, [:age, :diabetes, :coronary_heart_disease, :blood_pressure, :stroke_history, :smoke], [:stroke_risk] do |time, entity|
                if entity[:age].nil? || entity[:blood_pressure].nil? || entity[:gender].nil? 
                    return
                end 
                age = entity[:age]
                gender = entity[:gender]
                blood_pressure = entity[:blood_pressure][0]
                #https://www.heart.org/idc/groups/heart-public/@wcm/@sop/@smd/documents/downloadable/ucm_449858.pdf
                #calculate stroke risk based off of prevalence of stroke in age group for people younger than 54. Framingham score system does not cover these.
                if gender == 'M'
                    index = 0
                else
                    index = 1
                end
                if age < 20
                    return
                elsif age < 40 && age >= 20
                    rate = Synthea::Config.cardiovascular.stroke.rate_20_39[index]
                elsif age < 55 && age >=40
                    rate = Synthea::Config.cardiovascular.stroke.rate_40_59[index]
                end

                if rate
                    entity[:stroke_risk] = Synthea::Rules.convert_risk_to_timestep(rate, 3650) 
                    return
                end

                stroke_points = 0
                stroke_points += 3 if entity[:smoke]
                stroke_points += 5 if entity[:left_ventricular_hypertrophy]
                if gender == 'M'
                    stroke_points += m_age_stroke.find_index{|range| range.include?(age)}
                    if entity[:bp_treated?] #treating blood pressure currently is not a feature. Modify this for when it is.
                        stroke_points += m_treated_sys_bp_stroke.find_index{|range| range.include?(blood_pressure)}
                    else 
                        stroke_points += m_untreated_sys_bp_stroke.find_index{|range| range.include?(blood_pressure)}
                    end
                    stroke_points += 2 if entity[:diabetes]
                    stroke_points += 4 if entity[:coronary_heart_disease]

                    #these two diseases have not been implemented yet.
                    stroke_points += 4 if entity[:atrial_fibrillation]
                    ten_stroke_risk = m_10_year_stroke_risk[stroke_points]
                else
                    stroke_points += f_age_stroke.find_index{|range| range.include?(age)}
                    if entity[:bp_treated?] #treating blood pressure currently is not a feature. Modify this for when it is.
                        stroke_points += f_treated_sys_bp_stroke.find_index{|range| range.include?(blood_pressure)}
                    else 
                        stroke_points += f_untreated_sys_bp_stroke.find_index{|range| range.include?(blood_pressure)}
                    end
                    stroke_points += 3 if entity[:diabetes]
                    stroke_points += 2 if entity[:coronary_heart_disease]

                    #these two diseases have not been implemented yet.
                    stroke_points += 6 if entity[:atrial_fibrillation]
                    ten_stroke_risk = f_10_year_stroke_risk[stroke_points]
                end
                entity[:stroke_risk] = Synthea::Rules.convert_risk_to_timestep(ten_stroke_risk, 3650)
                entity[:stroke_points] = stroke_points
            end

            #Strokes are fatal 10-20 percent of cases https://stroke.nih.gov/materials/strokechallenges.htm
            rule :get_stroke, [:stroke_risk, :stroke_history], [:stroke, :death, :stroke_history] do |time, entity|
                if entity[:stroke_risk] && rand < entity[:stroke_risk]
                    entity.events.create(time, :stroke, :get_stroke)
                    entity[:stroke_history] = true
                    entity.events.create(time + 10.minutes, :emergency_encounter, :get_stroke)
                    Synthea::Modules::Encounters.emergency_visit(time + 15.minutes, entity)
                    if rand < Synthea::Config.cardiovascular.stroke.death
                        entity[:is_alive] = false
                        entity.events.create(time, :death, :get_stroke, true)
                        Synthea::Modules::Lifecycle::Record.death(entity, time)
                    end
                end
            end

            #-----------------------------------------------------------------------#

            class Record < BaseRecord
                def self.perform_encounter(entity, time)
                    [:coronary_heart_disease].each do |diagnosis|
                        if entity[diagnosis] && !entity.record_conditions[diagnosis]
                            entity.record_conditions[diagnosis] = Condition.new(condition_hash(diagnosis, time))
                            entity.record.conditions << entity.record_conditions[diagnosis]
                        
                            condition = FHIR::Condition.new
                            condition.id = SecureRandom.uuid
                            patient = entity.fhir_record.entry.find{|e| e.resource.is_a?(FHIR::Patient)}
                            condition.patient = FHIR::Reference.new({'reference'=>'Patient/' + patient.fullUrl})
                            conditionData = condition_hash(diagnosis, time)
                            conditionCoding = FHIR::Coding.new({'code'=>conditionData['codes']['SNOMED-CT'][0], 'display'=>conditionData['description'], 'system' => 'http://snomed.info/sct'})
                            condition.code = FHIR::CodeableConcept.new({'coding'=>[conditionCoding],'text'=>conditionData['description']})
                            condition.verificationStatus = 'confirmed'
                            condition.onsetDateTime = convertFhirDateTime(time,'time')

                            encounter = entity.fhir_record.entry.reverse.find {|e| e.resource.is_a?(FHIR::Encounter)}
                            condition.encounter = FHIR::Reference.new({'reference'=>'Encounter/' + encounter.fullUrl})

                            entry = FHIR::Bundle::Entry.new
                            entry.resource = condition
                            entity.fhir_record.entry << entry
                        end
                    end
                end

                def self.perform_emergency(entity, event)
                    time = event.time
                    [:myocardial_infarction, :stroke, :cardiac_arrest].each do |diagnosis|
                        if diagnosis == event.type && !entity.record_conditions[diagnosis]
                            entity.record_conditions[diagnosis] = Condition.new(condition_hash(diagnosis, time))
                            entity.record.conditions << entity.record_conditions[diagnosis]
                        
                            condition = FHIR::Condition.new
                            condition.id = SecureRandom.uuid
                            patient = entity.fhir_record.entry.find{|e| e.resource.is_a?(FHIR::Patient)}
                            condition.patient = FHIR::Reference.new({'reference'=>'Patient/' + patient.fullUrl})
                            conditionData = condition_hash(diagnosis, time)
                            conditionCoding = FHIR::Coding.new({'code'=>conditionData['codes']['SNOMED-CT'][0], 'display'=>conditionData['description'], 'system' => 'http://snomed.info/sct'})
                            condition.code = FHIR::CodeableConcept.new({'coding'=>[conditionCoding],'text'=>conditionData['description']})
                            condition.verificationStatus = 'confirmed'
                            condition.onsetDateTime = convertFhirDateTime(time,'time')

                            encounter = entity.fhir_record.entry.reverse.find {|e| e.resource.is_a?(FHIR::Encounter)}
                            condition.encounter = FHIR::Reference.new({'reference'=>'Encounter/' + encounter.fullUrl})

                            entry = FHIR::Bundle::Entry.new
                            entry.resource = condition
                            entity.fhir_record.entry << entry
                        end
                    end
                    #record treatments for coronary attack?
                end
                
            end
    	end
	end
end