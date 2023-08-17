package org.mitre.synthea.export;

import org.mitre.synthea.engine.Generator;
import org.mitre.synthea.export.Exporter.ExporterRuntimeOptions;

/**
 * This interface defines an exporter that will be invoked
 * after the entire population has been generated.
 * Example uses for this type of exporter include metadata,
 * Payer- or Provider-based data, or working in combination with a separate
 * PatientExporter where a final step is only performed once every patient
 * has been handled.
 */
public interface PostCompletionExporter {
  /**
   * Export data based on the given Generator and options.
   */
  void export(Generator generator, ExporterRuntimeOptions options);
}
