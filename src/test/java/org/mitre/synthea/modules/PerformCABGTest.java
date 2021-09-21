package org.mitre.synthea.modules;

import static org.junit.Assert.assertEquals;

import java.lang.reflect.Field;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedList;
import java.util.List;
import java.util.Map.Entry;
import java.util.Random;

import org.junit.Assert;
import org.junit.BeforeClass;
import org.junit.Ignore;
import org.junit.Test;
import org.mitre.synthea.engine.State;
import org.mitre.synthea.helpers.SimpleCSV;
import org.mitre.synthea.helpers.Utilities;
import org.mitre.synthea.world.agents.Clinician;
import org.mitre.synthea.world.agents.Person;
import org.mitre.synthea.world.agents.Provider;
import org.mitre.synthea.world.concepts.HealthRecord;
import org.mitre.synthea.world.concepts.HealthRecord.Code;
import org.mitre.synthea.world.concepts.VitalSign;

public class PerformCABGTest {
  
  
  private static List<TestWrapper> TEST_CASES;
  
  public static class TestWrapper {
    boolean onPump;
    int numberGrafts;
    boolean hasDialysis;
    int totalNoDistAnastArtCond;
    boolean sternotomy;
    boolean redo;
    boolean unstableAngina;
    double calculatedBMI;
    double meanSurgeonTime; 
    
    double expected;
  }
  
  @BeforeClass
  public static void setup() throws Exception {
   
    String testCaseContent = Utilities.readResource("cabg_test_cases.csv");
    
    TEST_CASES = new ArrayList<>();
    for (LinkedHashMap<String,String> line : SimpleCSV.parse(testCaseContent)) {
      TestWrapper testCase = new TestWrapper();
      TEST_CASES.add(testCase);
      
      for (Entry<String,String> kv : line.entrySet()) {
        // more fun to use reflection
        Field field = testCase.getClass().getDeclaredField(kv.getKey());
        
        switch (field.getType().getSimpleName()) {
          case "boolean":
            field.setBoolean(testCase, Boolean.parseBoolean(kv.getValue()));
            break;
          case "int":
            field.setInt(testCase, Integer.parseInt(kv.getValue()));
            break;
          case "double":
            field.setDouble(testCase, Double.parseDouble(kv.getValue()));
            break;
        }
      }
    }
  }
  
  @Test @Ignore
  public void testModule() {
    Person person = new Person(0L);
    person.setVitalSign(VitalSign.BMI, 20.0);
    
    person.attributes.put("cabg_number_of_grafts", (int)person.rand(1, 6));
    person.attributes.put("cabg_arterial_conduits", (int)person.rand(1, 6));
    
    HealthRecord.Procedure opApp = person.record.procedure(0, "OperativeApproach");
    person.attributes.put("cabg_operative_approach", opApp);
    
    person.history = new LinkedList<State>();
    person.history.add(new State.Initial());
    person.history.get(0).name = "Initial";
    PerformCABG module = new PerformCABG();
    Assert.assertFalse(module.process(person, 0L));
    
    long eightHours = Utilities.convertTime("hours", 8);
    Assert.assertTrue(module.process(person, eightHours));
  }
  
  
  @Test
  public void testFunctionWithPerson() {

    // TODO: disable gaussian noise in order to run this test
    for (TestWrapper testCase : TEST_CASES) {
      
      Person person = new Person(0L);
      Clinician clinician = new Clinician(0, new Random(0), 0, new Provider());
      
      
      person.attributes.put("cabg_pump", testCase.onPump);
      person.attributes.put("cabg_number_of_grafts", testCase.numberGrafts);
      
      if (testCase.hasDialysis) {
        person.attributes.put("ckd", 5);
      }
      
      person.attributes.put("cabg_arterial_conduits", testCase.totalNoDistAnastArtCond);
      
      HealthRecord.Procedure opApp = person.record.procedure(0, "OperativeApproach");
      person.attributes.put("cabg_operative_approach", opApp);
      if (testCase.sternotomy) {
        opApp.codes.add(new Code("SNOMED-CT", "359672006", "Sternotomy"));
      }
      
      if (testCase.redo) {
        person.record.conditionStart(0, "399261000");
      }
      
      if (testCase.unstableAngina) {
        person.record.conditionStart(0, "4557003");
      }

      person.setVitalSign(VitalSign.BMI,  testCase.calculatedBMI);
    
      clinician.attributes.put("mean_surgeon_time", testCase.meanSurgeonTime);
      
      
      double result = PerformCABG.getProcedureDuration(person, clinician, 0);
      assertEquals(testCase.expected, result, 1.0);
    }
  }
  
  @Test
  public void testFunctionWithRawValues() {
    for (TestWrapper testCase : TEST_CASES) {
      double result = PerformCABG.getProcedureDuration(testCase.onPump, testCase.numberGrafts, testCase.hasDialysis,
          testCase.totalNoDistAnastArtCond, testCase.sternotomy, testCase.redo, testCase.unstableAngina,
          testCase.calculatedBMI, testCase.meanSurgeonTime, 0);
      assertEquals(testCase.expected, result, 1.0);
    }
  }
}