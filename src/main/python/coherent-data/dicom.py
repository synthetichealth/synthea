import numpy as np

import pydicom
from pydicom.dataset import Dataset, FileMetaDataset
from pydicom.sequence import Sequence
from pydicom.datadict import add_dict_entry

# Register the Synthetic data attribute since it's not in pydicom yet
# https://dicom.innolitics.com/ciods/photoacoustic-image/sop-common/0008001c
# values are YES and NO
add_dict_entry(tag=0x0008001C, VR="CS", keyword="SyntheticData", description="Synthetic Data Attribute")

# to create these templates based on an existing image,
# run `pydicom codify <path-to-source-dcm>` 

def create_dataset_common(image, imaging_study, context):
    ds = Dataset()
    ds.SyntheticData = 'YES'

    image_date = imaging_study['started'][0:10].replace('-', '')
    image_time = imaging_study['started'][11:19].replace(':', '') + '.000000'
    ds.StudyDate = image_date
    ds.SeriesDate = image_date
    ds.AcquisitionDate = image_date
    ds.ContentDate = image_date
    ds.AcquisitionDateTime = image_date + image_time
    ds.StudyTime = image_time
    ds.SeriesTime = image_time
    ds.AcquisitionTime = image_time
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

    ds.Rows = image.height
    ds.Columns = image.width
    ds.PixelData = image.tobytes()

    return ds


