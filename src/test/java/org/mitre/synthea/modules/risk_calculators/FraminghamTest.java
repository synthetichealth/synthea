package org.mitre.synthea.modules.risk_calculators;

import static org.junit.Assert.assertEquals;

import org.junit.Test;
import org.mitre.synthea.helpers.Utilities;
import org.mitre.synthea.world.agents.Person;
import org.mitre.synthea.world.concepts.VitalSign;

public class FraminghamTest {

  @Test
  public void testCvdModelPatient1() {
    // https://www.ahajournals.org/doi/10.1161/CIRCULATIONAHA.107.699579
    // see table 11. case 1
    
    /*
    Risk Factor       Value   Points
    Age                61       9
    Total cholesterol 180       1
    HDL                47       0
    Nontreated SBP    124       0
    Treated SBP       N/A       0
    Smoker            Yes       3
    Diabetes           No       0
    -----------------------------
    Point total                13
    Estimate of risk, %      10.0
    Heart age/vascular age, y  73
    */
   
    long now = System.currentTimeMillis();
    long dob = now - Utilities.convertTime("years", 61);
    
    Person p = new Person(1L);
    p.attributes.put(Person.GENDER, "F");
    p.attributes.put(Person.BIRTHDATE, dob);
    p.setVitalSign(VitalSign.TOTAL_CHOLESTEROL, 180);
    p.setVitalSign(VitalSign.HDL, 47);
    p.setVitalSign(VitalSign.SYSTOLIC_BLOOD_PRESSURE, 124);
    p.attributes.put("blood_pressure_controlled", false);
    p.attributes.put(Person.SMOKER, true);
    p.attributes.put("diabetes", false);
    
    double risk = Framingham.cvd10Year(p, now, false);
    assertEquals(0.1, risk, 0.001);
  }
  
  @Test
  public void testCvdModelPatient2() {
    // https://www.ahajournals.org/doi/10.1161/CIRCULATIONAHA.107.699579
    // see table 12. case 2 
    
    /*
    Risk Factor       Value   Points
    Age                53       8
    Total cholesterol 161       1
    HDL                55      −1
    Nontreated SBP    N/A       0
    Treated SBP       125       2
    Smoker             No       0
    Diabetes          Yes       3
    -----------------------------
    Point total                13
    Estimate of risk, %      15.6
    Heart age/vascular age, y  64
    */
    
    long now = System.currentTimeMillis();
    long dob = now - Utilities.convertTime("years", 53);
    
    Person p = new Person(2L);
    p.attributes.put(Person.GENDER, "M");
    p.attributes.put(Person.BIRTHDATE, dob);
    p.setVitalSign(VitalSign.TOTAL_CHOLESTEROL, 161);
    p.setVitalSign(VitalSign.HDL, 55);
    p.setVitalSign(VitalSign.SYSTOLIC_BLOOD_PRESSURE, 125);
    p.attributes.put("blood_pressure_controlled", true);
    p.attributes.put(Person.SMOKER, false);
    p.attributes.put("diabetes", true);
    
    double risk = Framingham.cvd10Year(p, now, false);
    assertEquals(0.156, risk, 0.001);
  }
}
