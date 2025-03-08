package org.mitre.synthea.export;

import java.text.DecimalFormat;
import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.temporal.TemporalAdjusters;
import java.util.Arrays;
import java.util.Calendar;
import java.util.Date;
import java.util.List;
import java.util.Locale;
import java.util.TimeZone;
import java.util.UUID;

import org.apache.commons.lang3.time.FastDateFormat;
import org.hl7.fhir.dstu3.model.Condition;
import org.mitre.synthea.engine.Components.Attachment;
import org.mitre.synthea.engine.Components.SampledData;
import org.mitre.synthea.helpers.TimeSeriesData;
import org.mitre.synthea.world.agents.Clinician;
import org.mitre.synthea.world.agents.Person;
import org.mitre.synthea.world.concepts.HealthRecord;
import org.mitre.synthea.world.concepts.HealthRecord.Code;
import org.mitre.synthea.world.concepts.HealthRecord.Observation;

/**
 * Helper class for common logic that is used by more than one otherwise unrelated exporter.
 */
public abstract class ExportHelper {


  /**
   * Helper to get a readable string representation of an Observation's value.
   * Units are not included.
   *
   * @param observation The observation to get the value from
   * @return A human-readable string representation of observation.value
   */
  public static String getObservationValue(Observation observation) {
    String value = null;

    if (observation.value instanceof Condition) {
      Code conditionCode = ((HealthRecord.Entry)observation.value).codes.get(0);
      value = conditionCode.display;
    } else if (observation.value instanceof Code) {
      value = ((Code)observation.value).display;
    } else if (observation.value instanceof String) {
      value = (String)observation.value;
    } else if (observation.value instanceof Double) {
      // round to 1 decimal place for display
      value = String.format(Locale.US, "%.1f", observation.value);
    } else if (observation.value instanceof SampledData) {
      value = sampledDataToValueString((SampledData) observation.value);
    } else if (observation.value instanceof Attachment) {
      value = attachmentToValueString((Attachment) observation.value);
    } else if (observation.value != null) {
      value = observation.value.toString();
    }

    return value;
  }

  /**
   * Helper to get a readable string representation of an Observation's value.
   * Units are not included.
   *
   * @param observation The observation to get the value from.
   * @param code The observation or component observation matching this code.
   * @return A human-readable string representation of observation with the given code.
   */
  public static String getObservationValue(Observation observation, String code) {
    // Check whether this observation has the desired code.
    for (Code c : observation.codes) {
      if (c.code.equals(code)) {
        return getObservationValue(observation);
      }
    }

    // Check whether any of the contained observations have the desired code.
    String value = null;
    for (Observation o : observation.observations) {
      value = getObservationValue(o, code);
      if (value != null) {
        return value;
      }
    }

    return null;
  }

  /**
   * Helper to translate all SampledData values into string form.
   *
   * @param sampledData The SampledData object to export
   * @return stringified sampled data values
   */
  public static String sampledDataToValueString(SampledData sampledData) {
    int numSamples = sampledData.series.get(0).getValues().size();
    DecimalFormat df;

    if (sampledData.decimalFormat != null) {
      df = new DecimalFormat(sampledData.decimalFormat);
    } else {
      df = new DecimalFormat();
    }

    // Build the data string from all list values
    StringBuilder sb = new StringBuilder();
    for (int i = 0; i < numSamples; i++) {
      for (TimeSeriesData series : sampledData.series) {
        double num = series.getValues().get(i);
        sb.append(df.format(num));
        sb.append(" ");
      }
    }

    return sb.toString().trim();
  }

  /**
   * Helper to translate all Attachment values into string form.
   *
   * @param attachment The Attachment object to export
   * @return stringified Attachment data
   */
  public static String attachmentToValueString(Attachment attachment) {
    if (attachment.data != null) {
      return attachment.data;
    }
    if (attachment.url != null) {
      return attachment.url;
    }
    return "";
  }

