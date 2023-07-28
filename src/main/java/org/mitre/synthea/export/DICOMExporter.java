package org.mitre.synthea.export;

import org.dcm4che2.data.DicomObject;
import org.dcm4che2.data.Tag;
import org.dcm4che2.data.VR;
import org.dcm4che2.io.DicomInputStream;
import org.dcm4che2.io.DicomOutputStream;
import org.mitre.synthea.world.agents.Clinician;
import org.mitre.synthea.world.agents.Person;
import org.mitre.synthea.world.concepts.HealthRecord;
import org.mitre.synthea.world.concepts.VitalSign;

import java.io.File;
import java.io.IOException;
import java.nio.file.Path;
import java.util.Date;

public class DICOMExporter {
  public static void writeDICOMAttributes(HealthRecord.ImagingStudy is,
                                          HealthRecord.Encounter encounter,
                                          Person person,
                                          String originalFileLocation,
                                          String targetExportLocation) throws IOException {
    File dicomFile = new File(originalFileLocation);
    try(DicomInputStream dis = new DicomInputStream(dicomFile)) {
      DicomObject dicomObject = dis.readDicomObject();
      String firstName = (String) person.attributes.get(Person.FIRST_NAME);
      String lastName = (String) person.attributes.get(Person.LAST_NAME);
      String fullName = String.format("%s %s", firstName, lastName);
      dicomObject.putString(Tag.PatientName, VR.PN, fullName);
      dicomObject.putString(Tag.PatientSex, VR.CS, (String) person.attributes.get(Person.GENDER));
      dicomObject.putDate(Tag.PatientBirthDate, VR.DA, new Date((long) person.attributes.get(Person.BIRTHDATE)));
      dicomObject.putString(Tag.StudyInstanceUID, VR.UI, is.dicomUid);
      dicomObject.putString(Tag.SOPInstanceUID, VR.UI, is.series.get(0).instances.get(0).dicomUid);
      dicomObject.putString(Tag.SeriesInstanceUID, VR.UI, is.series.get(0).dicomUid);
      dicomObject.putDate(Tag.StudyDate, VR.DA, new Date(is.start));
      dicomObject.putDate(Tag.SeriesDate, VR.DA, new Date(is.start));
      dicomObject.putDate(Tag.AcquisitionDate, VR.DA, new Date(is.start));
      dicomObject.putDate(Tag.ContentDate, VR.DA, new Date(is.start));

      Clinician clinician = encounter.clinician;
      String clinicianFirstName = (String) clinician.attributes.get(Clinician.FIRST_NAME);
      String clinicianLastName = (String) clinician.attributes.get(Clinician.LAST_NAME);
      String clinicianFullName = String.format("%s %s", clinicianFirstName, clinicianLastName);
      dicomObject.putString(Tag.ReferringPhysicianName, VR.PN, clinicianFullName);

      Double weight = person.getVitalSign(VitalSign.WEIGHT, is.start);
      dicomObject.putString(Tag.PatientWeight, VR.DS, weight.toString());
      // Fix the image class to XRay
      dicomObject.putString(Tag.SOPClassUID, VR.UI, "1.2.840.10008.5.1.4.1.1.1.1");
      dicomObject.putString(Tag.PatientID, VR.LO, (String) person.attributes.get(Person.ID));
      // Fix the modality to XRay
      dicomObject.putString(Tag.Modality, VR.CS, "RG");

      File outputFile = new File(targetExportLocation);
      try(DicomOutputStream dos = new DicomOutputStream(outputFile)) {
        dos.writeDicomFile(dicomObject);
      }
    }
  }

  public static String outputDICOMFile(Person person, String dicomUID) {
    File outDir = Exporter.getOutputFolder("dicom", person);
    Path outFilePath = outDir.toPath().resolve(Exporter.filename(person, dicomUID, "dcm"));
    return outFilePath.toString();
  }
}