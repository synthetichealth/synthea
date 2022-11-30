package org.mitre.synthea.export.rif.identifiers;

/**
 * Utility class for working with CMS Plan Benefit Package IDs.
 */
public class PlanBenefitPackageID extends FixedLengthIdentifier {

  private static final char[][] PBP_CONTRACT_FORMAT = {NUMERIC, NUMERIC, NUMERIC};
  public static final long MIN_PARTC_CONTRACT_ID = 0;
  public static final long MAX_PARTC_CONTRACT_ID = maxValue(PBP_CONTRACT_FORMAT);

  public PlanBenefitPackageID(long value) {
    super(value, PBP_CONTRACT_FORMAT);
  }

  public static PlanBenefitPackageID parse(String str) {
    return new PlanBenefitPackageID(parse(str, PBP_CONTRACT_FORMAT));
  }

  @Override
  public PlanBenefitPackageID next() {
    return new PlanBenefitPackageID(value + 1);
  }
}
