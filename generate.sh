#!/bin/sh

NUM=$1
if [ -z "$NUM" ]; then
  echo "You must provide a number of patients"
  exit 2
fi

./run_synthea \
  --exporter.fhir.bulk_data false \
  --exporter.fhir.included_resources Patient,Encounter,Observation,Condition,Practitioner \
  --exporter.practitioner.fhir.export true \
  -cs 54321 \
  -s 54321 \
  -r 20230403 \
  -e 20230403 \
  -p "$NUM" \
  Stockholm Huddinge