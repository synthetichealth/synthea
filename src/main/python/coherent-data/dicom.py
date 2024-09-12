import numpy as np
import json
import pydicom
from pydicom.dataset import Dataset, FileMetaDataset
from pydicom.sequence import Sequence
from pydicom.datadict import add_dict_entry

# Register the Synthetic data attribute since it's not in pydicom yet
# https://dicom.innolitics.com/ciods/photoacoustic-image/sop-common/0008001c
# values are YES and NO
add_dict_entry(tag=0x0008001C, VR="CS", keyword="SyntheticData", description="Synthetic Data Attribute")


def uid(category, obj):
    # https://stackoverflow.com/a/46316162
    # B.1 Organizationally Derived UID:
    # The following example presents a particular choice made by a specific organization in defining its suffix to guarantee uniqueness of a SOP Instance UID.
    # "1.2.840.xxxxx.3.152.235.2.12.187636473"
    # In this example, the root is:
    #     1 Identifies ISO
    #     2 Identifies ANSI Member Body
    #     840 Country code of a specific Member Body (U.S. for ANSI)
    #     xxxxx Identifies a specific Organization.(assigned by ANSI)
    # In this example the first two components of the suffix relate to the identification of the device:
    #     3 Manufacturer defined device type
    #     152 Manufacturer defined serial number
    # The remaining four components of the suffix relate to the identification of the image:
    #     235 Study number
    #     2 Series number
    #     12 Image number
    #     187636473 Encoded date and time stamp of image acquisition

    # we use the hash() function just to get unique-enough numbers

    return f"1.2.840.99999999.{abs(hash(category))}.{abs(hash(json.dumps(obj, sort_keys=True)))}"

# to create these templates based on an existing image,
# run `pydicom codify <path-to-source-dcm>` 

def create_dataset_common(image, imaging_study, context):
    ds = Dataset()
    ds.SyntheticData = 'YES'

    image_date = imaging_study['started'][0:10].replace('-', '')
    image_time = imaging_study['started'][11:19].replace(':', '') + '.000000'
    ds.StudyDate = image_date
    ds.SeriesDate = image_date
    # ds.AcquisitionDate = image_date
    ds.ContentDate = image_date
    ds.AcquisitionDateTime = image_date + image_time
    ds.StudyTime = image_time
    ds.SeriesTime = image_time
    # ds.AcquisitionTime = image_time
    ds.ContentTime = image_time

    patient = context['patient']
    name_obj = patient['name'][0]
    ds.PatientName = f"{name_obj['family']}^{name_obj['given'][0]}"
    ds.PatientID = patient['id']
    ds.PatientBirthDate = patient['birthDate'].replace('-', '')
    ds.PatientSex = patient['gender'][0].upper()

    ds.StudyInstanceUID = imaging_study['series'][0]['uid']
    ds.SeriesInstanceUID = context['instance']['uid']
    ds.StudyID = '1'
    ds.SeriesNumber = '1'
    ds.AcquisitionNumber = '1'
    ds.InstanceNumber = str(context['instance']['number'])
    laterality = 'L' if context['laterality'] == 'OS' else 'R'
    ds.ImageLaterality = laterality
    ds.AccessionNumber = ''

    ds.PupilDilated = 'YES'
    ds.MydriaticAgentSequence = None
    ds.DegreeOfDilation = None
    ds.IntraOcularPressure = None
    ds.HorizontalFieldOfView = None
    ds.EmmetropicMagnification = None
    ds.RefractiveStateSequence = None

    ds.Manufacturer = 'GenericMfr'
    ds.InstitutionName = 'Generic'
    ds.ReferringPhysicianName = ''
    ds.StationName = 'VM'
    ds.InstitutionalDepartmentName = 'GPM'
    ds.OperatorsName = 'admin'
    ds.ManufacturerModelName = 'GenericDevice'

    ds.Rows = image.height
    ds.Columns = image.width
    if context['type'] == 'OCT':
        ds.SamplesPerPixel = 1
        ds.PhotometricInterpretation = 'MONOCHROME2'
        np_image = np.array(image.getdata(), dtype=np.uint8)[:,0]
        ds.PixelData = np_image.tobytes()
    else:
        ds.SamplesPerPixel = 3
        ds.PhotometricInterpretation = 'RGB'
        ds.PixelData = image.tobytes()

    return ds


