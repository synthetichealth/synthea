package org.mitre.synthea.editors;

import org.mitre.synthea.engine.HealthRecordEditor;
import org.mitre.synthea.export.DICOMExporter;
import org.mitre.synthea.helpers.DICOMFileSelector;
import org.mitre.synthea.world.agents.Person;
import org.mitre.synthea.world.concepts.HealthRecord;

import java.io.IOException;
import java.util.List;
import java.util.stream.Collectors;

public class MicrobleedEditor implements HealthRecordEditor {
  // strokes can happen at any time in Synthea, but the imaging that we have is more for
  // adults than children
  public static int MIN_AGE = 15;
  public static String BRAIN_MRI = "241601008";
  public static String MICROBLEED = "723857007";

  @Override
  public boolean shouldRun(Person person, HealthRecord record, long time) {
    return person.ageInYears(time) <= MIN_AGE;
  }

  @Override
  public void process(Person person, List<HealthRecord.Encounter> encounters, long time) {
    List<HealthRecord.Encounter> mriEncounters = encountersWithImagingStudiesOfCode(encounters, BRAIN_MRI);
    List<HealthRecord.Encounter> mriAndMicrobleedEncounters = encountersWithConditionsOfCode(mriEncounters, MICROBLEED);
    mriAndMicrobleedEncounters.forEach(encounter -> {
      HealthRecord.ImagingStudy is = encounter.imagingStudies.get(0);
      try {
        String sourceFile = DICOMFileSelector.selectRandomDICOMFile(person);
        String targetFile = DICOMExporter.outputDICOMFile(person, is.dicomUid);
        DICOMExporter.writeDICOMAttributes(is.dicomUid, is.start, person, sourceFile, targetFile);
      } catch (IOException e) {
        System.out.println("Unable to write DICOM file for microbleed");
        e.printStackTrace();
      }
    });
  }

  /**
   * Filter a list of encounters to find all that have an ImagingStudy with a particular code.
   * @param encounters The list to filter
   * @param code The code to look for
   * @return The filtered list. If there are no matching encounters, then an empty list.
   */
  public List<HealthRecord.Encounter> encountersWithImagingStudiesOfCode(
      List<HealthRecord.Encounter> encounters,
      String code) {
    return encounters.stream().filter(e -> {
      return e.imagingStudies.stream().anyMatch(imagingStudy -> {
        return imagingStudy.codes.stream().anyMatch(c -> code.equals(c.code));
      });
    }).collect(Collectors.toList());
  }

  /**
   * Filter a list of encounters to find all that have an condition with a particular code.
   * @param encounters The list to filter
   * @param code The code to look for
   * @return The filtered list. If there are no matching encounters, then an empty list.
   */
  public List<HealthRecord.Encounter> encountersWithConditionsOfCode(
      List<HealthRecord.Encounter> encounters,
      String code) {
    return encounters.stream().filter(e -> {
      return e.conditions.stream().anyMatch(condition -> {
        return condition.codes.stream().anyMatch(c -> code.equals(c.code));
      });
    }).collect(Collectors.toList());
  }
}
