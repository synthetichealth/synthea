package org.mitre.synthea.export;

import org.dcm4che2.data.DicomObject;
import org.dcm4che2.data.Tag;
import org.dcm4che2.data.VR;
import org.dcm4che2.io.DicomInputStream;
import org.dcm4che2.io.DicomOutputStream;
import org.mitre.synthea.world.agents.Person;

import java.io.File;
import java.io.IOException;
import java.nio.file.Path;
import java.util.Date;

public class DICOMExporter {
  public static void writeDICOMAttributes(String studyUID, long studyStart, Person person,
                                          String originalFileLocation,
                                          String targetExportLocation) throws IOException {
    File dicomFile = new File(originalFileLocation);
    try(DicomInputStream dis = new DicomInputStream(dicomFile)) {
      DicomObject dicomObject = dis.readDicomObject();
      String firstName = (String) person.attributes.get(Person.FIRST_NAME);
      String lastName = (String) person.attributes.get(Person.LAST_NAME);
      String fullName = String.format("%s %s", firstName, lastName);
      dicomObject.putString(Tag.PatientName, VR.ST, fullName);
      dicomObject.putString(Tag.PatientSex, VR.CS, (String) person.attributes.get(Person.GENDER));
      dicomObject.putDate(Tag.PatientBirthDate, VR.DA, new Date((long) person.attributes.get(Person.BIRTHDATE)));
      dicomObject.putString(Tag.StudyInstanceUID, VR.UI, studyUID);
      dicomObject.putDate(Tag.StudyDate, VR.DA, new Date(studyStart));

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
