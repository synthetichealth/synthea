package org.mitre.synthea.helpers;

import com.google.common.io.Resources;
import org.junit.Before;
import org.junit.Test;
import org.mitre.synthea.world.agents.Person;

import java.io.IOException;
import java.lang.reflect.InvocationTargetException;
import java.net.URL;

public class DICOMFileSelectorTest {

  @Before
  public void setup() throws IllegalAccessException, NoSuchMethodException, InvocationTargetException {
    URL dicomDirectory = Resources.getResource("dicom_selector");
    Config.set("dicom.directory", dicomDirectory.getPath());
  }

  @Test
  public void selectRandomDICOMFile() throws IOException {
    Person p = new Person(1);
    String filename = DICOMFileSelector.selectRandomDICOMFile(p);
    assert filename.contains("test") && filename.contains(".dcm");
  }

  @Test
  public void filesRemain() throws IOException {
    assert DICOMFileSelector.filesRemain();
  }
}