def create_oct_dicom(image, imaging_study, context):
    # File meta info data elements
    file_meta = FileMetaDataset()
    file_meta.FileMetaInformationGroupLength = 196
    file_meta.FileMetaInformationVersion = b'\x00\x01'
    file_meta.MediaStorageSOPClassUID = '1.2.840.10008.5.1.4.1.1.77.1.5.4'
    file_meta.MediaStorageSOPInstanceUID = '1.2.392.200106.1651.6.2.1803921148151.3911546542.17'
    file_meta.TransferSyntaxUID = '1.2.840.10008.1.2.1'
    file_meta.ImplementationClassUID = '1.2.392.200106.1651.6.2'
    file_meta.ImplementationVersionName = 'TP_STO_IM6_100'

    # Main data elements
    ds = create_dataset_common(image, imaging_study, context)
    ds.SpecificCharacterSet = 'ISO_IR 100'
    ds.ImageType = ['ORIGINAL', 'PRIMARY', '']
    ds.SOPClassUID = '1.2.840.10008.5.1.4.1.1.77.1.5.4'
    ds.SOPInstanceUID = '1.2.392.200106.1651.6.2.1803921148151.3911546542.17'

    ds.Modality = 'OPT'
    ds.Manufacturer = 'Topcon Healthcare'
    ds.InstitutionName = 'THINC'
    ds.ReferringPhysicianName = ''
    ds.StationName = 'VM'
    ds.SeriesDescription = 'OCT'
    ds.InstitutionalDepartmentName = 'GPM'
    ds.OperatorsName = 'admin'
    ds.ManufacturerModelName = 'Maestro2'

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
    ds.DateOfLastCalibration = '20220822'
    ds.TimeOfLastCalibration = '093900'
    ds.AcquisitionTimeSynchronized = 'N'
    ds.DetectorType = 'CCD'
    ds.AcquisitionDuration = 0.0

    ds.FrameOfReferenceUID = '1.2.392.200106.1651.6.2.1.20231214124222'
    ds.ImageLaterality = 'R'
    ds.SynchronizationFrameOfReferenceUID = '1.2.392.200106.1651.6.2.1803921148151.3911546542'
    ds.SOPInstanceUIDOfConcatenationSource = '1.2.392.200106.1651.6.2.1803921148151.45272.2.2'
    ds.PositionReferenceIndicator = ''
    ds.ConcatenationUID = '1.2.392.200106.1651.6.2.1803921148151.45272.2.2'
    ds.InConcatenationNumber = 1
    ds.InConcatenationTotalNumber = 1


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
    ds.SamplesPerPixel = 3
    ds.PhotometricInterpretation = 'RGB' # TODO: should be 'MONOCHROME2'
    ds.NumberOfFrames = '1'
    # ds.Rows = 885
    # ds.Columns = 512
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

    # Referenced Image Sequence
    refd_image_sequence = Sequence()
    shared_functional_groups1.ReferencedImageSequence = refd_image_sequence

    # Referenced Image Sequence: Referenced Image 1
    refd_image1 = Dataset()
    refd_image_sequence.append(refd_image1)
    refd_image1.ReferencedSOPClassUID = '1.2.840.10008.5.1.4.1.1.77.1.5.1'
    refd_image1.ReferencedSOPInstanceUID = '1.2.392.200106.1651.6.2.1803921148151.3911546542.14'

    # Purpose of Reference Code Sequence
    purpose_of_ref_code_sequence = Sequence()
    refd_image1.PurposeOfReferenceCodeSequence = purpose_of_ref_code_sequence

    # Purpose of Reference Code Sequence: Purpose of Reference Code 1
    purpose_of_ref_code1 = Dataset()
    purpose_of_ref_code_sequence.append(purpose_of_ref_code1)
    purpose_of_ref_code1.CodeValue = '121311'
    purpose_of_ref_code1.CodingSchemeDesignator = 'DCM'
    purpose_of_ref_code1.CodeMeaning = 'Localizer'


    # Derivation Image Sequence
    derivation_image_sequence = Sequence()
    shared_functional_groups1.DerivationImageSequence = derivation_image_sequence


    # Frame Anatomy Sequence
    frame_anatomy_sequence = Sequence()
    shared_functional_groups1.FrameAnatomySequence = frame_anatomy_sequence

    # Frame Anatomy Sequence: Frame Anatomy 1
    frame_anatomy1 = Dataset()
    frame_anatomy_sequence.append(frame_anatomy1)

    # Anatomic Region Sequence
    anatomic_region_sequence = Sequence()
    frame_anatomy1.AnatomicRegionSequence = anatomic_region_sequence

    # Anatomic Region Sequence: Anatomic Region 1
    anatomic_region1 = Dataset()
    anatomic_region_sequence.append(anatomic_region1)
    anatomic_region1.CodeValue = 'T-AA610'
    anatomic_region1.CodingSchemeDesignator = '1C SRT'
    anatomic_region1.CodeMeaning = 'Retina'

    frame_anatomy1.FrameLaterality = 'R'


    # Plane Orientation Sequence
    plane_orientation_sequence = Sequence()
    shared_functional_groups1.PlaneOrientationSequence = plane_orientation_sequence

    # Plane Orientation Sequence: Plane Orientation 1
    plane_orientation1 = Dataset()
    plane_orientation_sequence.append(plane_orientation1)
    plane_orientation1.ImageOrientationPatient = [1.000000, 0.000000, 0.000000, 0.000000, 1.000000, 0.000000]


    # Pixel Measures Sequence
    pixel_measures_sequence = Sequence()
    shared_functional_groups1.PixelMeasuresSequence = pixel_measures_sequence

    # Pixel Measures Sequence: Pixel Measures 1
    pixel_measures1 = Dataset()
    pixel_measures_sequence.append(pixel_measures1)
    pixel_measures1.SliceThickness = '0.0703125'
    pixel_measures1.PixelSpacing = [0.002600, 0.023438]


    # Per-frame Functional Groups Sequence
    per_frame_functional_groups_sequence = Sequence()
    ds.PerFrameFunctionalGroupsSequence = per_frame_functional_groups_sequence

    # Per-frame Functional Groups Sequence: Per-frame Functional Groups 1
    per_frame_functional_groups1 = Dataset()
    per_frame_functional_groups_sequence.append(per_frame_functional_groups1)

    # Frame Content Sequence
    frame_content_sequence = Sequence()
    per_frame_functional_groups1.FrameContentSequence = frame_content_sequence

    # Frame Content Sequence: Frame Content 1
    frame_content1 = Dataset()
    frame_content_sequence.append(frame_content1)
    frame_content1.FrameAcquisitionDateTime = ''
    frame_content1.FrameReferenceDateTime = ''
    frame_content1.FrameAcquisitionDuration = None
    frame_content1.StackID = '1'
    frame_content1.InStackPositionNumber = 1
    frame_content1.DimensionIndexValues = [1, 1]


    # Plane Position Sequence
    plane_position_sequence = Sequence()
    per_frame_functional_groups1.PlanePositionSequence = plane_position_sequence

    # Plane Position Sequence: Plane Position 1
    plane_position1 = Dataset()
    plane_position_sequence.append(plane_position1)
    plane_position1.ImagePositionPatient = None

    # Purpose of Reference Code Sequence
    purpose_of_ref_code_sequence = Sequence()
    plane_position1.PurposeOfReferenceCodeSequence = purpose_of_ref_code_sequence

    # Purpose of Reference Code Sequence: Purpose of Reference Code 1
    purpose_of_ref_code1 = Dataset()
    purpose_of_ref_code_sequence.append(purpose_of_ref_code1)
    purpose_of_ref_code1.CodeValue = '121311'
    purpose_of_ref_code1.CodingSchemeDesignator = 'DCM'
    purpose_of_ref_code1.CodeMeaning = 'Localizer'


    # Ophthalmic Frame Location Sequence
    ophthalmic_frame_location_sequence = Sequence()
    per_frame_functional_groups1.OphthalmicFrameLocationSequence = ophthalmic_frame_location_sequence

    # Ophthalmic Frame Location Sequence: Ophthalmic Frame Location 1
    ophthalmic_frame_location1 = Dataset()
    ophthalmic_frame_location_sequence.append(ophthalmic_frame_location1)
    ophthalmic_frame_location1.ReferencedSOPClassUID = '1.2.840.10008.5.1.4.1.1.77.1.5.1'
    ophthalmic_frame_location1.ReferencedSOPInstanceUID = '1.2.392.200106.1651.6.2.1803921148151.3911546542.14'
    ophthalmic_frame_location1.ReferenceCoordinates = [296.0, 371.0, 296.0, 2165.0]
    ophthalmic_frame_location1.OphthalmicImageOrientation = 'LINEAR'

    # Purpose of Reference Code Sequence
    purpose_of_ref_code_sequence = Sequence()
    ophthalmic_frame_location1.PurposeOfReferenceCodeSequence = purpose_of_ref_code_sequence

    # Purpose of Reference Code Sequence: Purpose of Reference Code 1
    purpose_of_ref_code1 = Dataset()
    purpose_of_ref_code_sequence.append(purpose_of_ref_code1)
    purpose_of_ref_code1.CodeValue = '121311'
    purpose_of_ref_code1.CodingSchemeDesignator = 'DCM'
    purpose_of_ref_code1.CodeMeaning = 'Localizer'

    # ds.file_meta = file_meta
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
    ds.SpecificCharacterSet = 'ISO_IR 100'
    ds.ImageType = ['ORIGINAL', 'PRIMARY', '3D Wide']
    ds.SOPClassUID = '1.2.840.10008.5.1.4.1.1.77.1.5.1'
    ds.SOPInstanceUID = '1.2.392.200106.1651.6.2.1803921148151.3911546542.14'

    ds.Modality = 'OP'
    ds.ConversionType = 'WSD'
    ds.Manufacturer = 'Topcon Healthcare'
    ds.InstitutionName = 'THINC'
    ds.ReferringPhysicianName = ''
    ds.SeriesDescription = 'Fundus'
    ds.ManufacturerModelName = 'Maestro2'

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
    ds.ImageLaterality = 'R'
    ds.SynchronizationFrameOfReferenceUID = '1.2.392.200106.1651.6.2.1803921148151.3911546542'
    ds.PatientEyeMovementCommanded = ''
    ds.EmmetropicMagnification = None
    ds.IntraOcularPressure = None
    ds.HorizontalFieldOfView = None
    ds.PupilDilated = ''

    # Acquisition Device Type Code Sequence
    acquisition_device_type_code_sequence = Sequence()
    ds.AcquisitionDeviceTypeCodeSequence = acquisition_device_type_code_sequence

    # Acquisition Device Type Code Sequence: Acquisition Device Type Code 1
    acquisition_device_type_code1 = Dataset()
    acquisition_device_type_code_sequence.append(acquisition_device_type_code1)
    acquisition_device_type_code1.CodeValue = 'R-1021A'
    acquisition_device_type_code1.CodingSchemeDesignator = 'SRT'
    acquisition_device_type_code1.CodeMeaning = 'Fundus Camera'

    ds.SamplesPerPixel = 3
    ds.PhotometricInterpretation = 'RGB'
    ds.PlanarConfiguration = 0
    ds.NumberOfFrames = '1'
    ds.FrameIncrementPointer = (0x0018, 0x1063)

    ds.PixelSpacing = [0, 0]
    ds.BitsAllocated = 8
    ds.BitsStored = 8
    ds.HighBit = 7
    ds.PixelRepresentation = 0
    ds.BurnedInAnnotation = 'NO'
    ds.LossyImageCompression = '00'
    
    # ds.file_meta = file_meta
    ds.is_implicit_VR = False
    ds.is_little_endian = True
    return ds