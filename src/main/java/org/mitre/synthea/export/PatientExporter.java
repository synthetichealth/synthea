package org.mitre.synthea.export;

import org.mitre.synthea.export.Exporter.ExporterRuntimeOptions;
import org.mitre.synthea.world.agents.Person;

/**
 * 
 *
 */
public interface PatientExporter {

  /**
   * 
   * @param person   Patient to export
   * @param stopTime Time at which the simulation stopped
   * @param options Runtime exporter options
   */
  void export(Person person, long stopTime, ExporterRuntimeOptions options);
}
