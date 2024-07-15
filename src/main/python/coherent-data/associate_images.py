import argparse
import base64
from io import BytesIO
import glob
import json
import numpy as np
import os
import pandas as pd
from pathlib import Path
from PIL import Image
import shutil
import uuid

from pydicom.dataset import Dataset, FileMetaDataset
from pydicom.sequence import Sequence

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

    for i in range(len(imaging_studies)):
        imaging_study = imaging_studies[i]
        diag_report = diag_reports[i]
        # these should always be 1:1
        # TODO: seems not to be true when record filtering is enabled

        context = find_context(bundle, imaging_study, diag_report, resources_by_id, diagnoses, observations)
        image = pick_image(fundus_index, oct_index, context)

        if not image:
            # print("no image found")
            continue

        dicom = create_dicom(image, imaging_study, context)
        dicom_uid = imaging_study['identifier'][0]['value'][8:]  # cut off urn:uuid:
        pat_name = Path(file).stem
        # dicom.save_as(f'{output}/dicom/{pat_name}_{dicom_uid}.dcm')
        image.save(f'{output}/dicom/{pat_name}_{dicom_uid}.jpg')

        media = create_fhir_media(context, imaging_study, image, dicom)
        bundle['entry'].append(wrap_in_entry(media))

    outputpath = Path(f"{output}/fhir") / os.path.basename(file)
    with open(outputpath, 'w') as f:
        json.dump(bundle, f, indent=2)

