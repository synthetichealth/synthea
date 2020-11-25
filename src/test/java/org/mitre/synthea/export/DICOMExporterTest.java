package org.mitre.synthea.export;

import static org.mitre.synthea.TestHelper.timestamp;

import org.dcm4che2.data.DicomObject;
import org.dcm4che2.data.SpecificCharacterSet;
import org.dcm4che2.data.Tag;
import org.dcm4che2.io.DicomInputStream;
import org.junit.Test;
import org.mitre.synthea.world.agents.Person;

import java.io.File;
import java.io.IOException;
import java.net.URL;

import static org.junit.Assert.*;

public class DICOMExporterTest {

  @Test
  public void writeDICOMAttributes() throws IOException {
    Person p = new Person(1l);
    p.attributes.put(Person.FIRST_NAME, "John");
    p.attributes.put(Person.LAST_NAME, "Doe");
    p.attributes.put(Person.BIRTHDATE, timestamp(1978, 7, 7, 12, 0, 0));
    p.attributes.put(Person.GENDER, "M");
    URL dicomURL = getClass().getClassLoader().getResource(
        "dicom/subject-000.dcm");
    long studyStart = timestamp(2020, 1, 1, 12, 0, 0);
    String studyUID = "1.2.3.4.5.6";
    File output = File.createTempFile("test", "dcm");
    output.deleteOnExit();
    DICOMExporter.writeDICOMAttributes(studyUID, studyStart, p, dicomURL.getFile(), output.getAbsolutePath());
    try(DicomInputStream dis = new DicomInputStream(output)) {
      DicomObject dicomObject = dis.readDicomObject();
      assertEquals("John Doe", dicomObject.getString(Tag.PatientName));
    }
  }
}