def create_oct_dicom(image, imaging_study, context):
    # File meta info data elements
    file_meta = FileMetaDataset()
    file_meta.FileMetaInformationGroupLength = 196
    file_meta.FileMetaInformationVersion = b'\x00\x01'
    file_meta.MediaStorageSOPClassUID = '1.2.840.10008.5.1.4.1.1.77.1.5.4'
    file_meta.MediaStorageSOPInstanceUID = uid("MediaStorageSOPInstanceUID", imaging_study)
    file_meta.TransferSyntaxUID = '1.2.840.10008.1.2.1'
    file_meta.ImplementationClassUID = '1.2.392.200106.1651.6.2'
    file_meta.ImplementationVersionName = 'TP_STO_IM6_100'

    # Main data elements
    ds = create_dataset_common(image, imaging_study, context)
    ds.file_meta = file_meta
    ds.SpecificCharacterSet = 'ISO_IR 100'
    ds.ImageType = ['ORIGINAL', 'PRIMARY', '']
    ds.SOPClassUID = '1.2.840.10008.5.1.4.1.1.77.1.5.4'
    ds.SOPInstanceUID = '1.2.392.200106.1651.6.2.1803921148151.3911546542.17'

    ds.LightPathFilterTypeStackCodeSequence = None
    ds.AcquisitionContextSequence = None

    ds.Modality = 'OPT'
    ds.SeriesDescription = 'OCT'

    # Anatomic Region Sequence
    anatomic_region_sequence = Sequence()
    ds.AnatomicRegionSequence = anatomic_region_sequence

    # Anatomic Region Sequence: Anatomic Region 1
    anatomic_region1 = Dataset()
    anatomic_region_sequence.append(anatomic_region1)
    anatomic_region1.CodeValue = 'T-AA610'
    anatomic_region1.CodingSchemeDesignator = 'SRT'
    anatomic_region1.CodeMeaning = 'Retina'


    ds.DeviceSerialNumber = '3070395'
    ds.SoftwareVersions = '2.54.24153'
    ds.SynchronizationTrigger = 'NO TRIGGER'
    ds.DateOfLastCalibration = '20200822'
    ds.TimeOfLastCalibration = '093900'
    ds.AcquisitionTimeSynchronized = 'N'
    ds.DetectorType = 'CCD'
    ds.AcquisitionDuration = 0.0

    ds.FrameOfReferenceUID = uid("FrameOfReferenceUID", imaging_study)

    ds.SynchronizationFrameOfReferenceUID = uid("SynchronizationFrameOfReferenceUID", imaging_study)
    ds.SOPInstanceUIDOfConcatenationSource = uid("SOPInstanceUIDOfConcatenationSource", imaging_study)
    ds.PositionReferenceIndicator = ''
    ds.ConcatenationUID = uid("ConcatenationUID", imaging_study)
    ds.ConcatenationFrameOffsetNumber = 0
    ds.InConcatenationNumber = 1
    ds.InConcatenationTotalNumber = 1

    ds.PresentationLUTShape = 'IDENTITY'

    # Acquisition Device Type Code Sequence
    acquisition_device_type_code_sequence = Sequence()
    ds.AcquisitionDeviceTypeCodeSequence = acquisition_device_type_code_sequence

    # Acquisition Device Type Code Sequence: Acquisition Device Type Code 1
    acquisition_device_type_code1 = Dataset()
    acquisition_device_type_code_sequence.append(acquisition_device_type_code1)
    acquisition_device_type_code1.CodeValue = 'A-00FBE'
    acquisition_device_type_code1.CodingSchemeDesignator = 'SRT'
    acquisition_device_type_code1.CodeMeaning = 'Optical Coherence Tomography Scanner'

    ds.AxialLengthOfTheEye = None
    ds.DepthSpatialResolution = 6.0
    ds.MaximumDepthDistortion = 0.5
    ds.AlongScanSpatialResolution = 20.0
    ds.MaximumAlongScanDistortion = 0.5
    ds.AcrossScanSpatialResolution = 20.0
    ds.MaximumAcrossScanDistortion = 0.5
    ds.IlluminationWaveLength = 840.0
    ds.IlluminationPower = 650.0
    ds.IlluminationBandwidth = 50.0
    ds.NumberOfFrames = '1'
    ds.BitsAllocated = 8
    ds.BitsStored = 8
    ds.HighBit = 7
    ds.PixelRepresentation = 0
    ds.BurnedInAnnotation = 'NO'
    ds.LossyImageCompression = '00'
    ds.RepresentativeFrameNumber = 1

    # Shared Functional Groups Sequence
    shared_functional_groups_sequence = Sequence()
    ds.SharedFunctionalGroupsSequence = shared_functional_groups_sequence

    # Shared Functional Groups Sequence: Shared Functional Groups 1
    shared_functional_groups1 = Dataset()
    shared_functional_groups_sequence.append(shared_functional_groups1)

    # Per-frame Functional Groups Sequence
    per_frame_functional_groups_sequence = Sequence()
    ds.PerFrameFunctionalGroupsSequence = per_frame_functional_groups_sequence

    # Per-frame Functional Groups Sequence: Per-frame Functional Groups 1
    per_frame_functional_groups1 = Dataset()
    per_frame_functional_groups_sequence.append(per_frame_functional_groups1)


    # Dimension Organization Sequence
    dimension_organization_sequence = Sequence()
    ds.DimensionOrganizationSequence = dimension_organization_sequence

    ds.DimensionOrganizationType = '3D'

    # Dimension Organization Sequence: Dimension Organization 1
    dimension_organization1 = Dataset()
    dimension_organization_sequence.append(dimension_organization1)
    dimension_organization1.DimensionOrganizationUID = uid("DimensionOrganizationUID", imaging_study)

    
    ds.is_implicit_VR = False
    ds.is_little_endian = True

    return ds


