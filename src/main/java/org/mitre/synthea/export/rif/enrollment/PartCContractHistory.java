package org.mitre.synthea.export.rif.enrollment;

import org.mitre.synthea.export.rif.identifiers.PartCContractID;
import org.mitre.synthea.helpers.Config;
import org.mitre.synthea.helpers.RandomNumberGenerator;
import org.mitre.synthea.world.agents.Person;

/**
 * Utility class to manage a beneficiary's part C contract history.
 */
public class PartCContractHistory extends ContractHistory<PartCContractID> {

  private static final PartCContractID[] partCContractIDs = initContractIDs();

  /**
   * Create a new random Part C contract history.
   * @param person source of randomness
   * @param stopTime when the history should end (as ms since epoch)
   * @param yearsOfHistory how many years should be covered
   */
  public PartCContractHistory(Person person, long stopTime, int yearsOfHistory) {
    super(person, stopTime, yearsOfHistory, 20, 1);
  }

  /**
   * Get a random contract ID or null.
   * @param rand source of randomness
   * @return a contract ID (58% or the time) or null (42% of the time)
   */
  @Override
  protected PartCContractID getRandomContractID(RandomNumberGenerator rand) {
    if (rand.randInt(100) < 42) {
      // 42% chance of not enrolling in Part C
      // see https://www.kff.org/medicare/issue-brief/medicare-advantage-in-2021-enrollment-update-and-key-trends/
      return null;
    }
    return partCContractIDs[rand.randInt(partCContractIDs.length)];
  }

  /**
   * Initialize an array containing all of the configured contract IDs.
   * @return
   */
  private static PartCContractID[] initContractIDs() {
    int numContracts = Config.getAsInteger("exporter.bfd.partc_contract_count", 10);
    PartCContractID[] contractIDs = new PartCContractID[numContracts];
    PartCContractID contractID = PartCContractID.parse(Config.get(
            "exporter.bfd.partc_contract_start", "Y0001"));
    for (int i = 0; i < numContracts; i++) {
      contractIDs[i] = contractID;
      contractID = contractID.next();
    }
    return contractIDs;
  }

}
