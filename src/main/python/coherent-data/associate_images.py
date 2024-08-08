import argparse
import base64
from io import BytesIO
import glob
import json
import os
import pandas as pd
from pathlib import Path
from PIL import Image
import shutil
import uuid

from dicom import create_oct_dicom, create_fundus_dicom

OCT_PROCEDURE_CODE = '700070005'
OCT_DIAGREPORT_CODE = '87674-8'

FUNDUS_PROCEDURE_CODE = '314971001'

NPDR_CONDITION_CODE = '1551000119108'
PDR_CONDITION_CODE = '1501000119109'
DME_CONDITION_CODE = '97331000119101'

DR_STAGE_OBS_CODE = '71490-7' # left eye, but both are the same in our model
DR_STAGE_VALUE_CODES = [
    "LA18643-9", # 0
    "LA18644-7", # 1
    "LA18645-4", # 2
    "LA18646-2", # 3
    "LA18648-8"  # 4
]

def parse_args():
    parser = argparse.ArgumentParser()
    parser.add_argument(
        "--clean",
        dest="clean",
        action="store_true",
        help="Clean output directory before running",
    )
    parser.add_argument(
        "fundus_index",
        help="Path to fundus images index csv file",
    )
    parser.add_argument(
        "oct_index",
        help="Path to OCT images index csv file",
    )
    parser.add_argument(
        "fhir",
        help="Path to folder containing fhir jsons",
    )
    parser.add_argument(
        "--output",
        dest="output",
        default="./output",
        help="Output directory",
    )

    args = parser.parse_args()
    return args


def main():
    args = parse_args()

    if args.clean:
        clean(args.output)

    fundus_index = pd.read_csv(args.fundus_index, dtype=str)
    fundus_index['selected'] = False
    fundus_index['File Path'] = str(Path(args.fundus_index).parent) + '/' + fundus_index['File Path']

    oct_index = pd.read_csv(args.oct_index, dtype=str)
    oct_index['selected'] = False
    oct_index['File Path'] = str(Path(args.oct_index).parent) + '/' + oct_index['File Path']

    fhir_jsons = glob.glob(f'{args.fhir}/*.json')

    for file in fhir_jsons:
        if 'hospitalInformation' in file or 'practitionerInformation' in file:
            continue
        process_file(file, fundus_index, oct_index, args.output)

def clean(output):
    outputpath = Path(output)
    if os.path.exists(outputpath) and os.path.isdir(outputpath):
        shutil.rmtree(outputpath)

    os.makedirs(outputpath / 'fhir/')
    os.makedirs(outputpath / 'dicom/')
    os.makedirs(outputpath / 'images/')
    (outputpath / '.keep').touch()


def process_file(file, fundus_index, oct_index, output):
    print(f"Processing {file}")
    with open(file) as f:
        bundle = json.load(f)

    resources_by_id = {}  # map of id(technically fullUrl) -> resource
    imaging_studies = []
    diag_reports = []
    diagnoses = { 'npdr': None, 'pdr': None, 'dme': None }
    observations = []

    for entry in bundle['entry']:
        resource = entry['resource']
        resources_by_id[entry['fullUrl']] = resource

        if resource['resourceType'] == 'ImagingStudy':
            if resource['procedureCode'][0]['coding'][0]['code'] == OCT_PROCEDURE_CODE:
                imaging_studies.append(resource)
            elif resource['procedureCode'][0]['coding'][0]['code'] == FUNDUS_PROCEDURE_CODE:
                imaging_studies.append(resource)
                diag_reports.append({})

        elif resource['resourceType'] == 'DiagnosticReport' and \
             resource['code']['coding'][0]['code'] == OCT_DIAGREPORT_CODE:
            diag_reports.append(resource)

        elif resource['resourceType'] == 'Condition':
            code = resource['code']['coding'][0]['code']
            if code == NPDR_CONDITION_CODE:
                diagnoses['npdr'] = resource
            elif code == PDR_CONDITION_CODE:
                diagnoses['pdr'] = resource
            elif code == DME_CONDITION_CODE:
                diagnoses['dme'] = resource

        elif resource['resourceType'] == 'Observation' and \
             resource['code']['coding'][0]['code'] == DR_STAGE_OBS_CODE:
            observations.append(resource)

    if not imaging_studies:
        return

    # import pdb; pdb.set_trace()

    # print(f"Found {len(imaging_studies)} imaging studies")

    previous_context = None
    previous_image = { 'OCT': [None, None], 'fundus': [None, None] }

    for i in range(len(imaging_studies)):
        imaging_study = imaging_studies[i]
        diag_report = diag_reports[i]
        # these should always be 1:1
        # TODO: seems not to be true when record filtering is enabled

        context = find_context(bundle, imaging_study, diag_report, resources_by_id, diagnoses, observations)
        img_type = context['type']
        for index, instance in enumerate(imaging_study['series'][0]['instance']):
            context['instance'] = instance
            context['laterality'] = instance['title'] # OD or OS
            if previous_image[img_type][index] and previous_context and previous_context['dr_stage'] == context['dr_stage']:
                image = previous_image[img_type][index]
            else:
                image = pick_image(fundus_index, oct_index, context)

            if not image:
                # print("no image found")
                previous_image[img_type][index] = None
                continue

            dicom = create_dicom(image, imaging_study, context)
            dicom_uid = imaging_study['identifier'][0]['value'][8:]  # cut off urn:uuid:
            instance_uid = context['instance']['uid']

            pat_name = Path(file).stem
            if dicom:
                dicom.save_as(f'{output}/dicom/{pat_name}_{dicom_uid}_{instance_uid}.dcm')
            image.save(f'{output}/images/{pat_name}_{dicom_uid}_{instance_uid}.jpg')

            media = create_fhir_media(context, imaging_study, image, dicom)
            bundle['entry'].append(wrap_in_entry(media))
            previous_image[img_type][index] = image

        previous_context = context

    outputpath = Path(f"{output}/fhir") / os.path.basename(file)
    with open(outputpath, 'w') as f:
        json.dump(bundle, f, indent=2)

