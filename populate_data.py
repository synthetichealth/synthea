import os
import requests

"""
Populate your FHIR endpoint with generated Synthea data.
URL parameter is your FHIR server endpoint ending with /fhir/
You will run this script after the run_sythea script is finished generateing test patients for you.
To execute: python ./populate_data.py

"""

#fhir server endpoint
URL = ""

#fhir server json header content
headers = {"Content-Type": "application/fhir+json;charset=utf-8"}

# load data
for dirpath, dirnames, files in os.walk('./output/fhir'):
    for file_name in files:
        if file_name.startswith('hospital') and file_name.endswith('.json'):
            with open('./output/fhir/'+file_name, "r") as bundle_file:
                data = bundle_file.read()
            r = requests.post(url = URL, data = data.encode('utf-8'), headers = headers)
            assert r.status_code == 200, ( "Wrong status code: %s" % r.status_code )
            print(file_name)
        elif file_name.startswith('practitioner') and file_name.endswith('.json'):
            with open('./output/fhir/'+file_name, "r") as bundle_file:
                data = bundle_file.read()
            r = requests.post(url = URL, data = data.encode('utf-8'), headers = headers)
            assert r.status_code == 200, ( "Wrong status code: %s" % r.status_code )
            print(file_name)
        else:
            continue

# loop over all files in the output folder in order to upload each json file for each patient.
for dirpath, dirnames, files in os.walk('./output/fhir'):
    for file_name in files:
        with open('./output/fhir/'+file_name, "r") as bundle_file:
            data = bundle_file.read()

        r = requests.post(url = URL, data = data.encode('utf-8'), headers = headers)
        try:
            assert r.status_code == 200, ("Wrong status code %s on %s file" % (r.status_code, file_name))
            print(file_name)
        except Exception:
            continue
