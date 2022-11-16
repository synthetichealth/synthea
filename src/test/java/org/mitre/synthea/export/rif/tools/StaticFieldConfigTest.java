package org.mitre.synthea.export.rif.tools;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

import java.io.IOException;
import java.util.HashMap;
import org.junit.Test;
import org.mitre.synthea.export.rif.BB2RIFStructure;
import org.mitre.synthea.helpers.RandomNumberGenerator;
import org.mitre.synthea.world.agents.Person;

public class StaticFieldConfigTest {

  @Test
  public void testStaticFieldConfig() throws IOException, NoSuchMethodException {
    RandomNumberGenerator rand = new Person(System.currentTimeMillis());
    assertEquals("foo", StaticFieldConfig.processCell("foo", rand));
    String randomVal = StaticFieldConfig.processCell("1, 2, 3", rand);
    assertTrue(randomVal.equalsIgnoreCase("1")
            || randomVal.equalsIgnoreCase("2")
            || randomVal.equalsIgnoreCase("3"));
    StaticFieldConfig config = new StaticFieldConfig();
    assertEquals("INSERT", config.getValue("DML_IND", BB2RIFStructure.INPATIENT.class));
    assertEquals("82 (DMEPOS)", config.getValue("NCH_CLM_TYPE_CD", BB2RIFStructure.DME.class));
    assertEquals("71 (local carrier, non-DME)",
            config.getValue("NCH_CLM_TYPE_CD", BB2RIFStructure.CARRIER.class));
    HashMap<BB2RIFStructure.BENEFICIARY, String> values = new HashMap<>();
    config.setValues(values, BB2RIFStructure.BENEFICIARY.class, rand);
    assertEquals("INSERT", values.get(BB2RIFStructure.BENEFICIARY.DML_IND));
    String sexIdent = values.get(BB2RIFStructure.BENEFICIARY.BENE_SEX_IDENT_CD);
    assertTrue(sexIdent.equals("1") || sexIdent.equals("2"));
  }
}
