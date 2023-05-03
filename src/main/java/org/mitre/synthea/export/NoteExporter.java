package org.mitre.synthea.export;

import org.mitre.synthea.helpers.Config;
import org.mitre.synthea.world.agents.Person;
import org.mitre.synthea.world.concepts.HealthRecord;

import java.util.concurrent.ExecutionException;

public class NoteExporter {
  /**
   * Export a clinical note for a Person at a given Encounter.
   * By default, it will use the ClinicalNoteExporter. If configured, it will use the
   * ChatGPTNoteExporter. Even if the ChatGPTNoteExporter, it will only be used if the provided
   * Encounter has a reason and the person has active symptoms
   *
   * @param person Person to write a note about.
   * @param encounter Encounter to write a note about.
   * @return Clinical note as a plain text string.
   */
  public static String export(Person person, HealthRecord.Encounter encounter) {
    String note = null;
    if (Config.getAsBoolean("openai.enabled", false)) {
      if(!person.getSymptoms().isEmpty() && encounter.reason != null) {
        try {
          note = ChatGPTNoteExporter.export(person, encounter);
        } catch (InterruptedException | ExecutionException e) {
          System.out.println("Unable to generate ChatGPT note. Using regular exporter instead");
          e.printStackTrace();
        }
      }
    }
    if (note == null) {
      note = ClinicalNoteExporter.export(person, encounter);
    }
    return note;
  }
}
