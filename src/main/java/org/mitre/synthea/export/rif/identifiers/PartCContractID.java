package org.mitre.synthea.export.rif.identifiers;

/**
 * Utility class for working with CMS Part C Contract IDs.
 */
public class PartCContractID extends FixedLengthIdentifier {

  private static final char[][] PARTC_CONTRACT_FORMAT = {ALPHA, NUMERIC, NUMERIC, NUMERIC, NUMERIC};
  public static final long MIN_PARTC_CONTRACT_ID = 0;
  public static final long MAX_PARTC_CONTRACT_ID = maxValue(PARTC_CONTRACT_FORMAT);

  public PartCContractID(long value) {
    super(value, PARTC_CONTRACT_FORMAT);
  }

  public static PartCContractID parse(String str) {
    return new PartCContractID(parse(str, PARTC_CONTRACT_FORMAT));
  }

  @Override
  public PartCContractID next() {
    return new PartCContractID(value + 1);
  }
}
