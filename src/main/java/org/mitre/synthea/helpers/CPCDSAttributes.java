package org.mitre.synthea.helpers;

import java.util.Random;

import org.mitre.synthea.world.concepts.HealthRecord.Encounter;

/**
 * A helper class for storing CPCDS derived encounter attributes
 * to eliminate reusing the same code in multiple areas.
 */
public class CPCDSAttributes {

	private Encounter ENCOUNTER;
	private String inout;
	private String subtype;
	private String code;
	private String type;
	
	private long disallowed;
	private long liability;
	private String deductable;
	private long copay;
	
	public CPCDSAttributes(Encounter encounter) {
		ENCOUNTER = encounter;
		
		if (encounter.reason == null) {
      setInout("out of network");
      setSubtype("4013");
      setCode("3");
      setType("OUTPATIENT");
		} else {
      setInout("in network");
      if (randomTrueOrFalse()) {
        setSubtype("4011");
        setCode("1");
      } else {
        setSubtype("4041");
        setCode("2");
      }
      setType("INPATIENT");
		}
		
    long baseEcounterCost = encounter.getCost().longValue();
    long totalClaimCost = (long) encounter.claim.getTotalClaimCost();
    long payerCoverage = (long) encounter.claim.getCoveredCost();
		if (totalClaimCost >= baseEcounterCost) {
			setDisallowed(0);
		  setLiability(0);
		} else {
			setDisallowed(baseEcounterCost - totalClaimCost);
		  setLiability(baseEcounterCost - totalClaimCost);
		}
    if (payerCoverage == totalClaimCost) {
    	setDeductable("True");
      setCopay(0);
    } else {
    	setDeductable("False");
      setCopay(totalClaimCost - payerCoverage);
    }
	}
	
	/**
	 * @return a random boolean value
	 */
	private boolean randomTrueOrFalse() {
		Random random = new Random();
		return random.nextBoolean();
	}
	
	public String getNpiProvider() {
		String npiProvider;
		if (ENCOUNTER.provider == null) {
      npiProvider = "";
    } else {
      npiProvider = ENCOUNTER.provider.getResourceID();
    }
		return npiProvider;
	}

	public String getInout() {
		return inout;
	}

	private void setInout(String inout) {
		this.inout = inout;
	}

	public String getSubtype() {
		return subtype;
	}

	private void setSubtype(String subtype) {
		this.subtype = subtype;
	}

	public String getCode() {
		return code;
	}

	private void setCode(String code) {
		this.code = code;
	}

	public String getType() {
		return type;
	}

	private void setType(String type) {
		this.type = type;
	}

	public long getDisallowed() {
		return disallowed;
	}

	private void setDisallowed(long disallowed) {
		this.disallowed = disallowed;
	}

	public long getLiability() {
		return liability;
	}

	private void setLiability(long liability) {
		this.liability = liability;
	}

	public String getDeductable() {
		return deductable;
	}

	private void setDeductable(String deductable) {
		this.deductable = deductable;
	}

	public long getCopay() {
		return copay;
	}

	private void setCopay(long copay) {
		this.copay = copay;
	}
}
