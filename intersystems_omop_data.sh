# adjust to your liking
rm -rf output
rm -rf upload
mkdir upload

declare -a states=("Alabama" "Alaska" "Arizona" "Arkansas" "California" "Colorado" "Connecticut" "Delaware" "Florida" "Georgia" "Hawaii" "Idaho" "Illinois" "Indiana" "Iowa" "Kansas" "Kentucky" "Louisiana" "Maine" "Maryland" "Massachusetts" "Michigan" "Minnesota" "Mississippi" "Missouri" "Montana" "Nebraska" "Nevada" ""New Hampshire"" ""New Jersey"" ""New Mexico"" ""New York"" ""North Carolina"" ""North Dakota"" "Ohio" "Oklahoma" "Oregon" "Pennsylvania" ""Rhode Island"" ""South Carolina"" ""South Dakota"" "Tennessee" "Texas" "Utah" "Vermont" "Virginia" "Washington" ""West Virginia"" "Wisconsin" "Wyoming")
declare -a states=("Alabama" "Alaska" "Arizona" "Arkansas" "California" "Colorado" "Connecticut" "Delaware" "Florida" "Georgia" "Hawaii" "Idaho" "Illinois" "Indiana" "Iowa" "Kansas" "Kentucky" "Louisiana" "Maine" "Maryland" "Massachusetts" "Michigan" "Minnesota" "Mississippi" "Missouri" "Montana" "Nebraska" "Nevada" "Ohio" "Oklahoma" "Oregon" "Pennsylvania" "Tennessee" "Texas" "Utah" "Vermont" "Virginia" "Washington" "Wisconsin" "Wyoming")


for state in ${!states[@]}; do
  echo ${states[$state]}
  count=`shuf -i 100-150 -n 1`
  ./run_synthea -s 234 -p $count ${states[$state]} --exporter.baseDirectory="./output/output_${states[$state]}/"
  cd output/output_${states[$state]}/fhir
  find . -type f -exec sed -i s#"?identifier=https:\/\/github.com\/synthetichealth\/synthea|"#/\#g {} +
  find . -type f -exec sed -i s#"?identifier=http:\/\/hl7.org\/fhir\/sid\/us-npi|"#/\#g {} +
  jq -c . hospital*.json > hospital.ndjson
  zip -r hosp-${states[$state]}.zip hospital.ndjson
  jq -c . practitioner*.json > practitioner.ndjson
  zip -r prac-${states[$state]}.zip practitioner.ndjson
  jq -c . *.json > pop-${states[$state]}.ndjson
  zip -r pop-${states[$state]}.zip pop-${states[$state]}.ndjson
  cp *.zip ../../../upload
  cd ../../../
  pwd
done