package org.mitre.synthea.export.rif.identifiers;

/**
 * Utility class for working with synthetic passport numbers.
 * Format: 'X' + 8 digits + 'X' (e.g. X12345678X), matching the format
 * originally intended in LifecycleModule but produced incorrectly due to
 * a dropped offset (see issue #1685).
 */
public class Passport extends FixedLengthIdentifier {

  private static final char[] FIXED_X = {'X'};
  private static final char[][] PASSPORT_FORMAT = {FIXED_X, NUMERIC, NUMERIC, NUMERIC, NUMERIC,
      NUMERIC, NUMERIC, NUMERIC, NUMERIC, FIXED_X};
  public static final long MIN_PASSPORT = 0;
  public static final long MAX_PASSPORT = maxValue(PASSPORT_FORMAT);

  public Passport(long value) {
    super(value, PASSPORT_FORMAT);
  }

  /**
   * Parse a Passport from a String.
   *
   * @param str the string
   * @return the Passport
   */
  public static Passport parse(String str) {
    return new Passport(parse(str, PASSPORT_FORMAT));
  }

  @Override
  public Passport next() {
    return new Passport(value + 1);
  }
}