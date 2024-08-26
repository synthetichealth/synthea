package org.mitre.synthea.export.rif.identifiers;

/**
 * Utility class for working with CMS MBIs.
 * Note that when used for fake MBIs, this class fixes the value of character
 * position 2 to be 'S'.
 *
 * @see <a href="https://www.cms.gov/Medicare/New-Medicare-Card/Understanding-the-MBI-with-Format.pdf">
 * https://www.cms.gov/Medicare/New-Medicare-Card/Understanding-the-MBI-with-Format.pdf</a>
 */
public class MBI extends FixedLengthIdentifier {

  private static final char[] FIXED = {'S'};
  private static final char[][] FAKE_MBI_FORMAT = {NON_ZERO_NUMERIC, FIXED, ALPHA_NUMERIC, NUMERIC,
    NON_NUMERIC_LIKE_ALPHA, ALPHA_NUMERIC, NUMERIC, NON_NUMERIC_LIKE_ALPHA, NON_NUMERIC_LIKE_ALPHA,
    NUMERIC, NUMERIC};
  private static final char[][] REAL_MBI_FORMAT = {NON_ZERO_NUMERIC, NON_NUMERIC_LIKE_ALPHA,
    ALPHA_NUMERIC, NUMERIC, NON_NUMERIC_LIKE_ALPHA, ALPHA_NUMERIC, NUMERIC, NON_NUMERIC_LIKE_ALPHA,
    NON_NUMERIC_LIKE_ALPHA, NUMERIC, NUMERIC};
  public static final long MIN_MBI = 0;
  public static final long MAX_FAKE_MBI = maxValue(FAKE_MBI_FORMAT);
  public static final long MAX_REAL_MBI = maxValue(REAL_MBI_FORMAT);
  private final boolean fake;

  public MBI(long value) {
    this(value, true);
  }

  public MBI(long value, boolean fake) {
    super(value, fake ? FAKE_MBI_FORMAT : REAL_MBI_FORMAT);
    this.fake = fake;
  }

  /**
   * Parse an MBI from a String. If the second character is 'S' this will create
   * a fake MBI, otherwise it will create a "real" MBI.
   * @param str the string
   * @return the MBI
   */
  public static MBI parse(String str) {
    if (str.charAt(1) == 'S' || str.charAt(1) == 's') {
      return parse(str, true);
    } else {
      return parse(str, false);
    }
  }

  private static MBI parse(String str, boolean fake) {
    return new MBI(parse(str, fake ? FAKE_MBI_FORMAT : REAL_MBI_FORMAT), fake);
  }

  @Override
  public MBI next() {
    return new MBI(value + 1, fake);
  }
}
