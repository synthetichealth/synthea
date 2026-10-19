package org.mitre.synthea.export;

import java.util.ArrayList;
import java.util.List;

import org.hl7.fhir.r4.model.Bundle;
import org.hl7.fhir.r4.model.Bundle.BundleType;
import org.hl7.fhir.r4.model.CodeableConcept;
import org.hl7.fhir.r4.model.Observation;
import org.hl7.fhir.r4.model.Observation.ObservationStatus;
import org.mitre.synthea.engine.Components;
import org.mitre.synthea.helpers.TimeSeriesData;
import org.simulator.math.odes.MultiTable;

/**
 * Exports a standalone physiology simulation's time-series output as a FHIR R4
 * Bundle of Observations, each carrying a {@code SampledData} value.
 *
 * <p>This is the FHIR facade for the standalone ("generator" / hardware-simulation)
 * entry path. It intentionally reuses {@link FhirR4#mapValueToSampledData}, the same
 * mapping the in-framework (module {@code Physiology} state) path already uses to
 * export sampled observations, so both entry paths emit identical FHIR SampledData.
 */
public final class PhysiologySimulationExporter {

  private PhysiologySimulationExporter() {}

  /**
   * Builds a FHIR R4 Bundle (COLLECTION) of Observations from a simulation result.
   * One Observation is produced per non-constant output column; the "Time" column is
   * the sampling axis and is skipped, and constant columns are omitted because a flat
   * SampledData series carries no signal.
   *
   * @param results simulation output table (column 0 is "Time")
   * @param unit units-of-measure string applied to each Observation's SampledData
   * @return a FHIR Bundle of sampled Observations
   */
  public static Bundle toBundle(MultiTable results, String unit) {
    Bundle bundle = new Bundle();
    bundle.setType(BundleType.COLLECTION);

    int numRows = results.getRowCount();
    int numCols = results.getColumnCount();

    // Sampling period (seconds): spacing between the first two time points.
    double period = numRows > 1 ? results.getTimePoint(1) - results.getTimePoint(0) : 1.0;

    for (int col = 0; col < numCols; col++) {
      String name = results.getColumnIdentifier(col);
      if ("Time".equalsIgnoreCase(name)) {
        continue;
      }

      // Collect the column values, tracking whether the series actually varies.
      List<Double> values = new ArrayList<Double>(numRows);
      double min = Double.POSITIVE_INFINITY;
      double max = Double.NEGATIVE_INFINITY;
      for (int row = 0; row < numRows; row++) {
        double value = results.getValueAt(row, col);
        values.add(value);
        if (value < min) {
          min = value;
        }
        if (value > max) {
          max = value;
        }
      }

      // Skip constant columns; a flat series is not a meaningful signal.
      if (max - min <= 1e-9) {
        continue;
      }

      Components.SampledData sampled = new Components.SampledData();
      sampled.originValue = 0;
      sampled.series = new ArrayList<TimeSeriesData>(1);
      sampled.series.add(new TimeSeriesData(values, period));

      Observation observation = new Observation();
      observation.setStatus(ObservationStatus.FINAL);
      observation.setCode(new CodeableConcept().setText(name));
      observation.setValue(FhirR4.mapValueToSampledData(sampled, unit));

      bundle.addEntry().setResource(observation);
    }

    return bundle;
  }

  /**
   * Serializes {@link #toBundle} to a pretty-printed FHIR R4 JSON string.
   *
   * @param results simulation output table
   * @param unit units-of-measure string for each SampledData
   * @return FHIR R4 JSON for the Bundle
   */
  public static String toBundleJson(MultiTable results, String unit) {
    return FhirR4.getContext().newJsonParser().setPrettyPrint(true)
        .encodeResourceToString(toBundle(results, unit));
  }
}
