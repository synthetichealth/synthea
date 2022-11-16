package org.mitre.synthea.export.rif.identifiers;

/**
 * Utility class for working with CMS HICNs.
 * Note that this class fixes the value of character position 1 to be 'T' and character position
 * 10 to be 'A' - it will fail to parse HICNs that do not conform to this restriction.
 *
 * @see <a href="https://github.com/CMSgov/beneficiary-fhir-data/blob/master/apps/bfd-model/bfd-model-rif-samples/dev/design-sample-data-sets.md">
 * https://github.com/CMSgov/beneficiary-fhir-data/blob/master/apps/bfd-model/bfd-model-rif-samples/dev/design-sample-data-sets.md</a>
 */
public class HICN extends FixedLengthIdentifier {

  private static final char[] START = {'T'};
  private static final char[] END = {'A'};
  private static final char[][] HICN_FORMAT = {START, NUMERIC, NUMERIC, NUMERIC, NUMERIC, NUMERIC,
    NUMERIC, NUMERIC, NUMERIC, END};
  public static final long MIN_HICN = 0;
  public static final long MAX_HICN = maxValue(HICN_FORMAT);

  public HICN(long value) {
    super(value, HICN_FORMAT);
  }

  public static HICN parse(String str) {
    return new HICN(parse(str, HICN_FORMAT));
  }

  @Override
  public HICN next() {
    return new HICN(value + 1);
  }
}
