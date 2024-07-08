package org.mitre.synthea.export.rif.identifiers;

/**
 * Utility class for working with CMS HICNs.
 * Note that, when used for fake HICNs, this class fixes the value of character
 * position 1 to be 'T'.
 *
 * @see <a href="https://github.com/CMSgov/beneficiary-fhir-data/blob/master/apps/bfd-model/bfd-model-rif-samples/dev/design-sample-data-sets.md">
 * https://github.com/CMSgov/beneficiary-fhir-data/blob/master/apps/bfd-model/bfd-model-rif-samples/dev/design-sample-data-sets.md</a>
 */
public class HICN extends FixedLengthIdentifier {

  private static final char[] START = {'T'};
  private static final char[] END = {'A'};
  private static final char[][] FAKE_HICN_FORMAT = {START, NUMERIC, NUMERIC,
    NUMERIC, NUMERIC, NUMERIC, NUMERIC, NUMERIC, NUMERIC, END};
  private static final char[][] REAL_HICN_FORMAT = {NUMERIC, NUMERIC, NUMERIC,
    NUMERIC, NUMERIC, NUMERIC, NUMERIC, NUMERIC, NUMERIC, END};
  public static final long MIN_HICN = 0;
  public static final long MAX_FAKE_HICN = maxValue(FAKE_HICN_FORMAT);
  public static final long MAX_REAL_HICN = maxValue(REAL_HICN_FORMAT);
  private final boolean fake;

  public HICN(long value) {
    this(value, true);
  }

  public HICN(long value, boolean fake) {
    super(value, fake ? FAKE_HICN_FORMAT : REAL_HICN_FORMAT);
    this.fake = fake;
  }

  /**
   * Parse a HICN from a string.
   * @param str the string to parse
   * @return the HICN
   * @throws IllegalArgumentException if st isn't a valid HICN
   */
  public static HICN parse(String str) {
    if (str.charAt(0) == 'T' || str.charAt(0) == 't') {
      return parse(str, true);
    } else {
      return parse(str, false);
    }
  }

  private static HICN parse(String str, boolean fake) {
    return new HICN(parse(str,
            fake ? FAKE_HICN_FORMAT : REAL_HICN_FORMAT), fake);
  }

  @Override
  public HICN next() {
    return new HICN(value + 1, fake);
  }
}
