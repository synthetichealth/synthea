package org.mitre.synthea.export.rif.enrollment;

import org.mitre.synthea.export.rif.identifiers.PartDContractID;
import org.mitre.synthea.helpers.Config;
import org.mitre.synthea.helpers.RandomNumberGenerator;
import org.mitre.synthea.world.agents.Person;

/**
 * Utility class to manage a beneficiary's part D contract history.
 */
public class PartDContractHistory extends ContractHistory<PartDContractID> {

  private static final PartDContractID[] partDContractIDs = initContractIDs();

  /**
   * Get the part D cost sharing code based on the person's income level.
   * @param person the person
   * @return part D cost sharing code
   */
  public static String getPartDCostSharingCode(Person person) {
    double incomeLevel = Double.parseDouble(person.attributes.get(Person.INCOME_LEVEL).toString());
    if (incomeLevel >= 1.0) {
      // Beneficiary enrolled in Parts A and/or B, and Part D; no premium or cost sharing subsidy
      return "09";
    } else if (incomeLevel >= 0.6) {
      // Beneficiary enrolled in Parts A and/or B, and Part D; deemed eligible for LIS with 100%
      // premium subsidy and high copayment
      return "03";
    } else if (incomeLevel >= 0.3) {
      // Beneficiary enrolled in Parts A and/or B, and Part D; deemed eligible for LIS with 100%
      // premium subsidy and low copayment
      return "02";
    }
    // Beneficiary enrolled in Parts A and/or B, and Part D; deemed eligible for LIS with 100%
    // premium subsidy and no copayment
    return "01";
  }

  private boolean employeePDP;

  /**
   * Create a new random Part D contract history.
   * @param person source of randomness
   * @param stopTime when the history should end (as ms since epoch)
   * @param yearsOfHistory how many years should be covered
   */
  public PartDContractHistory(Person person, long stopTime, int yearsOfHistory) {
    super(person, stopTime, yearsOfHistory, 20, 1);
    // 1% chance of being enrolled in employer PDP if person's income is above threshold
    // TBD determine real % of employer PDP enrollment
    employeePDP = getPartDCostSharingCode(person).equals("09")
            && person.randInt(100) == 1;
  }

  /**
   * Check if person has employer sponsored PDP.
   * @return true if has employer PDP, false otherwise.
   */
  public boolean hasEmployeePDP() {
    return employeePDP;
  }

  /**
   * Get the RDS indicator based on whether person is enrolled in Part D and has employee
   * coverage.
   * @param contractID Part D contract ID or null if not enrolled
   * @return the RDS indicator code or null if contract id is null
   */
  public String getEmployeePDPIndicator(PartDContractID contractID) {
    if (contractID == null) {
      return null;
    } else if (hasEmployeePDP()) {
      return "Y";
    } else {
      return "N";
    }
  }

  /**
   * Get a random contract ID or null.
   * @param rand source of randomness
   * @return a contract ID (70% or the time) or null (30% of the time)
   */
  @Override
  protected PartDContractID getRandomContractID(RandomNumberGenerator rand) {
    if (rand.randInt(100) < 30) {
      // 30% chance of not enrolling in Part D
      // see https://www.kff.org/medicare/issue-brief/10-things-to-know-about-medicare-part-d-coverage-and-costs-in-2019/
      return null;
    }
    return partDContractIDs[rand.randInt(partDContractIDs.length)];
  }

  /**
   * Initialize an array containing all of the configured contract IDs.
   * @return the contract IDs
   */
  private static PartDContractID[] initContractIDs() {
    int numContracts = Config.getAsInteger("exporter.bfd.partd_contract_count", 10);
    PartDContractID[] contractIDs = new PartDContractID[numContracts];
    PartDContractID contractID = PartDContractID.parse(Config.get(
            "exporter.bfd.partd_contract_start", "Z0001"));
    for (int i = 0; i < numContracts; i++) {
      contractIDs[i] = contractID;
      contractID = contractID.next();
    }
    return contractIDs;
  }
}
