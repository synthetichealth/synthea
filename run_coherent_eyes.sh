#!/bin/bash

basedir=`pwd`

base_run_synthea () {
    ./run_synthea -a 55-70 \
                  -fm src/test/resources/flexporter/eyes_on_fhir.yaml \
                  --exporter.baseDirectory=$outputfolder \
                  -s $seed \
                  --exporter.years_of_history=$years_of_history \
                  --generate.log_patients.detail=none \
                  --generate.only_alive_patients=true \
                  --generate.max_attempts_to_keep_patient=2000 \
                  "$@" \
                  $location
}


run_population () {
  popcount=$1

  base_run_synthea -p $((popcount / 4)) -k keep_diabetes_no_dr.json
  base_run_synthea -p $((popcount / 2)) -k keep_npdr_no_pdr.json
  base_run_synthea -p $((popcount / 4)) -k keep_pdr.json
}


#rm -rf output

# all populations have:
# 25% diabetes but no DR
# 50% NPDR, no PDR
# 25% PDR
# This is not a realistic proportion, but there's not much point in including a lot of records that have no relevant data
# Also, the total population run is 10x the target so we can downselect.
# We want records with a recent diagnosis, for 2 reasons. 
# 1) DR treatment is modeled per current standards (2024). 
# Treatment from say the 80s would have been a lot different and we're not trying to model that. 
# We minimize anachronism by picking records where things happen when they are supposed to.
# 2) File size. Treatment loops and images add a lot of data, so making those start later means the files don't get as crazy large.

# population 1
# 1000 records with 5-year history and only relevant conditions enabled
outputfolder="./output_population1000"
seed=12345
location=Massachusetts
years_of_history=5
run_population 10000

# population 2
# 100 records with 5 year history and all conditions enabled
outputfolder="./output_population100"
seed=98765
location=Virginia
years_of_history=5
run_population 1000

# population 3
# 5-10 curated records with full history and all conditions enabled
outputfolder="./output_population10"
seed=4444
location=Washington
years_of_history=0
run_population 1000


# cd src/main/python/coherent-data/
# source ./venv/bin/activate

# ./venv/bin/python associate_images.py ${basedir}/images/fundus_index.csv ${basedir}/images/oct_index.csv ${basedir}/output/fhir --clean --output ${basedir}/coherent_eyes

# # ./venv/bin/python associate_images.py ${basedir}/images/fundus_index.csv ${basedir}/images/oct_index.csv ${basedir}/samples --clean --output ${basedir}/coherent_eyes

# rm ${basedir}/dicom_errors.txt

# validate_iods --verbose /Users/dehall/synthea/nei/coherent_eyes/dicom/Annabel185_Lettie611_Fisher429_af88404e-aad1-c9cb-3e7f-07daf0e44eac_fundus_1.2.840.99999999.10633938.1562002233954_1.2.840.99999999.1.1.99330560.1562002233954.dcm > ${basedir}/dicom_errors.txt
# validate_iods --verbose /Users/dehall/synthea/nei/coherent_eyes/dicom/Annabel185_Lettie611_Fisher429_af88404e-aad1-c9cb-3e7f-07daf0e44eac_OCT_1.2.840.99999999.11240513.1609790227954_1.2.840.99999999.1.1.66970829.1609790227954.dcm >> ${basedir}/dicom_errors.txt