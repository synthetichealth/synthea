package org.mitre.synthea.export;

import org.mitre.synthea.export.Exporter.ExporterRuntimeOptions;
import org.mitre.synthea.world.agents.Person;

/**
 * This interface defines an exporter that will be run for every patient
 * in a simulation.
 */
public interface PatientExporter {
  /**
   * Export the given Person object.
   * @param person   Patient to export
   * @param stopTime Time at which the simulation stopped
   * @param options Runtime exporter options
   */
  void export(Person person, long stopTime, ExporterRuntimeOptions options);
}
