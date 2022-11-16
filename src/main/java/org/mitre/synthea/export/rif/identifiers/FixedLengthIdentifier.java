package org.mitre.synthea.export.rif.identifiers;

import java.util.concurrent.atomic.AtomicReference;

/**
 * Base class for fixed length, alphanumeric identifiers.
 */
public abstract class FixedLengthIdentifier {

  static final char[] NUMERIC = {'0', '1', '2', '3', '4', '5', '6', '7', '8', '9'};
  static final char[] NON_ZERO_NUMERIC = {'1', '2', '3', '4', '5', '6', '7', '8', '9'};
  static final char[] ALPHA = {'A', 'B', 'C', 'D', 'E', 'F', 'G', 'H', 'I', 'J', 'K', 'L', 'M',
    'N', 'O', 'P', 'Q', 'R', 'S', 'T', 'U', 'V', 'W', 'X', 'Y', 'Z'};
  static final char[] NON_NUMERIC_LIKE_ALPHA = {'A', 'C', 'D', 'E', 'F', 'G', 'H', 'J', 'K', 'M',
    'N', 'P', 'Q', 'R', 'T', 'U', 'V', 'W', 'X', 'Y'};
  static final char[] ALPHA_NUMERIC = {'0', '1', '2', '3', '4', '5', '6', '7', '8', '9', 'A', 'C',
    'D', 'E', 'F', 'G', 'H', 'J', 'K', 'M', 'N', 'P', 'Q', 'R', 'T', 'U', 'V', 'W', 'X', 'Y'};

  private final char[][] format;
  long value;

  /**
   * Construct a fixed length identifier with the supplied value in the specified format.
   * @param value the underlying numeric value of the identifier
   * @param format the format of the identifier
   */
  public FixedLengthIdentifier(long value, char[][] format) {
    this.format = format;
    if (value < 0 || value > maxValue(format)) {
      throw new IllegalArgumentException(String.format("Value (%d) out of range (%d - %d)", value,
              0, maxValue(format)));
    }
    this.value = value;
  }

  public abstract <T extends FixedLengthIdentifier> T next();

  public static String getAndUpdateId(AtomicReference<? extends FixedLengthIdentifier> idRef) {
    FixedLengthIdentifier id = idRef.getAndUpdate(v -> v.next());
    return id.toString();
  }

  protected static long parse(String str, char[][] format) {
    str = str.replaceAll("-", "").toUpperCase();
    if (str.length() != format.length) {
      throw new IllegalArgumentException(String.format(
              "Invalid format (%s), must be %d characters", str, format.length));
    }
    long v = 0;
    for (int i = 0; i < format.length; i++) {
      int multiplier = format[i].length;
      v = v * multiplier;
      char c = str.charAt(i);
      char[] range = format[i];
      int index = indexOf(range, c);
      if (index == -1) {
        throw new IllegalArgumentException(String.format(
                "Unexpected character (%c) at position %d in %s", c, i, str));
      }
      v += index;
    }
    return v;
  }

  protected static long maxValue(char[][] format) {
    long max = 1;
    for (char[] range : format) {
      max = max * range.length;
    }
    return max - 1;
  }

  private static int indexOf(char[] arr, char v) {
    for (int i = 0; i < arr.length; i++) {
      if (arr[i] == v) {
        return i;
      }
    }
    return -1;
  }

  @Override
  public String toString() {
    StringBuilder sb = new StringBuilder();
    long v = this.value;
    for (int i = 0; i < format.length; i++) {
      char[] range = format[format.length - i - 1];
      long p = v % range.length;
      sb.insert(0, range[(int) p]);
      v = v / range.length;
    }
    return sb.toString();
  }

  @Override
  public int hashCode() {
    int hash = 5;
    hash = 53 * hash + (int) (this.value ^ (this.value >>> 32));
    return hash;
  }

  @Override
  public boolean equals(Object obj) {
    if (this == obj) {
      return true;
    }
    if (obj == null) {
      return false;
    }
    if (getClass() != obj.getClass()) {
      return false;
    }
    final FixedLengthIdentifier other = (FixedLengthIdentifier) obj;
    return this.value == other.value;
  }
}
