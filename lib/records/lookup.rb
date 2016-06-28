module Synthea

	#Store all code lookups here:

	OBS_LOOKUP = {
    height: { description: 'Body Height', code: '8302-2',  unit: 'cm'},
    weight: { description: 'Body Weight', code: '29463-7', unit: 'kg'},
    systolic_blood_pressure: { description: 'Systolic Blood Pressure', code: '8480-6', unit: 'mmHg'},
    diastolic_blood_pressure: { description: 'Diastolic Blood Pressure', code: '8462-4', unit: 'mmHg'},
    ha1c: { description: 'Hemoglobin A1c/Hemoglobin.total in Blood', code: '4548-4', unit: '%'},
    cholesterol: { description: 'Total Cholesterol', code: '2093-3', unit: 'mg/dL'},
    triglycerides: { description: 'Triglycerides', code: '2571-8', unit: 'mg/dL'},
    hdl: { description: 'High Density Lipoprotein Cholesterol', code: '2085-9', unit: 'mg/dL'},
    ldl: { description: 'Low Density Lipoprotein Cholesterol', code: '18262-6', unit: 'mg/dL'},
    lipid_panel: { description: 'Lipid Panel', code: '57698-3'},
    blood_pressure: { description: 'Blood Pressure', code: '55284-4'}
  }

	COND_LOOKUP = {
    #http://www.icd9data.com/2012/Volume1/780-799/790-796/790/790.29.htm
    hypertension: { description: 'Hypertension', codes: {'SNOMED-CT' => ['38341003']}},
    prediabetes: { description: 'Prediabetes', 
                   codes: {'ICD-9-CM' => ['790.29'], 
                           'ICD-10-CM' => ['R73.09'], 
                           'SNOMED-CT' => ['15777000']}},
    diabetes: { description: 'Diabetes', 
                   codes: {'SNOMED-CT' => ['44054006']}},

    nephropathy: { description: 'Diabetic renal disease (disorder)', codes: {'SNOMED-CT' => ['127013003']}},
    microalbuminuria: { description: 'Microalbuminuria due to type 2 diabetes mellitus (disorder)', codes: {'SNOMED-CT' => ['90781000119102']}},         
    proteinuria: { description: 'Proteinuria due to type 2 diabetes mellitus (disorder)', codes: {'SNOMED-CT' => ['157141000119108']}},         
    end_stage_renal_disease: { description: 'End stage renal disease (disorder)', codes: {'SNOMED-CT' => ['46177005']}},         

    retinopathy: { description: 'Diabetic retinopathy associated with type II diabetes mellitus (disorder)', codes: {'SNOMED-CT' => ['422034002']}},         
    nonproliferative_retinopathy: { description: 'Nonproliferative diabetic retinopathy due to type 2 diabetes mellitus (disorder)', codes: {'SNOMED-CT' => ['1551000119108']}},         
    proliferative_retinopathy: { description: 'Proliferative diabetic retinopathy due to type II diabetes mellitus (disorder)', codes: {'SNOMED-CT' => ['1501000119109']}},         
    macular_edema: { description: 'Macular edema and retinopathy due to type 2 diabetes mellitus (disorder)', codes: {'SNOMED-CT' => ['97331000119101']}},         
    blindness: { description: 'Blindness due to type 2 diabetes mellitus (disorder)', codes: {'SNOMED-CT' => ['60951000119105']}},         

    neuropathy: { description: 'Neuropathy due to type 2 diabetes mellitus (disorder)', codes: {'SNOMED-CT' => ['368581000119106']}},         
    amputation: { description: 'History of limb amputation (situation)', codes: {'SNOMED-CT' => ['271396005']}},

    food_allergy_peanuts: { description: 'Food Allergy: Peanuts', codes: {'SNOMED-CT' => ['91935009']}},
    food_allergy_tree_nuts: { description: 'Food Allergy: Tree Nuts', codes: {'SNOMED-CT' => ['91934008']}},
    food_allergy_fish: { description: 'Food Allergy: Fish', codes: {'SNOMED-CT' => ['417532002']}},
    food_allergy_shellfish: { description: 'Food Allergy: Shellfish', codes: {'SNOMED-CT' => ['300913006']}},
  
    stroke: { description: 'Stroke', codes: {'SNOMED-CT' => ['230690007']}},
    coronary_heart_disease: { description: 'Coronary Heart Disease', codes: {'SNOMED-CT' => ['53741008']}},
    myocardial_infarction: { description: 'Myocardial Infarction', codes: {'SNOMED-CT' => ['22298006']}},
    cardiac_arrest: {description: 'Cardiac Arrest', codes: {'SNOMED-CT' => ['410429000']}},
    atrial_fibrillation: { description: 'Atrial Fibrillation', codes: {'SNOMED-CT' => ['49436004']} }
  }

  RACE_ETHNICITY_CODES = {
    :white => '2106-3',
    :hispanic => '2131-1',
    :black => '2054-5',
    :asian => '2028-9',
    :native => '1002-5',
    :other => '2131-1',
    :irish => '2113-9',
    :italian => '2114-7',
    :english => '2110-5',
    :french => '2111-3',
    :german => '2112-1',
    :polish => '2115-4',
    :portuguese => '2131-1',
    :american => '2131-1',
    :french_canadian => '2131-1',
    :scottish => '2116-2',
    :russian => '2131-1',
    :swedish => '2131-1',
    :greek => '2131-1',
    :puerto_rican => '2180-8',
    :mexican => '2148-5',
    :central_american => '2155-0',
    :south_american => '2165-9',
    :african => '2058-6',
    :dominican => '2069-3',
    :chinese => '2034-7',
    :west_indian => '2075-0',
    :asian_indian => '2029-7',
    :american_indian => '1004-1',
    :arab => '2129-5',         
    :nonhispanic => '2186-5'   
  }

  PROCEDURE_LOOKUP = {
    amputation_left_arm: { description: 'Amputation of left arm', codes: {'SNOMED-CT' => ['13995008']}},
    amputation_left_hand: {description: 'Amputation of left hand', codes: {'SNOMED-CT' => ['46028000']}},
    amputation_left_foot: {description: 'Amputation of left foot', codes: {'SNOMED-CT' => ['180030006']}},
    amputation_left_leg: {description: 'Amputation of left leg', codes: {'SNOMED-CT' => ['79733001']}},
    amputation_right_arm: { description: 'Amputation of right arm', codes: {'SNOMED-CT' => ['13995008']}},
    amputation_right_hand: {description: 'Amputation of right hand', codes: {'SNOMED-CT' => ['46028000']}},
    amputation_right_foot: {description: 'Amputation of right foot', codes: {'SNOMED-CT' => ['180030006']}},
    amputation_right_leg: {description: 'Amputation of right leg', codes: {'SNOMED-CT' => ['79733001']}}
  }

  # https://www.uhccommunityplan.com/content/dam/communityplan/healthcareprofessionals/reimbursementpolicies/Preventive-Medicine-and-Screening-Policy-(R0013).pdf
  # https://www.aap.org/en-us/professional-resources/practice-support/financing-and-payment/documents/bf-pmsfactsheet.pdf
  # https://www.nlm.nih.gov/research/umls/mapping_projects/icd9cm_to_snomedct.html
  # http://icd10coded.com/convert/
  ENCOUNTER_LOOKUP = {
    age_lt_1: {description: 'Outpatient Encounter', codes: {"ICD-9-CM" => ['V20.2'], "ICD-10-CM" => ['Z00.129'], 'SNOMED-CT' => ['170258001']}},
    age_lt_4: {description: 'Outpatient Encounter', codes: {"ICD-9-CM" => ['V20.2'], "ICD-10-CM" => ['Z00.129'], 'SNOMED-CT' => ['170258001']}},
    age_lt_11: {description: 'Outpatient Encounter', codes: {"ICD-9-CM" => ['V20.2'], "ICD-10-CM" => ['Z00.129'], 'SNOMED-CT' => ['170258001']}},
    age_lt_17: {description: 'Outpatient Encounter', codes: {"ICD-9-CM" => ['V20.2'], "ICD-10-CM" => ['Z00.129'], 'SNOMED-CT' => ['170258001']}},
    age_lt_39: {description: 'Outpatient Encounter', codes: {"ICD-9-CM" => ['V70.0'], "ICD-10-CM" => ['Z00.00'],  'SNOMED-CT' => ['185349003']}},
    age_lt_64: {description: 'Outpatient Encounter', codes: {"ICD-9-CM" => ['V70.0'], "ICD-10-CM" => ['Z00.00'],  'SNOMED-CT' => ['185349003']}},
    age_senior: {description: 'Outpatient Encounter', codes: {"ICD-9-CM" => ['V70.0'], "ICD-10-CM" => ['Z00.00'],  'SNOMED-CT' => ['185349003']}}, 
    emergency: {description: 'Emergency Encounter', codes: {'SNOMED-CT' => ['50849002']}}
  }

  IMM_SCHEDULE = {
    :hepb => {
      :code => {'system'=>'http://hl7.org/fhir/sid/cvx','code'=>'08','display'=>'Hep B, adolescent or pediatric'},
      :at_months => [0, 1, 6]
    },
    :rv_mono => {
      :code => {'system'=>'http://hl7.org/fhir/sid/cvx','code'=>'119','display'=>'rotavirus, monovalent'},
      #  Monovalent (Rotarix) is a 2-dose series, as opposed to pentavalent (RotaTeq) which is a 3-dose series 
      :at_months => [2, 4]
    },
    :dtap => {
      :code => {'system'=>'http://hl7.org/fhir/sid/cvx','code'=>'20','display'=>'DTaP'},
      :at_months => [2, 4, 6, 15, 48]
    },
    :hib => {
      :code => {'system'=>'http://hl7.org/fhir/sid/cvx','code'=>'49','display'=>'Hib (PRP-OMP)'},
      # PRP-OMP (PedvaxHib or COMVAX) is a 2-dose series with a booster at 12-15 months, as opposed to PRP-T
      # (AC-THIB) which is a 3-dose series with a booster at 12-15 months
      :at_months => [2, 4, 12]
    },
    :pcv13 => {
      :code => {'system'=>'http://hl7.org/fhir/sid/cvx','code'=>'133','display'=>'Pneumococcal conjugate PCV 13'},
      :at_months => [2, 4, 6, 12, 780]
    },
    :ipv => {
      :code => {'system'=>'http://hl7.org/fhir/sid/cvx','code'=>'10','display'=>'IPV'},
      :at_months => [2, 4, 6, 48]
    },
    :flu => {
      :code => {'system'=>'http://hl7.org/fhir/sid/cvx','code'=>'140','display'=>'Influenza, seasonal, injectable, preservative free'},
      # This should really only happen Aug - Feb (preferring earlier).  That may take some trickery.
      # Since this is annual administration just populate the array with every 12 months, starting at 6 months.
      :at_months => (0..100).map {|year| year * 12 + 6 }
    },
    :mmr => {
      :code => {'system'=>'http://hl7.org/fhir/sid/cvx','code'=>'03','display'=>'MMR'},
      :at_months => [12, 48]
    },
    :var => {
      :code => {'system'=>'http://hl7.org/fhir/sid/cvx','code'=>'21','display'=>'varicella'},
      :at_months => [12, 48]
    },
    :hepa => {
      :code => {'system'=>'http://hl7.org/fhir/sid/cvx','code'=>'83','display'=>'Hep A, ped/adol, 2 dose'},
      # First dose should be 12-23 months, second dose 6-18 months after.  Choosing to do 12 months after.
      :at_months => [12, 24]
    },
    :men => {
      # MenACWY can be Menactra (114) or Menveo (136).  Arbitrarily chose Menactra.
      :code => {'system'=>'http://hl7.org/fhir/sid/cvx','code'=>'114','display'=>'meningococcal MCV4P'},
      :at_months => [132, 192]
    },
    :tdap => {
      :code => {'system'=>'http://hl7.org/fhir/sid/cvx','code'=>'115','display'=>'Tdap'},
      :at_months => [132]
    },
    :hpv => {
      :code => {'system'=>'http://hl7.org/fhir/sid/cvx','code'=>'62','display'=>'HPV, quadrivalent'},
      # [11 years, boosters 2 months and 6 months later] -- but since we only have encounters scheduled yearly
      # at this age, the boosters will be late.  To be revisited later.
      :at_months => [132, 134, 138]
    },
    :td => {
      :code => {'system'=>'http://hl7.org/fhir/sid/cvx','code'=>'113','display'=>'Td (adult) preservative free'},
      # 21 years and every 10 years after
      :at_months => [21, 31, 41, 51, 61, 71, 81, 91].map {|year| year * 12 }
    },
    :zoster => {
      :code => {'system'=>'http://hl7.org/fhir/sid/cvx','code'=>'121','display'=>'zoster'},
      :at_months => [720]
    },
    :ppsv23 => {
      :code => {'system'=>'http://hl7.org/fhir/sid/cvx','code'=>'33','display'=>'pneumococcal polysaccharide vaccine, 23 valent'},
      :at_months => [792]
    },
  }
end