module Synthea
  module Modules
    class Lifecycle < Synthea::Rules
      attr_accessor :races, :ethnicity, :blood_types

      def initialize
        super
        @growth_chart = JSON.parse(File.read('./resources/cdc_growth_charts.json'))
      end

      # People are born
      rule :birth, [], [:age] do |time, entity|
        unless entity.had_event?(:birth)
          entity[:age] = 0
          entity[:name_first] = Faker::Name.first_name
          entity[:name_last] = Faker::Name.last_name
          if Synthea::Config.population.append_hash_to_person_names == true
            entity[:name_first] = "#{entity[:name_first]}#{(entity[:name_first].hash % 999)}"
            entity[:name_last] = "#{entity[:name_last]}#{(entity[:name_last].hash % 999)}"
          end
          entity[:gender] ||= gender
          entity[:race] ||= Synthea::World::Demographics::RACES.pick
          entity[:ethnicity] ||= Synthea::World::Demographics::ETHNICITY[entity[:race]].pick
          entity[:blood_type] = Synthea::World::Demographics::BLOOD_TYPES[entity[:race]].pick
          entity[:sexual_orientation] = Synthea::World::Demographics::SEXUAL_ORIENTATION.pick.to_s
          entity[:fingerprint] = Synthea::Fingerprint.generate if Synthea::Config.population.generate_fingerprints

          # growth chart data goes covers the 3rd-97th percentile
          #  and we don't have data to extrapolate that last few %

          # https://www2.census.gov/library/publications/2010/compendia/statab/130ed/tables/11s0205.pdf
          # height distribution is basically a normal distribution
          # mean for males is 5'9, SD is 2.9 inches; for females it's 5'4 and 2.8 inches
          # that SD corresponds to the difference between the 50th percentile and the 90th percentile
          distro = Synthea::Utils::Distribution.normal(50, 30)
          hgt_pct = distro.call
          entity[:height_percentile] = [[3, hgt_pct].max, 97].min # bound the percentile within 3-97
          # weight distribution is less normal but still close enough that this should work for now
          wgt_pct = distro.call
          entity[:weight_percentile] = [[3, wgt_pct].max, 97].min

          height = growth_chart('height', entity[:gender], entity[:age], entity[:height_percentile])
          weight = growth_chart('weight', entity[:gender], entity[:age], entity[:weight_percentile])
          entity.set_vital_sign(:height, height, 'cm')
          entity.set_vital_sign(:weight, weight, 'kg')
          entity[:multiple_birth] = rand(3) + 1 if rand < Synthea::Config.lifecycle.prevalence_of_twins
          entity.events.create(time, :birth, :birth, true)
          entity.events.create(time, :encounter, :birth)
          entity.events.create(time, :symptoms_cause_encounter, :birth)

          # determine lat/long coordinates of address within MA
          location_data = Synthea::Location.select_point(entity[:city])
          entity[:coordinates_address] = location_data['point']
          zip_code = Synthea::Location.get_zipcode(location_data['city'])
          entity[:address] = {
            'line' => [Faker::Address.street_address],
            'city' => location_data['city'],
            'state' => 'MA',
            'postalCode' => zip_code
          }
          entity[:address]['line'] << Faker::Address.secondary_address if rand < 0.5
          entity[:city] = location_data['city']

          # telephone
          entity[:telephone] = Faker::PhoneNumber.phone_number

          # birthplace
          entity[:birth_place] = {
            'city' => Synthea::Location.select_point['city'],
            'state' => 'MA'
          }

          # parents
          mothers_name = Faker::Name.first_name
          mothers_name = "#{mothers_name}#{(mothers_name.hash % 999)}" if Synthea::Config.population.append_hash_to_person_names == true

          mothers_surname = Faker::Name.last_name
          mothers_surname = "#{mothers_surname}#{(mothers_surname.hash % 999)}" if Synthea::Config.population.append_hash_to_person_names == true
          entity[:name_mother] = "#{mothers_name} #{mothers_surname}"

          fathers_name = Faker::Name.first_name
          fathers_name = "#{fathers_name}#{(fathers_name.hash % 999)}" if Synthea::Config.population.append_hash_to_person_names == true
          entity[:name_father] = "#{fathers_name} #{entity[:name_last]}"

          # identifiers
          entity[:identifier_ssn] = "999-#{rand(10..99)}-#{rand(1000..9999)}"

          entity[:med_changes] = Hash.new { |hsh, key| hsh[key] = [] }
          choose_socioeconomic_values(entity)
        end
      end

      # People age
      rule :age, [:birth, :age], [:age] do |time, entity|
        if entity.alive?(time)
          birthdate = entity.event(:birth).time
          age = entity[:age]
          entity[:age] = ((time.to_i - birthdate.to_i) / 1.year).floor
          if entity[:age] > age
            dt = nil
            begin
              dt = DateTime.new(time.year, birthdate.month, birthdate.mday, birthdate.hour, birthdate.min, birthdate.sec, birthdate.formatted_offset)
            rescue StandardError
              # this person was born on a leap-day
              dt = time
            end
            entity.events.create(dt.to_time, :grow, :age)
          end
          # stuff happens when you're an adult
          if entity[:age] == 16
            # you get a driver's license
            entity[:identifier_drivers] = "S999#{rand(10_000..99_999)}" unless entity[:identifier_drivers]
          elsif entity[:age] == 18
            # you get respect
            if entity[:gender] == 'M'
              entity[:name_prefix] = 'Mr.' unless entity[:name_prefix]
            else
              entity[:name_prefix] = 'Ms.' unless entity[:name_prefix]
            end
          elsif entity[:age] == 20 && entity[:identifier_passport].nil?
            # you might get a passport
            entity[:identifier_passport] = (rand(0..1) == 1)
            entity[:identifier_passport] = "X#{rand(10_000_000..99_999_999)}X" if entity[:identifier_passport]
          elsif entity[:age] == 27 && !entity[:marital_status] # median age of marriage (26 for women, 28 for men)
            if rand < 0.8
              # you might get married
              entity[:marital_status] = 'M'
              if entity[:gender] == 'F'
                entity[:name_prefix] = 'Mrs.'
                entity[:name_maiden] = entity[:name_last]
                entity[:name_last] = Faker::Name.last_name
                entity[:name_last] = "#{entity[:name_last]}#{(entity[:name_last].hash % 999)}"
              end
            else
              entity[:marital_status] = 'S'
            end
            # this doesn't account for divorces or widows right now
          elsif entity[:age] == 30
            # you might get overeducated
            entity[:name_suffix] = %w(PhD JD MD).sample if entity[:ses] && entity[:ses][:education] >= 0.95 && !entity[:name_suffix]
          end
        end
      end

      # People grow
      rule :grow, [:age, :gender], [:height, :weight, :bmi] do |time, entity|
        # Assume a linear growth rate until average size is achieved at age 20
        # TODO consider genetics, social determinants of health, etc
        if entity.alive?(time)
          unprocessed_events = entity.events.unprocessed.select { |e| e.type == :grow }
          unprocessed_events.each do |event|
            entity.events.process(event)
            age = entity[:age]
            gender = entity[:gender]
            height = entity.get_vital_sign_value(:height)
            weight = entity.get_vital_sign_value(:weight)
            if age <= 20
              height = growth_chart('height', gender, entity[:age], entity[:height_percentile])
              weight = growth_chart('weight', gender, entity[:age], entity[:weight_percentile])
            elsif age <= Synthea::Config.lifecycle.adult_max_weight_age
              # getting older and fatter
              range = Synthea::Config.lifecycle.adult_weight_gain
              adult_weight_gain = rand(range.first..range.last)
              weight += adult_weight_gain
            elsif age >= Synthea::Config.lifecycle.geriatric_weight_loss_age
              # getting older and wasting away
              range = Synthea::Config.lifecycle.geriatric_weight_loss
              geriatric_weight_loss = rand(range.first..range.last)
              weight -= geriatric_weight_loss
            end
            # set the BMI
            entity.set_vital_sign(:height, height, 'cm')
            entity.set_vital_sign(:weight, weight, 'kg')
            entity.set_vital_sign(:bmi, calculate_bmi(height, weight), 'kg/m2')
          end
        end
      end

      rule :diabetic_vital_signs, [:gender, :weight, :bmi], [:blood_glucose, :blood_pressure] do |_time, entity|
        range = case entity['diabetic_kidney_damage']
                when 1
                  Synthea::Config.metabolic.basic_panel.creatinine_clearance.mild_kidney_damage
                when 2
                  Synthea::Config.metabolic.basic_panel.creatinine_clearance.moderate_kidney_damage
                when 3
                  Synthea::Config.metabolic.basic_panel.creatinine_clearance.severe_kidney_damage
                when 4
                  Synthea::Config.metabolic.basic_panel.creatinine_clearance.esrd
                else
                  if entity[:gender] == 'F'
                    Synthea::Config.metabolic.basic_panel.creatinine_clearance.normal.female
                  else
                    Synthea::Config.metabolic.basic_panel.creatinine_clearance.normal.male
                  end
                end
        creatinine_clearance = rand(range.first..range.last)
        entity.set_vital_sign(:egfr, creatinine_clearance, 'mL/min/{1.73_m2}')

        creatinine = begin
                       reverse_calculate_creatine(entity, creatinine_clearance)
                     rescue
                       1.0
                     end
        entity.set_vital_sign(:creatinine, creatinine, 'mg/dL')

        bmi = entity.vital_sign(:bmi)
        if bmi
          bmi = bmi[:value]
          bloodglucose = blood_glucose(bmi, entity[:prediabetes], entity[:diabetes])

          # How much does A1C need to be lowered to get to goal?
          # Metformin and sulfonylureas may lower A1C 1.5 to 2 percentage points,
          # GLP-1 agonists and DPP-4 inhibitors 0.5 to 1 percentage point on average, and
          # insulin as much as 6 points or more, depending on where you start.
          # -- http://www.diabetesforecast.org/2013/mar/your-a1c-achieving-personal-blood-glucose-goals.html
          # [:metformin, :glp1ra, :sglt2i, :basal_insulin, :prandial_insulin]
          #     mono        bi      tri        insulin          insulin++
          record = entity.record_synthea

          bloodglucose -= 1.5 if record.medication_active?('24_hr_metformin_hydrochloride_500_mg_extended_release_oral_tablet'.to_sym)
          bloodglucose -= 0.5 if record.medication_active?('3_ml_liraglutide_6_mg/ml_pen_injector'.to_sym)
          bloodglucose -= 0.5 if record.medication_active?('canagliflozin_100_mg_oral_tablet'.to_sym)
          bloodglucose -= 3.0 if record.medication_active?('insulin_human,_isophane_70_unt/ml_/_regular_insulin,_human_30_unt/ml_injectable_suspension_[humulin]'.to_sym)
          bloodglucose -= 6.0 if record.medication_active?('insulin_lispro_100_unt/ml_injectable_solution_[humalog]'.to_sym)
          entity.set_vital_sign(:blood_glucose, bloodglucose.round(1), '%')
        end

        # estimate values
        if entity['hypertension']
          entity.set_vital_sign(:systolic_blood_pressure, pick(Synthea::Config.metabolic.blood_pressure.hypertensive.systolic), 'mmHg')
          entity.set_vital_sign(:diastolic_blood_pressure, pick(Synthea::Config.metabolic.blood_pressure.hypertensive.diastolic), 'mmHg')
        else
          entity.set_vital_sign(:systolic_blood_pressure, pick(Synthea::Config.metabolic.blood_pressure.normal.systolic), 'mmHg')
          entity.set_vital_sign(:diastolic_blood_pressure, pick(Synthea::Config.metabolic.blood_pressure.normal.diastolic), 'mmHg')
        end
        # calculate the components of a lipid panel
        index = 0
        index = 1 if entity['diabetes_severity']
        index = entity['diabetes_severity'] if entity['diabetes_severity']
        cholesterol = Synthea::Config.metabolic.lipid_panel.cholesterol
        triglycerides = Synthea::Config.metabolic.lipid_panel.triglycerides
        hdl = Synthea::Config.metabolic.lipid_panel.hdl

        entity.set_vital_sign(:total_cholesterol, rand(cholesterol[index]..cholesterol[index + 1]), 'mg/dL')
        entity.set_vital_sign(:triglycerides, rand(triglycerides[index]..triglycerides[index + 1]), 'mg/dL')
        entity.set_vital_sign(:hdl, rand(hdl[index + 1]..hdl[index]), 'mg/dL')

        ldl = entity.get_vital_sign_value(:total_cholesterol) - entity.get_vital_sign_value(:hdl) - (0.2 * entity.get_vital_sign_value(:triglycerides))
        entity.set_vital_sign(:ldl, ldl.to_i, 'mg/dL')

        # calculate the components of a metabolic panel and associated observations
        normal = Synthea::Config.metabolic.basic_panel.normal
        metabolic_panel = {
          urea_nitrogen: rand(normal.urea_nitrogen.first..normal.urea_nitrogen.last),
          creatinine: rand(normal.creatinine.first..normal.creatinine.last),
          calcium: rand(normal.calcium.first..normal.calcium.last)
        }

        electrolytes_panel = {
          chloride: rand(normal.chloride.first..normal.chloride.last),
          potassium: rand(normal.potassium.first..normal.potassium.last),
          carbon_dioxide: rand(normal.co2.first..normal.co2.last),
          sodium: rand(normal.sodium.first..normal.sodium.last)
        }

        # calculate glucose out of the normal
        glucose = Synthea::Config.metabolic.basic_panel.glucose
        index = 2 if index > 2
        metabolic_panel[:glucose] = rand(glucose[index]..glucose[index + 1])
        # calculate creatine values
        range = nil
        if entity[:gender] && entity[:gender] == 'M'
          range = Synthea::Config.metabolic.basic_panel.creatinine_clearance.normal.male
        else
          range = Synthea::Config.metabolic.basic_panel.creatinine_clearance.normal.female
        end
        creatinine_clearance = rand(range.first..range.last)
        metabolic_panel[:creatinine] = begin
                                         reverse_calculate_creatine(entity, creatinine_clearance)
                                       rescue
                                         1.0
                                       end
        range = Synthea::Config.metabolic.basic_panel.microalbumin_creatine_ratio.normal
        entity.set_vital_sign(:microalbumin_creatine_ratio, rand(range.first..range.last), 'mg/g')
        if creatinine_clearance > 60
          entity.set_vital_sign(:egfr, '>60', 'mL/min/{1.73_m2}')
        else
          entity.set_vital_sign(:egfr, creatinine_clearance, 'mL/min/{1.73_m2}')
        end

        metabolic_panel.each { |k, v| entity.set_vital_sign(k, v, 'mg/dL') }
        electrolytes_panel.each { |k, v| entity.set_vital_sign(k, v, 'mmol/L') }

        microalbumin_creatine_ratio = Synthea::Config.metabolic.basic_panel.microalbumin_creatine_ratio

        range = case entity['diabetic_kidney_damage']
                when 4
                  microalbumin_creatine_ratio.proteinuria
                when 3
                  microalbumin_creatine_ratio.microalbuminuria_uncontrolled
                when 2
                  microalbumin_creatine_ratio.microalbuminuria_controlled
                else
                  microalbumin_creatine_ratio.normal
                end
        entity.set_vital_sign(:microalbumin_creatine_ratio, rand(range.first..range.last), 'mg/g')
      end

      def blood_glucose(bmi, prediabetes, diabetes)
        if diabetes
          if bmi > 48
            12.0
          elsif bmi <= 27
            6.6
          else
            bmi / 4.0
          end
          # very simple BMI function so that BMI 40 --> blood glucose ~ 10, but with a bounded min at 6.6 and bounded max at 12.0
        elsif prediabetes
          rand(5.8..6.4)
        else
          rand(5.0..5.7)
        end
      end

      # http://www.mcw.edu/calculators/creatinine.htm
      def reverse_calculate_creatine(entity, crcl)
        age = entity[:age] # years
        female = (entity[:gender] == 'F')
        weight = entity[:weight] # kilograms
        crcl = 100 if crcl.nil? # mg/dL
        crcl = 1 if crcl < 1
        creatine = ((140 - age) * weight) / (72 * crcl)
        creatine *= 0.85 if female
        creatine
      end

      # People die
      rule :death, [:age], [] do |time, entity|
        if entity.alive?(time)
          if rand <= likelihood_of_death(entity[:age])
            entity.events.create(time, :death, :death, true)
            self.class.record_death(entity, time)
          end
        end
      end

      def gender(ratios = { male: 0.5 })
        value = rand
        if value < ratios[:male]
          'M'
        else
          'F'
        end
      end

      # height in centimeters
      # weight in kilograms
      def calculate_bmi(height, weight)
        (weight / ((height / 100) * (height / 100)))
      end

      def growth_chart(type, gender, age, percentile)
        age_months_str = (age * 12).to_i.to_s

        hsh = @growth_chart[type][gender][age_months_str]

        # use the LMS values to calculate the intermediate values
        # ref: https://www.cdc.gov/growthcharts/percentile_data_files.htm
        l = hsh['l'].to_f
        m = hsh['m'].to_f
        s = hsh['s'].to_f
        z = Statistics2.pnormaldist(percentile.to_f / 100.0) # z-score

        if l == 0.0 # no cases of this exist in the current data, this is included for completeness
          m * Math.E**(s * z)
        else
          m * (1 + (l * s * z))**(1.0 / l)
        end
      end

      def likelihood_of_death(age)
        # http://www.cdc.gov/nchs/nvss/mortality/gmwk23r.htm: 820.4/100000
        x = if age < 1
              # 508.1/100000/365
              508.1 / 100_000
            elsif age >= 1  && age <= 4
              # 15.6/100000/365
              15.6 / 100_000
            elsif age >= 5  && age <= 14
              # 10.6/100000/365
              10.6 / 100_000
            elsif age >= 15 && age <= 24
              # 56.4/100000/365
              56.4 / 100_000
            elsif age >= 25 && age <= 34
              # 74.7/100000/365
              74.7 / 100_000
            elsif age >= 35 && age <= 44
              # 145.7/100000/365
              145.7 / 100_000
            elsif age >= 45 && age <= 54
              # 326.5/100000/365
              326.5 / 100_000
            elsif age >= 55 && age <= 64
              # 737.8/100000/365
              737.8 / 100_000
            elsif age >= 65 && age <= 74
              # 1817.0/100000/365
              1817.0 / 100_000
            elsif age >= 75 && age <= 84
              # 4877.3/100000/365
              4877.3 / 100_000
            elsif age >= 85 && age <= 94
              # 13499.4/100000/365
              13_499.4 / 100_000
            else
              # 50000/100000/365
              50_000.to_f / 100_000
            end
        Synthea::Rules.convert_risk_to_timestep(x, 365)
      end

      def self.age(time, birthdate, deathdate, unit = :years)
        case unit
        when :months
          left = deathdate.nil? ? time : deathdate
          (left.month - birthdate.month) + (12 * (left.year - birthdate.year)) + (left.day < birthdate.day ? -1 : 0)
        else
          divisor = 1.method(unit).call

          left = deathdate.nil? ? time : deathdate
          ((left - birthdate) / divisor).floor
        end
      end

      def self.socioeconomic_score(entity)
        weighting = Synthea::Config.socioeconomic_status.weighting

        ses = entity[:ses]

        (ses[:education] * weighting.education) + (ses[:income] * weighting.income) + (ses[:occupation] * weighting.occupation)
      end

      def self.socioeconomic_category(entity)
        categories = Synthea::Config.socioeconomic_status.categories

        score = socioeconomic_score(entity)

        case score
        when categories.low[0]...categories.low[1]
          return 'Low'
        when categories.middle[0]...categories.middle[1]
          return 'Middle'
        when categories.high[0]..categories.high[1]
          return 'High'
        else
          raise "socioeconomic score #{score} outside expected range, make sure weightings add to 1 and categories cover 0..1"
        end
      end

      def choose_socioeconomic_values(entity)
        # for now, these are assigned at birth
        # eventually these should be able to change. for example major illness before age 18 could lead to a reduced education
        entity[:ses] = {}

        if entity[:income]
          # simple linear formula just maps federal poverty level to 0.0 and 75,000 to 1.0
          # 75,000 chosen based on https://www.princeton.edu/~deaton/downloads/deaton_kahneman_high_income_improves_evaluation_August2010.pdf

          # (11000, 0) -> (75000, 1)
          # m = y2-y1/x2-x1 = 1/64000
          # y = mx+b, y = x/64000 - 11/64
          entity[:ses][:income] = if entity[:income] >= 75_000
                                    1.0
                                  elsif entity[:income] <= 11_000
                                    0.0
                                  else
                                    entity[:income].to_f / 64_000 - 11.0 / 64.0
                                  end
        else
          entity[:ses][:income] = rand
        end

        edu_scores = Synthea::Config.socioeconomic_status.values.education

        if entity[:education].nil?
          entity[:ses][:education] = rand
        else
          range = edu_scores.send(entity[:education])
          entity[:ses][:education] = rand(range[0]..range[1])
        end

        entity[:ses][:occupation] = rand # by default occupation is only 10% of SES and is tough to quantify, so just make it random
      end

      # This returns a random integer in a supplied range, possibly weighted; weighting ranges from 0 (prefer
      # lower numbers) to 5 (even distribution) to 10 (prefer higher numbers)
      def self.weighted_random_distribution(range, weighting)
        # NOTE: Could probably be updated to use Distribution::Exponential.rng in some way
        raise 'Error, weighting must range from 0 to 10' if weighting < 0 || weighting > 10
        # Normalize the range to have a min of 0
        normalized_max = range.max - range.min
        # Generate a curve based either on a root or a power for the appropriate shape
        exponent = (15.0 - weighting) / 10.0 # Ranges from 5/10 to 15/10
        # Start with a random number in the normalized range, apply the curve exponent, and normalize again to the original range
        value = (rand * normalized_max)**exponent
        normalization_factor = normalized_max / (normalized_max**exponent)
        (range.min + (value * normalization_factor)).round
      end

      #------------------------------------------------------------------------# begin class record functions
      def self.record_death(entity, time, reason = nil)
        entity.record_synthea.death(time)
        if reason
          entity[:cause_of_death] = reason
          # TODO: once CCDA supports cause of death, change the ccda_method parameter
          entity.record_synthea.encounter(:death_certification, time)
          entity.record_synthea.observation(:cause_of_death, time, reason, 'fhir' => :observation, 'ccda' => :no_action)
          entity.record_synthea.diagnostic_report(:death_certificate, time, 1) # note: ccda already no action here
        end
      end
    end
  end
end
