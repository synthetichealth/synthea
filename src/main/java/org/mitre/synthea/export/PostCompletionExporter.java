package org.mitre.synthea.export;

import org.mitre.synthea.engine.Generator;
import org.mitre.synthea.export.Exporter.ExporterRuntimeOptions;

/**
 * 
 *
 */
public interface PostCompletionExporter {
  /**
   * 
   * @param generator
   * @param options
   */
  void export(Generator generator, ExporterRuntimeOptions options);
}
