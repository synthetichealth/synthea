module Synthea
	module Modules
    	class CardiovascularDisease < Synthea::Rules

    		m_age_lookup = {'20-34' => -9, '35-39' => -4, '40-44' => 0, '45-49' => 3, '50-54' => 6,
    			'55-59' => 8, '60-64' => 10, '65-69' => 11, '70-74' => 12, '75-79' => 13
    		}

    		f_age_lookup = {'20-34' => -7, '35-39' => -3, '40-44' => 0, '45-49' => 3, '50-54' => 6,
    			'55-59' => 8, '60-64' => 10, '65-69' => 12, '70-74' => 14, '75-79' => 16
    		}

    		m_age_chol_lookup = {
    			'20-39' => {'<160' => 0, '160-199' => 4, '200-239' => 7, '240-279' => 9, '>280' => 11},
    			'40-49' => {'<160' => 0, '160-199' => 3, '200-239' => 5, '240-279' => 6, '>280' => 8},
    			'50-59' => {'<160' => 0, '160-199' => 2, '200-239' => 3, '240-279' => 4, '>280' => 5},
    			'60-69' => {'<160' => 0, '160-199' => 1, '200-239' => 1, '240-279' => 2, '>280' => 3},
    			'70-79' => {'<160' => 0, '160-199' => 0, '200-239' => 0, '240-279' => 1, '>280' => 1}
    		}

    		f_age_chol_lookup = {
    			'20-39' => {'<160' => 0, '160-199' => 4, '200-239' => 8, '240-279' => 11, '>280' => 13},
    			'40-49' => {'<160' => 0, '160-199' => 3, '200-239' => 6, '240-279' => 8, '>280' => 10},
    			'50-59' => {'<160' => 0, '160-199' => 2, '200-239' => 4, '240-279' => 5, '>280' => 7},
    			'60-69' => {'<160' => 0, '160-199' => 1, '200-239' => 2, '240-279' => 3, '>280' => 4},
    			'70-79' => {'<160' => 0, '160-199' => 1, '200-239' => 1, '240-279' => 2, '>280' => 2}
    		}

    		m_age_smoke = {
    			'20-39' => 8,
    			'40-49' => 5,
    			'50-59' => 3,
    			'60-69' => 1,
    			'70-79' => 1
    		}

    		f_age_smoke = {
    			'20-39' => 9,
    			'40-49' => 7,
    			'50-59' => 4,
    			'60-69' => 2,
    			'70-79' => 1
    		}

    		hdl_lookup = {
    			'>60' => -1,
    			'50-59' => 0,
    			'40-49' => 1,
    			'<40' => 2
    		}

    		m_sys_bp = {
    			'<120' => {true => 0, false => 0},
    			'120-129' => {true => 1, false => 0},
    			'130-139' => {true => 2, false => 1},
    			'140-159' => {true => 2, false => 1},
    			'>160' => {true => 3, false => 2}
    		}

    		f_sys_bp = {
    			'<120' => {true => 0, false => 0},
    			'120-129' => {true => 3, false => 1},
    			'130-139' => {true => 4, false => 2},
    			'140-159' => {true => 5, false => 3},
    			'>160' => {true => 6, false => 4}
    		}

    		#framingham point scores gives a 10-year risk. Divide by 365*10 to estimate daily risk
    		m_daily_risk_chd = {
    			'<0' => 0.00000137,
    			0 => 0.00000274,
    			1 => 0.00000274,
    			2 => 0.00000274,
    			3 => 0.00000274,
    			4 => 0.00000274,
    			5 => 0.00000548,
    			6 => 0.00000548,
    			7 => 0.00000822,
    			8 => 0.0000110,
    			9 => 0.0000137,
    			10 => 0.0000164,
    			11 => 0.0000219,
    			12 => 0.0000274,
    			13 => 0.0000329,
    			14 => 0.0000438,
    			15 => 0.0000548,
    			16 => 0.0000685,
    			'>17' => 0.0000822
    		}

    		f_daily_risk_chd = {
    			'<9' => 0.00000137,
    			9 => 0.00000274,
    			10 => 0.00000274,
    			11 => 0.00000274,
    			12 => 0.00000274,
    			13 => 0.00000548,
    			14 => 0.00000548,
    			15 => 0.00000822,
    			16 => 0.0000110,
    			17 => 0.0000137,
    			18 => 0.0000164,
    			19 => 0.0000219,
    			20 => 0.0000301,
    			21 => 0.0000384,
    			22 => 0.0000466,
    			23 => 0.0000603,
    			24 => 0.0000740,
    			'>25' => 0.00008219
    		}
    		#estimate cardiovascular risk of developing coronary heart disease (CHD)
    		#http://www.nhlbi.nih.gov/health-pro/guidelines/current/cholesterol-guidelines/quick-desk-reference-html/10-year-risk-framingham-table#men
    		rule :calculate_cardio_risk, [:cholesterol, :HDL, :age, :gender, :blood_pressure], [:cardio_risk] do |time, entity|
    			
    			if entity[:age].nil? || entity[:blood_pressure].nil? || entity[:gender].nil? || entity[:cholesterol].nil?
    				return
    			end	
				age = entity[:age]
    			cholesterol = entity[:cholesterol][:total]
    			hdl_level = entity[:cholesterol][:hdl]
    			blood_pressure = entity[:blood_pressure][0]

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
    			framingham_points += hdl_lookup[hdl_range]
    			if entity[:gender] == 'M'
    				framingham_points += m_age_lookup[short_age_range]
    				framingham_points += m_age_chol_lookup[long_age_range][chol_range]
    				if entity[:smoke]
    					framingham_points += m_age_smoke[long_age_range]
    				end
    				framingham_points += m_sys_bp[bp_range][false]
    				if framingham_points < 0
    					framingham_points = '<0'
    				elsif framingham_points >= 17
    					framingham_points = '>17'
    				end
    				#cardio risk per time step
    				entity[:cardio_risk] = m_daily_risk_chd[framingham_points] * Synthea::Config.time_step*10
	    		else
	    			framingham_points += f_age_lookup[short_age_range]
	    			framingham_points += f_age_chol_lookup[long_age_range][chol_range]
	    			if entity[:smoke]
    					framingham_points += f_age_smoke[long_age_range]
    				end
    				framingham_points += f_sys_bp[bp_range][false]
    				if framingham_points < 9
    					framingham_points = '<9'
    				elsif framingham_points >= 25
    					framingham_points = '>25'
    				end
    				entity[:cardio_risk] = f_daily_risk_chd[framingham_points] * Synthea::Config.time_step*10
	    		end
    		end

    		rule :coronary_heart_disease?, [:cardio_risk], [:coronary_heart_disease] do |time, entity|
    			if !entity[:cardio_risk].nil? && entity[:coronary_heart_disease].nil? && rand < entity[:cardio_risk]
    				entity[:coronary_heart_disease] = true 
    				entity.events.create(time, :coronary_heart_disease, :coronary_heart_disease?, true)
    			end
    		end 

            #numbers are from appendix: http://www.ncbi.nlm.nih.gov/pmc/articles/PMC1647098/pdf/amjph00262-0029.pdf
    		rule :coronary_heart_disease, [:coronary_heart_disease], [:cardiac_event, :death] do |time, entity|
    			if entity[:gender] && entity[:gender] == 'M'
    				cardiac_event_chance = 0.000115
    			else
    				cardiac_event_chance = 0.00004110
    			end

		        if entity[:coronary_heart_disease] && rand < cardiac_event_chance
		          entity.events.create(time, :cardiac_event, :coronary_heart_disease, true)
		        	if rand < 0.894
						entity[:is_alive] = false
						entity.events.create(time, :death, :coronary_heart_disease, true)
						Synthea::Modules::Lifecycle::Record.death(entity, time)
					end
		        end
    		end

            #chance of getting a heart attack without heart disease. NUMBERS ARE NOT ACCURATE.
            rule :no_coronary_heart_disease, [:coronary_heart_disease], [:cardiac_event, :death] do |time, entity|
                cardiac_event_chance = 0.0000001
                if entity[:coronary_heart_disease].nil? && rand < cardiac_event_chance
                  entity.events.create(time, :cardiac_event, :no_coronary_heart_disease, true)
                    if rand < 0.894
                        entity[:is_alive] = false
                        entity.events.create(time, :death, :no_coronary_heart_disease, true)
                        Synthea::Modules::Lifecycle::Record.death(entity, time)
                    end
                end
            end

            #-----------------------------------------------------------------------#
            rule :calculate_stroke_risk, [:age, :blood_pressure, :stroke_history], [:stroke_risk] do |time, entity|
                # need to find source for calculating stroke risk.
                entity[:stroke_risk] = 0.0001
            end

            #Strokes are fatal 10-20 percent of cases https://stroke.nih.gov/materials/strokechallenges.htm
            rule :get_stroke, [:stroke_history], [:stroke, :death] do |time, entity|
                if entity[:stroke_risk] && rand < entity[:stroke_risk]
                    entity.events.create(time, :stroke, :get_stroke, true)
                    entity[:stroke_history] = true
                    if rand < 0.15
                        entity[:is_alive] = false
                        entity.events.create(time, :death, :get_stroke, true)
                        Synthea::Modules::Lifecycle::Record.death(entity, time)
                    end
                end
            end
    	end
	end
end
