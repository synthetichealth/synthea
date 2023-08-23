package org.mitre.synthea.export.rif.identifiers;

/**
 * Utility class for working with CLIA Lab Numbers.
 *
 * @see <a href="https://www.cms.gov/apps/clia/clia_start.asp">
 * https://www.cms.gov/apps/clia/clia_start.asp</a>
 */
public class CLIA extends FixedLengthIdentifier {

  private static final char[][] CLIA_FORMAT = {NUMERIC, NUMERIC, ALPHA, NUMERIC, NUMERIC, NUMERIC,
    NUMERIC, NUMERIC, NUMERIC, NUMERIC};
  public static final long MIN_CLIA = 0;
  public static final long MAX_CLIA = maxValue(CLIA_FORMAT);

  public CLIA(long value) {
    super(value, CLIA_FORMAT);
  }

  public static CLIA parse(String str) {
    return new CLIA(parse(str, CLIA_FORMAT));
  }

  @Override
  public CLIA next() {
    return new CLIA(value + 1);
  }
}