def find_context(bundle, imaging_study, diag_report, resources_by_id, diagnoses, observations):
    context = {}
    context['patient'] = bundle['entry'][0]['resource']
    context['imaging_study'] = imaging_study
    context['code'] = imaging_study['procedureCode'][0]['coding'][0]['code']
    if context['code'] == OCT_PROCEDURE_CODE:
        context['type'] = 'OCT'
    else:
        context['type'] = 'fundus'
    context['encounter'] = resources_by_id[imaging_study['encounter']['reference']]
    encounter_date = context['encounter']['period']['start']
    encounter_id = context['encounter']['id']

    context['npdr'] = has_diagnosis(diagnoses, 'npdr', encounter_date, encounter_id)
    context['pdr'] = has_diagnosis(diagnoses, 'pdr', encounter_date, encounter_id)
    context['dme'] = has_diagnosis(diagnoses, 'dme', encounter_date, encounter_id)
    
    context['dr_stage'] = get_stage(observations, encounter_id)

    context['diagnosticreport'] = diag_report

    return context


def has_diagnosis(diagnoses, diag_type, date, encounter_id):
    condition = diagnoses[diag_type]
    if not condition:
        return False

    # was it diagnosed at the current encounter?
    if condition['encounter']['reference'] == f"urn:uuid:{encounter_id}":
        return True

    # or was it diagnosed previously?
    # string comparison should be safe since both are iso8601 dates yyyy-mm-dd
    if condition['onsetDateTime'] < date:
        return True

    return False


def get_stage(observations, encounter_id):
    stage_obs = next((o for o in observations if o['encounter']['reference'] == f"urn:uuid:{encounter_id}"), None)

    if not stage_obs:
        return 0

    stage = DR_STAGE_VALUE_CODES.index(stage_obs['valueCodeableConcept']['coding'][0]['code'])

    return str(stage)


def pick_image(fundus_index, oct_index, context):
    if context['code'] == OCT_PROCEDURE_CODE:
        index = filter_oct_index(oct_index, context)
    else:
        index = filter_fundus_index(fundus_index, context)

    available_to_select = index[index['selected'] == False]
    if available_to_select.empty:
        return None

    selected = available_to_select.sample(n=1)
    index.at[selected.index[0], 'selected'] = True

    path = selected['File Path'].iat[0]
    image = Image.open(path)

    return image

def filter_oct_index(oct_index, context):
    # currently available in the index:
    #  oct_index[Class] == [CNV, DRUSEN, DME, Normal]
    # CNV = Choroidal neovascularization
    # DME = diabetic macular edema

    if context['dme']:
        oct_index = oct_index[oct_index['Class'] == 'DME']
    elif context['pdr']:
        oct_index = oct_index[oct_index['Class'] == 'CNV']
    else:
        oct_index = oct_index[oct_index['Class'] == 'Normal']

    return oct_index


# def filter_fundus_index(fundus_index, context):
#     # fundus_index items are 0/1
#     # DR = diabetic retinopathy
#     # MH = macular hole
#     # DN = ??
#     # BRVO = Branch Retinal Vein Occlusion
#     # ODC = Optic Disc Coloboma?
#     # ODE = Optic disc edema?
#     # (there were more but i deleted all columns with all 0s)

#     if context['npdr']:
#         fundus_index = fundus_index[fundus_index['DR'] == '1']
#     else:
#         fundus_index = fundus_index[fundus_index['DR'] == '0']

#     return fundus_index

def filter_fundus_index(fundus_index, context):
    # Retinopathy grade = lines up to our stages
    # Risk of macular edema = unclear. seems like 0 = no DME, 1/2 = DME

    fundus_index = fundus_index[fundus_index['Retinopathy grade'] == context['dr_stage']]

    if context['dme']:
        fundus_index = fundus_index[fundus_index['Risk of macular edema'] != '0']
    else:
        fundus_index = fundus_index[fundus_index['Risk of macular edema'] == '0']

    return fundus_index

def create_fhir_media(context, imaging_study, image, dicom):
    buffered = BytesIO()
    image.save(buffered, format="JPEG")
    img_str = base64.b64encode(buffered.getvalue()).decode("utf-8")
    return {
        'resourceType': 'Media',
        'id': str(uuid.uuid4()),
        'identifier': [ {
            "use": "official",
            "system": "urn:ietf:rfc:3986",
            "value": f"urn:oid:{context['instance']['uid']}"
          } ],
        'partOf': [ref(imaging_study)],
        'status': 'completed',
        'subject': ref(context['patient']),
        'encounter': ref(context['encounter']),
        'content': {
            'contentType': 'image/jpeg', # application/dicom or image/jpeg
            'data': img_str # base64 
        }
    }


def wrap_in_entry(resource):
    return {
        "fullUrl": f"urn:uuid:{resource['id']}",
        "resource": resource,
        "request": {
            "method": "POST",
            "url": f"{resource['resourceType']}"
        }
    }

def ref(resource):
    return {"reference": f"urn:uuid:{resource['id']}"}

def create_dicom(image, imaging_study, context):
    # for now only dicomify the fundus photos
    if context['code'] == OCT_PROCEDURE_CODE:
        return create_oct_dicom(image, imaging_study, context)
    elif context['code'] == FUNDUS_PROCEDURE_CODE:
        return create_fundus_dicom(image, imaging_study, context)
    else:
        raise ValueError(f"unexpected image type {context['code']}")


if __name__ == '__main__':
    main()
