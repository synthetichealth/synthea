#!/bin/sh

NUM=$1
if [ -z "$NUM" ]; then
  echo "You must provide a number of patients"
  exit 2
fi

./run_synthea --exporter.baseDirectory "$WORKDIR" \
  --exporter.fhir.bulk_data true \
  --exporter.fhir.included_resources \
  AllergyIntolerance,Condition,Device,DiagnosticReport,DocumentReference,Encounter,Immunization,MedicationRequest,Observation,Patient,Procedure \
  -cs 54321 \
  -s 54321 \
  -r 20230403 \
  -e 20230403 \
  -p "$NUM" \
  Stockholm Huddinge