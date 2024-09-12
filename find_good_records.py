import argparse
import glob
import json

NPDR_CONDITION_CODE = '1551000119108'
PDR_CONDITION_CODE = '1501000119109'
DME_CONDITION_CODE = '97331000119101'
DIABETES_CONDITION_CODE = "44054006"

parser = argparse.ArgumentParser(description='Select records for Coherent Eyes dataset')
parser.add_argument('path', help='folder path to process')
args = parser.parse_args()

fhir_jsons = glob.glob(f"{args.path}/fhir/*.json")

print("file,diabetes_onset,npdr_onset,pdr_onset,dme_onset,count")

for file in fhir_jsons:
    if 'hospitalInformation' in file or 'practitionerInformation' in file:
        continue
    with open(file) as f:
        bundle = json.load(f)

    diabetes_onset = "0000"
    npdr_onset = "0000"
    pdr_onset = "0000"
    dme_onset = "0000"

    for entry in bundle['entry']:
      resource = entry['resource']

      if resource['resourceType'] != 'Condition':
        continue

      code = resource['code']['coding'][0]['code']
      onset = resource['onsetDateTime']

      if code == NPDR_CONDITION_CODE:
        npdr_onset = onset
      elif code == PDR_CONDITION_CODE:
        pdr_onset = onset
      elif code == DME_CONDITION_CODE:
        dme_onset = onset
      elif code == DIABETES_CONDITION_CODE:
        diabetes_onset = onset


    print(f"{file},{diabetes_onset},{npdr_onset},{pdr_onset},{dme_onset},{len(bundle['entry'])}")