  /**
   * Helper to get a readable string representation of an Observation's type.
   *
   * @param observation The observation to get the type from
   * @return A human-readable string representation of the type of observation.value
   */
  public static String getObservationType(Observation observation) {
    String type = null;

    if (observation.value instanceof Condition) {
      type = "text";
    } else if (observation.value instanceof Code) {
      type = "text";
    } else if (observation.value instanceof String) {
      type = "text";
    } else if (observation.value instanceof Double) {
      type = "numeric";
    } else if (observation.value instanceof Boolean) {
      type = "boolean";
    } else if (observation.value != null) {
      type = "text";
    }

    return type;
  }

  /**
   * Year-Month-Day date format.
   */
  private static final FastDateFormat DATE_FORMAT = FastDateFormat.getInstance("yyyy-MM-dd");

  /**
   * Iso8601 date time format.
   */
  private static final FastDateFormat ISO_DATE_FORMAT = iso();

  /**
   * Create a FastDateFormat for iso8601.
   * @return Iso8601 date time format.
   */
  private static final FastDateFormat iso() {
    FastDateFormat f = FastDateFormat.getInstance("yyyy-MM-dd'T'HH:mm:ss'Z'",
        TimeZone.getTimeZone("UTC"));
    return f;
  }

  /**
   * Get a date string in the format YYYY-MM-DD from the given time stamp.
   */
  public static String dateFromTimestamp(long time) {
    return DATE_FORMAT.format(new Date(time));
  }

  /**
   * Get an iso8601 string for the given time stamp.
   */
  public static String iso8601Timestamp(long time) {
    return ISO_DATE_FORMAT.format(new Date(time));
  }

  /**
   * Get the timestamp for next Friday.
   */
  public static long nextFriday(long time) {
    Calendar c = Calendar.getInstance(TimeZone.getTimeZone("UTC"));
    c.setTimeInMillis(time);
    LocalDate d = LocalDate.of(
        c.get(Calendar.YEAR), 1 + c.get(Calendar.MONTH), c.get(Calendar.DAY_OF_MONTH));
    d = d.with(TemporalAdjusters.next(DayOfWeek.FRIDAY));
    c.set(Calendar.YEAR, d.getYear());
    c.set(Calendar.MONTH, d.getMonthValue() - 1);
    c.set(Calendar.DAY_OF_MONTH, d.getDayOfMonth());
    return c.getTimeInMillis();
  }

  private static final String SNOMED_URI = "http://snomed.info/sct";
  private static final String LOINC_URI = "http://loinc.org";
  private static final String RXNORM_URI = "http://www.nlm.nih.gov/research/umls/rxnorm";
  private static final String CVX_URI = "http://hl7.org/fhir/sid/cvx";
  private static final String DICOM_DCM_URI = "http://dicom.nema.org/medical/dicom/current/output/chtml/part16/sect_CID_29.html";
  private static final String CDT_URI = "http://www.ada.org/cdt";
  private static final String ICD9_URI = "http://hl7.org/fhir/sid/icd-9-cm";
  private static final String ICD10_URI = "http://hl7.org/fhir/sid/icd-10";
  private static final String ICD10_CM_URI = "http://hl7.org/fhir/sid/icd-10-cm";

  /**
   * Translate the system name (e.g. SNOMED-CT) into the official
   * FHIR system URI (e.g. http://snomed.info/sct).
   * @param system SNOMED-CT, LOINC, RxNorm, CVX
   * @return The FHIR system URI for the given system or the input if not found.
   */
  public static String getSystemURI(String system) {
    if (system.equals("SNOMED-CT")) {
      system = SNOMED_URI;
    } else if (system.equals("LOINC")) {
      system = LOINC_URI;
    } else if (system.equals("RxNorm")) {
      system = RXNORM_URI;
    } else if (system.equals("CVX")) {
      system = CVX_URI;
    } else if (system.equals("DICOM-DCM")) {
      system = DICOM_DCM_URI;
    } else if (system.equals("CDT")) {
      system = CDT_URI;
    } else if (system.equals("ICD9")) {
      system = ICD9_URI;
    } else if (system.equals("ICD10")) {
      system = ICD10_URI;
    } else if (system.equals("ICD10-CM")) {
      system = ICD10_CM_URI;
    }
    return system;
  }

