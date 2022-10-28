package org.mitre.synthea.export.rif;

/**
 * Utility class for working with CLIA Lab Numbers.
 *
 * @see <a href="https://www.cms.gov/apps/clia/clia_start.asp">
 * https://www.cms.gov/apps/clia/clia_start.asp</a>
 */
class CLIA extends FixedLengthIdentifier {

  private static final char[][] CLIA_FORMAT = {NUMERIC, NUMERIC, ALPHA, NUMERIC, NUMERIC, NUMERIC,
    NUMERIC, NUMERIC, NUMERIC, NUMERIC};
  static final long MIN_CLIA = 0;
  static final long MAX_CLIA = maxValue(CLIA_FORMAT);

  public CLIA(long value) {
    super(value, CLIA_FORMAT);
  }

  static CLIA parse(String str) {
    return new CLIA(parse(str, CLIA_FORMAT));
  }

  public CLIA next() {
    return new CLIA(value + 1);
  }
}