def create_fundus_dicom(image, imaging_study, context):
    # File meta info data elements
    file_meta = FileMetaDataset()
    file_meta.FileMetaInformationGroupLength = 196
    file_meta.FileMetaInformationVersion = b'\x00\x01'
    file_meta.MediaStorageSOPClassUID = '1.2.840.10008.5.1.4.1.1.77.1.5.1'
    file_meta.MediaStorageSOPInstanceUID = '1.2.392.200106.1651.6.2.1803921148151.3911546542.14'
    file_meta.TransferSyntaxUID = '1.2.840.10008.1.2.1'
    file_meta.ImplementationClassUID = '1.2.392.200106.1651.6.2'
    file_meta.ImplementationVersionName = 'TP_STO_IM6_100'


    # Main data elements
    ds = create_dataset_common(image, imaging_study, context)
    ds.file_meta = file_meta
    ds.SpecificCharacterSet = 'ISO_IR 100'
    ds.ImageType = ['ORIGINAL', 'PRIMARY', 'COLOR']
    ds.SOPClassUID = '1.2.840.10008.5.1.4.1.1.77.1.5.1'
    ds.SOPInstanceUID = '1.2.392.200106.1651.6.2.1803921148151.3911546542.14'

    ds.Modality = 'OP'
    ds.SeriesDescription = 'Fundus'

    ds.IlluminationTypeCodeSequence = None
    ds.LightPathFilterTypeStackCodeSequence = None
    ds.ImagePathFilterTypeStackCodeSequence = None
    ds.LensesCodeSequence = None

    # Anatomic Region Sequence
    anatomic_region_sequence = Sequence()
    ds.AnatomicRegionSequence = anatomic_region_sequence

    # Anatomic Region Sequence: Anatomic Region 1
    anatomic_region1 = Dataset()
    anatomic_region_sequence.append(anatomic_region1)
    anatomic_region1.CodeValue = '5665001'
    anatomic_region1.CodingSchemeDesignator = 'SCT'
    anatomic_region1.CodeMeaning = 'Retina'


    ds.DeviceSerialNumber = '3070395'
    ds.SoftwareVersions = '2.54.24153'
    ds.FrameTime = '0.0'
    ds.SynchronizationTrigger = 'NO TRIGGER'
    ds.DateOfLastCalibration = '20220822'
    ds.TimeOfLastCalibration = '093900'
    ds.AcquisitionTimeSynchronized = 'N'

    ds.PatientOrientation = ['L', 'F']
    ds.SynchronizationFrameOfReferenceUID = uid("SynchronizationFrameOfReferenceUID", imaging_study)
    ds.PatientEyeMovementCommanded = 'NO'
    ds.DetectorType = ''
    # Acquisition Device Type Code Sequence
    acquisition_device_type_code_sequence = Sequence()
    ds.AcquisitionDeviceTypeCodeSequence = acquisition_device_type_code_sequence

    # Acquisition Device Type Code Sequence: Acquisition Device Type Code 1
    acquisition_device_type_code1 = Dataset()
    acquisition_device_type_code_sequence.append(acquisition_device_type_code1)
    acquisition_device_type_code1.CodeValue = 'R-1021A'
    acquisition_device_type_code1.CodingSchemeDesignator = 'SRT'
    acquisition_device_type_code1.CodeMeaning = 'Fundus Camera'


    ds.PlanarConfiguration = 0
    ds.NumberOfFrames = '1'
    ds.FrameIncrementPointer = (0x0018, 0x1063)

    ds.BitsAllocated = 8
    ds.BitsStored = 8
    ds.HighBit = 7
    ds.PixelRepresentation = 0
    ds.BurnedInAnnotation = 'NO'
    ds.LossyImageCompression = '00'
    
    ds.is_implicit_VR = False
    ds.is_little_endian = True
    return ds