  /**
   * Translate the official FHIR system URI (e.g. http://snomed.info/sct)
   * into system name (e.g. SNOMED-CT).
   * @param uri http://snomed.info/sct, http://loinc.org, etc.
   * @return The internal short name used by Synthea, or "Unknown"
   */
  public static String getSystemFromURI(String uri) {
    switch (uri) {
      case SNOMED_URI:
        return "SNOMED-CT";
      case LOINC_URI:
        return "LOINC";
      case RXNORM_URI:
        return "RxNorm";
      case CVX_URI:
        return "CVX";
      case DICOM_DCM_URI:
        return "DICOM-DCM";
      case CDT_URI:
        return "CDT";
      case ICD9_URI:
        return "ICD9";
      case ICD10_URI:
        return "ICD10";
      case ICD10_CM_URI:
        return "ICD10-CM";
      default:
        return "Unknown";
    }
  }

  /**
   * Build a FHIR search url for the specified type of resource and identifier. This method
   * hard codes the identifier type to "https://github.com/synthetichealth/synthea".
   * @param resourceType type of FHIR resource
   * @param identifier the identifier value
   * @return FHIR search URL
   */
  public static String buildFhirSearchUrl(String resourceType, String identifier) {
    return String.format("%s?identifier=%s|%s", resourceType,
            "https://github.com/synthetichealth/synthea", identifier);
  }

  /**
   * Build a FHIR search URL for a clinician using the clinician's NPI identifier.
   * @param clinician the Synthea clinician instance
   * @return FHIR search URL or null if clinician is null
   */
  public static String buildFhirNpiSearchUrl(Clinician clinician) {
    if (clinician == null) {
      return null;
    } else {
      return String.format("%s?identifier=%s|%s", "Practitioner",
              "http://hl7.org/fhir/sid/us-npi", clinician.npi);
    }
  }

  /**
   * Construct a consistent UUID based on the given Person, timestamp, and a key.
   * This method allows you to get the same UUID for a concept at a given point in time,
   * when that concept does not map cleanly 1:1 to an object in the Synthea model.
   * IMPORTANT: this is NOT random but attempts to minimize the likelihood of collisions.
   */
  public static final String buildUUID(Person person, long timestamp, String key) {
    return buildUUID(person.getSeed(), timestamp, key);
  }

  /**
   * Construct a consistent UUID based on the given seed, timestamp, and a key.
   * This method allows you to get the same UUID for a concept at a given point in time,
   * when that concept does not map cleanly 1:1 to an object in the Synthea model.
   * IMPORTANT: this is NOT random but attempts to minimize the likelihood of collisions.
   */
  public static final String buildUUID(long personSeed, long timestamp, String key) {
    long mostSigBits = personSeed;
    long leastSigBits = timestamp;

    // the UUID is just the hex encoding of a 128bit number (represented in java as 2 64bit longs)
    // so the person seed and timestamp are enough to get us "something", but we can mix it up
    // to enable variety using the key. to make it numeric just get the hashCode
    int keyHash = key.hashCode();

    // first add the key to each long
    mostSigBits = mostSigBits + keyHash;
    leastSigBits = leastSigBits + keyHash;

    // because the hashCode is an int, it didn't add anything in the upper bits of the long
    // reverse the hashCode to get the upper bits
    mostSigBits = mostSigBits + Long.reverse(keyHash);
    leastSigBits = leastSigBits + Long.reverse(keyHash);

    // finally rotate the bits just to get a little more variance in the characters
    mostSigBits = Long.rotateLeft(mostSigBits, keyHash);
    leastSigBits = Long.rotateLeft(leastSigBits, keyHash);

    return new UUID(mostSigBits, leastSigBits).toString();
  }

  /**
   * FHIR resources that should include an ifNoneExist precondition when outputting transaction
   * bundles.
   */
  public static final List<String> UNDUPLICATED_FHIR_RESOURCES = Arrays.asList(
          "Location", "Organization", "Practitioner");
}
