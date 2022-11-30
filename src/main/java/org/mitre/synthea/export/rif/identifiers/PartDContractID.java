package org.mitre.synthea.export.rif.identifiers;

/**
 * Utility class for working with CMS Part D Contract IDs.
 */
public class PartDContractID extends FixedLengthIdentifier {

  private static final char[][] PARTD_CONTRACT_FORMAT = {ALPHA, NUMERIC, NUMERIC, NUMERIC, NUMERIC};
  public static final long MIN_PARTD_CONTRACT_ID = 0;
  public static final long MAX_PARTD_CONTRACT_ID = maxValue(PARTD_CONTRACT_FORMAT);

  public PartDContractID(long value) {
    super(value, PARTD_CONTRACT_FORMAT);
  }

  public static PartDContractID parse(String str) {
    return new PartDContractID(parse(str, PARTD_CONTRACT_FORMAT));
  }

  @Override
  public PartDContractID next() {
    return new PartDContractID(value + 1);
  }
}
