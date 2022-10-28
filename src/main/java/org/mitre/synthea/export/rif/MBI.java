package org.mitre.synthea.export.rif;

/**
 * Utility class for working with CMS MBIs.
 * Note that this class fixes the value of character position 2 to be 'S' and will fail to
 * parse MBIs that do not conform to this restriction.
 *
 * @see <a href="https://www.cms.gov/Medicare/New-Medicare-Card/Understanding-the-MBI-with-Format.pdf">
 * https://www.cms.gov/Medicare/New-Medicare-Card/Understanding-the-MBI-with-Format.pdf</a>
 */
class MBI extends FixedLengthIdentifier {

  private static final char[] FIXED = {'S'};
  private static final char[][] MBI_FORMAT = {NON_ZERO_NUMERIC, FIXED, ALPHA_NUMERIC, NUMERIC,
    NON_NUMERIC_LIKE_ALPHA, ALPHA_NUMERIC, NUMERIC, NON_NUMERIC_LIKE_ALPHA, NON_NUMERIC_LIKE_ALPHA,
    NUMERIC, NUMERIC};
  static final long MIN_MBI = 0;
  static final long MAX_MBI = maxValue(MBI_FORMAT);

  public MBI(long value) {
    super(value, MBI_FORMAT);
  }

  static MBI parse(String str) {
    return new MBI(parse(str, MBI_FORMAT));
  }

  public MBI next() {
    return new MBI(value + 1);
  }
}
