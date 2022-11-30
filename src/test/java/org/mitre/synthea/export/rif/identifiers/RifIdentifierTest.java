package org.mitre.synthea.export.rif.identifiers;

import static org.junit.Assert.assertEquals;

import org.junit.Test;

public class RifIdentifierTest {

  @Test
  public void testHICN() {
    HICN hicn = new HICN(HICN.MIN_HICN);
    assertEquals("T00000000A", hicn.toString());
    hicn = new HICN(HICN.MAX_HICN);
    assertEquals("T99999999A", hicn.toString());
    hicn = HICN.parse("T01001001A");
    assertEquals("T01001001A", hicn.toString());
    hicn = HICN.parse("T99999999A");
    assertEquals("T99999999A", hicn.toString());
  }

  @Test
  public void testMBI() {
    MBI mbi = new MBI(MBI.MIN_MBI);
    assertEquals("1S00A00AA00", mbi.toString());
    mbi = new MBI(MBI.MAX_MBI);
    assertEquals("9SY9YY9YY99", mbi.toString());
    mbi = MBI.parse("1S00A00AA00");
    assertEquals("1S00A00AA00", mbi.toString());
    mbi = MBI.parse("1S00-A00-AA00");
    assertEquals("1S00A00AA00", mbi.toString());
    mbi = MBI.parse("9SY9YY9YY99");
    assertEquals("9SY9YY9YY99", mbi.toString());
    mbi = MBI.parse("9SY9-YY9-YY99");
    assertEquals("9SY9YY9YY99", mbi.toString());
  }

}
