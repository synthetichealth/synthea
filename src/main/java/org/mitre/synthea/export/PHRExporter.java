// src/main/java/org/mitre/synthea/export/PHRExporter.java

package org.mitre.synthea.export;

import ca.uhn.fhir.parser.IParser;

import java.io.File;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

import org.hl7.fhir.r4.model.Bundle;
import org.hl7.fhir.r4.model.Bundle.BundleEntryComponent;

import org.mitre.synthea.helpers.Config;
import org.mitre.synthea.world.agents.Person;

/**
 * Exporter for PHR (Personal Health Record) format.
 * Exports patient data as heterogeneous NDJSON files, where each file contains
 * all FHIR resources for a single patient, with one resource per line.
 * This format is similar to FHIR Bundles but in newline-delimited JSON format.
 */
public class PHRExporter {

  /**
   * Export a single patient's data in PHR format.
   * Creates a heterogeneous NDJSON file containing all FHIR resources for the patient.
   *
   * @param person   Patient to export
   * @param stopTime Time at which the simulation stopped
   */
  public static void export(Person person, long stopTime) {
    // Generate the complete FHIR Bundle for this patient
    Bundle bundle = FhirR4.convertToFHIR(person, stopTime);
    
    // Create JSON parser for compact output (no pretty printing)
    IParser parser = FhirR4.getContext().newJsonParser().setPrettyPrint(false);
    
    // Determine output directory and file path
    File outDirectory = Exporter.getOutputFolder("phr", person);
    Path outFilePath = outDirectory.toPath().resolve(Exporter.filename(person, "", "phr"));
    
    // Build the complete NDJSON content
    List<String> ndjsonLines = new ArrayList<>();
    for (BundleEntryComponent entry : bundle.getEntry()) {
      // Convert each resource to JSON string
      String resourceJson = parser.encodeResourceToString(entry.getResource());
      ndjsonLines.add(resourceJson);
    }
    
    // Join all lines with newlines and write as a single file
    String phrContent = String.join("\n", ndjsonLines);
    Exporter.overwriteFile(outFilePath, phrContent);
  }
}