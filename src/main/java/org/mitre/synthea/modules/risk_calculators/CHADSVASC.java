package org.mitre.synthea.modules.risk_calculators;

import org.mitre.synthea.world.agents.Person;

public class CHADSVASC {

  private static final double[] SCORE_TO_RISK = {0.002, 0.006, 0.022, 0.032, 0.048, 0.072, 0.097, 0.112, 0.108, 0.122};
  
  public static double strokeRisk1Year(Person person, long time) {
    int score = 0;
    
    int age = person.ageInYears(time);

    if (age >= 75) {
      score += 2;
    } else if (age >= 65) {
      score += 1;
    }
    if (person.attributes.get(Person.GENDER).equals("F")) {
      score += 1;
    }
    
    // chf - 1
    if (person.attributes.get("chf") != null) {
      score += 1;
    }
    
    // hypertension - 1
    if (person.attributes.get("hypertension") != null) {
      score += 1;
    }
    
    // diabetes - 1
    if (person.attributes.get("diabetes") != null) {
      score += 1;
    }
    
    // vascular disease - history or active mi = 1
    if (person.record.conditionActive("399211009")) { // History of myocardial infarction (situation)
      score += 1;
    }
    
    
    // prior stroke = 2
    // TODO - as of now the stroke condition is never ended and doesn't assign a history condition
    // keep this in line with that module
    if (person.record.conditionActive("230690007")) { // Stroke
      score += 2;
    }
    
    double risk = SCORE_TO_RISK[score];
    return risk;
  }
}
