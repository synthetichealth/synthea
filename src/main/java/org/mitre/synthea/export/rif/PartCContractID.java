package org.mitre.synthea.export.rif;

/**
 * Utility class for working with CMS Part C Contract IDs.
 */
class PartCContractID extends FixedLengthIdentifier {

  private static final char[][] PARTC_CONTRACT_FORMAT = {ALPHA, NUMERIC, NUMERIC, NUMERIC, NUMERIC};
  static final long MIN_PARTC_CONTRACT_ID = 0;
  static final long MAX_PARTC_CONTRACT_ID = maxValue(PARTC_CONTRACT_FORMAT);

  public PartCContractID(long value) {
    super(value, PARTC_CONTRACT_FORMAT);
  }

  static PartCContractID parse(String str) {
    return new PartCContractID(parse(str, PARTC_CONTRACT_FORMAT));
  }

  public PartCContractID next() {
    return new PartCContractID(value + 1);
  }
}
