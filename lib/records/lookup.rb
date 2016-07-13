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
    blood_pressure: { description: 'Blood Pressure', code: '55284-4'},

    basic_metabolic_panel: { description: 'Basic Metabolic Panel', code: '51990-0'},
      glucose: { description: 'Glucose', code: '2339-0', unit: 'mg/dL'},
      urea_nitrogen: { description: 'Urea Nitrogen', code: '6299-2', unit: 'mg/dL'},
      creatinine: { description: 'Creatinine', code: '38483-4', unit: 'mg/dL'},
      calcium: { description: 'Calcium', code: '49765-1', unit: 'mg/dL'},
      electrolytes_panel: { description: 'Electrolytes Panel', code: '55231-5'},
        sodium: { description: 'Sodium', code: '2947-0', unit: 'mmol/L'},
        potassium: { description: 'Potassium', code: '6298-4', unit: 'mmol/L'},
        chloride: { description: 'Chloride', code: '2069-3', unit: 'mmol/L'},
        carbon_dioxide: { description: 'Carbon Dioxide', code: '20565-8', unit: 'mmol/L'},

    microalbumin_creatine_ratio: { description: 'Microalbumin Creatine Ratio', code: '14959-1', unit: 'mg/g'},
    egfr: { description: 'Estimated Glomerular Filtration Rate', code: '33914-3', unit: 'mL/min/{1.73_m2}'}
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

  CAREPLAN_LOOKUP = {
    diabetes: { description: 'Diabetes self management plan', codes: {'SNOMED-CT'=>['698360004']}},
    diabetic_diet: { description: 'Diabetic diet', codes: {'SNOMED-CT'=>['160670007']}},
    exercise: { description: 'Exercise therapy', codes: {'SNOMED-CT'=>['229065009']}},
    cardiovascular_disease: { description: 'Angina self management plan', codes: {'SNOMED-CT'=>['698358001']}},
    healthy_diet: { description: 'Healthy Diet', codes: {'SNOMED-CT'=>['226234005']}},
    stress_management: { description: 'Stress Management', codes: {'SNOMED-CT'=>['226060000']}},
    stop_smoking: { description: 'Stopped Smoking', codes: {'SNOMED-CT'=>['160617001']}}
  }

  REASON_LOOKUP = {
    diabetes_well_controlled: {
      description: 'Type II Diabetes Mellitus Well Controlled',
      codes: {'SNOMED-CT'=>['444110003']}
    },
    cardiovascular_improved: {
      description: 'Cardiac status is consistent with or improved from preoperative baseline',
      codes: {'SNOMED-CT'=>['413757005']}
    },
    stop_drug: {
      description: 'Recommendation to stop drug treatment',
      codes: {'SNOMED-CT'=>['304540007']}
    }
  }

  MEDICATION_LOOKUP = {
    metformin: { description: '24 HR Metformin hydrochloride 500 MG Extended Release Oral Tablet', codes: {'RxNorm'=>['860975']}},
    glp1ra: { description: '3 ML liraglutide 6 MG/ML Pen Injector', codes: {'RxNorm'=>['897122']}},
    sglt2i: { description: 'canagliflozin 100 MG Oral Tablet', codes: {'RxNorm'=>['1373463']}},
    basal_insulin: { description: 'insulin human, isophane 70 UNT/ML / Regular Insulin, Human 30 UNT/ML Injectable Suspension [Humulin]', codes: {'RxNorm'=>['106892']}},
    prandial_insulin: { description: 'Insulin Lispro 100 UNT/ML Injectable Solution [Humalog]', codes: {'RxNorm'=>['865098']}},
    
    #cardiovascular disease medications. Dosage amounts are roughly estimated, need to be confirmed
    clopidogrel: { description: 'Clopidogrel 75 MG Oral Tablet', codes: {'RxNorm'=>['309362']}},
    simvastatin: { description: 'Simvastatin 20 MG Oral Tablet', codes: {'RxNorm'=>['312961']}},
    amlodipine: { description: 'Amlodipine 5 MG Oral Tablet', codes: {'RxNorm'=>['197361']}},
    nitroglycerin: { description: 'Nitroglycerin 0.4 MG/ML Injectible Solution', codes: {'RxNorm'=>['312006']}},
    atorvastatin: { description: 'Atorvastatin 80 MG Oral Tablet', codes: {'RxNorm'=>['259255']}},
    captopril: { description: 'Captopril 25 MG Oral Tablet', codes: {'RxNorm'=>['833036']}},
    warfarin: { description: 'Warfarin Sodium 5 MG Oral Tablet', codes: {'RxNorm'=>['855332']}},
    verapamil: { description: 'Verapamil Hydrochloride 40 MG', codes: {'RxNorm'=>['897718']}},
    digoxin: { description: 'Digoxin 0.125 MG Oral Tablet', codes: {'RxNorm'=>['197604']}},
    epinephrine: { description: '1 ML Epinephrine 1 MG/ML Prefilled Syringe', codes: {'RxNorm'=>['727374']}},
    amiodarone: { description: '3 ML Amiodarone hydrocholoride 50 MG/ML Prefilled Syringe', codes: {'RxNorm'=>['834357']}},
    atropine: { description: 'Atropine Sulfate 1 MG/ML Injectable Solution', codes: {'RxNorm'=>['1190795']}},
    alteplase: { description: 'Alteplase 1 MG/ML Injectable Solution', codes: {'RxNorm'=>['308056']}}
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
    amputation_right_leg: {description: 'Amputation of right leg', codes: {'SNOMED-CT' => ['79733001']}},
    defibrillation: {description: 'Monophasic defibrillation', codes: {'SNOMED-CT' => ['429500007']}},
    implant_cardioverter_defib: {description: 'Insertion of biventricular implantable cardioverter defibrillator', codes: {'SNOMED-CT' => ['447365002']}},
    catheter_ablation: {description: 'Catheter ablation of tissue of heart', codes: {'SNOMED-CT' => ['18286008']}},
    percutaneous_coronary_intervention: { description: 'Percutaneous coronary intervention', codes: {'SNOMED-CT' => ['415070008']}},
    coronary_artery_bypass_grafting: {description: 'Coronary artery bypass grafting', codes: {'SNOMED-CT' => ['232717009']}},
    mechanical_thrombectomy: {description: 'Percutaneous mechanical thrombectomy of portal vein using fluoroscopic guidance', codes: {'SNOMED-CT'=>['433112001']}},
    electrical_cardioversion: {description: 'Electrical cardioversion', codes: {'SNOMED-CT' => ['180325003']}}
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


  # http://www.cdc.gov/vaccines/schedules/downloads/child/0-18yrs-schedule.pdf
  # http://www.cdc.gov/vaccines/schedules/downloads/adult/adult-schedule.pdf
  # http://www2a.cdc.gov/vaccines/iis/iisstandards/vaccines.asp?rpt=cvx
  # https://www2a.cdc.gov/vaccines/iis/iisstandards/vaccines.asp?rpt=tradename
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