def find_context(bundle, imaging_study, diag_report, resources_by_id, diagnoses, observations):
    context = {}
    context['patient'] = bundle['entry'][0]['resource']
    context['imaging_study'] = imaging_study
    context['code'] = imaging_study['procedureCode'][0]['coding'][0]['code']
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

    return stage


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
        'identifier': imaging_study['identifier'], # just copy it
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
    # run `pydicom codify <path-to-dcm>` to build this

    # File meta info data elements
    file_meta = FileMetaDataset()
    file_meta.FileMetaInformationGroupLength = 192
    file_meta.FileMetaInformationVersion = b'\x00\x01'
    file_meta.MediaStorageSOPClassUID = '1.2.840.10008.5.1.4.1.1.2'
    file_meta.MediaStorageSOPInstanceUID = '1.3.6.1.4.1.5962.1.1.1.1.1.20040119072730.12322'
    file_meta.TransferSyntaxUID = '1.2.840.10008.1.2.1'
    file_meta.ImplementationClassUID = '1.3.6.1.4.1.5962.2'
    file_meta.ImplementationVersionName = 'DCTOOL100'
    file_meta.SourceApplicationEntityTitle = 'CLUNIE1'

    # Main data elements
    ds = Dataset()
    ds.SpecificCharacterSet = 'ISO_IR 100'
    ds.ImageType = ['ORIGINAL', 'PRIMARY', 'AXIAL']
    ds.InstanceCreationDate = '20040119'
    ds.InstanceCreationTime = '072731'
    ds.InstanceCreatorUID = '1.3.6.1.4.1.5962.3'
    ds.SOPClassUID = '1.2.840.10008.5.1.4.1.1.2'
    ds.SOPInstanceUID = '1.3.6.1.4.1.5962.1.1.1.1.1.20040119072730.12322'
    ds.StudyDate = '20040119'
    ds.SeriesDate = '19970430'
    ds.AcquisitionDate = '19970430'
    ds.ContentDate = '19970430'
    ds.StudyTime = '072730'
    ds.SeriesTime = '112749'
    ds.AcquisitionTime = '112936'
    ds.ContentTime = '113008'
    ds.AccessionNumber = ''
    ds.Modality = 'CT'
    ds.Manufacturer = 'GE MEDICAL SYSTEMS'
    ds.InstitutionName = 'JFK IMAGING CENTER'
    ds.ReferringPhysicianName = ''
    ds.TimezoneOffsetFromUTC = '-0500'
    ds.StationName = 'CT01_OC0'
    ds.StudyDescription = 'e+1'
    ds.ManufacturerModelName = 'RHAPSODE'
    ds.PatientName = 'CompressedSamples^CT1'
    ds.PatientID = '1CT1'
    ds.PatientBirthDate = ''
    ds.PatientSex = 'O'

    # Other Patient IDs Sequence
    other_patient_i_ds_sequence = Sequence()
    ds.OtherPatientIDsSequence = other_patient_i_ds_sequence

    # Other Patient IDs Sequence: Other Patient IDs 1
    other_patient_i_ds1 = Dataset()
    other_patient_i_ds_sequence.append(other_patient_i_ds1)
    other_patient_i_ds1.PatientID = 'ABCD1234'
    other_patient_i_ds1.TypeOfPatientID = 'TEXT'

    # Other Patient IDs Sequence: Other Patient IDs 2
    other_patient_i_ds2 = Dataset()
    other_patient_i_ds_sequence.append(other_patient_i_ds2)
    other_patient_i_ds2.PatientID = '1234ABCD'
    other_patient_i_ds2.TypeOfPatientID = 'TEXT'

    ds.PatientAge = '000Y'
    ds.PatientWeight = '0.0'
    ds.AdditionalPatientHistory = ''
    ds.ContrastBolusAgent = 'ISOVUE300/100'
    ds.ScanOptions = 'HELICAL MODE'
    ds.SliceThickness = '5.0'
    ds.KVP = '120.0'
    ds.SpacingBetweenSlices = '5.0'
    ds.DataCollectionDiameter = '480.0'
    ds.SoftwareVersions = '05'
    ds.ContrastBolusRoute = 'IV'
    ds.ReconstructionDiameter = '338.6716'
    ds.DistanceSourceToDetector = '1099.3100585938'
    ds.DistanceSourceToPatient = '630.0'
    ds.GantryDetectorTilt = '0.0'
    ds.TableHeight = '133.699997'
    ds.ExposureTime = '1601'
    ds.XRayTubeCurrent = '170'
    ds.Exposure = '170'
    ds.FilterType = 'LARGE BOWTIE FIL'
    ds.FocalSpots = '0.7'
    ds.ConvolutionKernel = 'STANDARD'
    ds.PatientPosition = 'FFS'
    ds.StudyInstanceUID = '1.3.6.1.4.1.5962.1.2.1.20040119072730.12322'
    ds.SeriesInstanceUID = '1.3.6.1.4.1.5962.1.3.1.1.20040119072730.12322'
    ds.StudyID = '1CT1'
    ds.SeriesNumber = '1'
    ds.AcquisitionNumber = '2'
    ds.InstanceNumber = '1'
    ds.ImagePositionPatient = [-158.135803, -179.035797, -75.699997]
    ds.ImageOrientationPatient = [1.000000, 0.000000, 0.000000, 0.000000, 1.000000, 0.000000]
    ds.FrameOfReferenceUID = '1.3.6.1.4.1.5962.1.4.1.1.20040119072730.12322'
    ds.Laterality = ''
    ds.PositionReferenceIndicator = 'SN'
    ds.SliceLocation = '-77.2040634155'
    ds.ImageComments = 'Uncompressed'
    ds.SamplesPerPixel = 3
    # ds.PhotometricInterpretation = 'RGB'
    ds.Rows = image.height
    ds.Columns = image.width
    # ds.PixelSpacing = [0.661468, 0.661468]
    ds.BitsAllocated = 8
    ds.BitsStored = 8
    ds.HighBit = 7
    ds.PixelRepresentation = 1
    ds.PixelPaddingValue = -2000
    ds.RescaleIntercept = '-1024.0'
    ds.RescaleSlope = '1.0'
    image_data = np.array(image.getdata(), dtype=np.uint8)[:, :3]
    # ds.PixelData = image_data.tobytes()
    # ds.DataSetTrailingPadding = # XXX Array of 126 bytes excluded

    ds.file_meta = file_meta
    ds.is_implicit_VR = False
    ds.is_little_endian = True
    return ds


if __name__ == '__main__':
    main()
