package org.mitre.synthea.export.rif.identifiers;

/**
 * Utility class for working with CMS MBIs.
 * Note that this class fixes the value of character position 2 to be 'S' and will fail to
 * parse MBIs that do not conform to this restriction.
 *
 * @see <a href="https://www.cms.gov/Medicare/New-Medicare-Card/Understanding-the-MBI-with-Format.pdf">
 * https://www.cms.gov/Medicare/New-Medicare-Card/Understanding-the-MBI-with-Format.pdf</a>
 */
public class MBI extends FixedLengthIdentifier {

  private static final char[] FIXED = {'S'};
  private static final char[][] MBI_FORMAT = {NON_ZERO_NUMERIC, FIXED, ALPHA_NUMERIC, NUMERIC,
    NON_NUMERIC_LIKE_ALPHA, ALPHA_NUMERIC, NUMERIC, NON_NUMERIC_LIKE_ALPHA, NON_NUMERIC_LIKE_ALPHA,
    NUMERIC, NUMERIC};
  public static final long MIN_MBI = 0;
  public static final long MAX_MBI = maxValue(MBI_FORMAT);

  public MBI(long value) {
    super(value, MBI_FORMAT);
  }

  public static MBI parse(String str) {
    return new MBI(parse(str, MBI_FORMAT));
  }

  @Override
  public MBI next() {
    return new MBI(value + 1);
  }
}
