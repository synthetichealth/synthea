package org.mitre.synthea.engine;

import com.google.gson.JsonObject;
import com.google.gson.internal.LinkedTreeMap;

import java.io.File;
import java.io.IOException;
import java.nio.charset.Charset;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Random;

import org.apache.commons.csv.CSVFormat;
import org.apache.commons.csv.CSVParser;
import org.apache.commons.csv.CSVRecord;
import org.mitre.synthea.world.agents.Person;

import ca.uhn.fhir.model.dstu2.resource.Provenance.AgentRelatedAgent;

/**
 * Transition represents all the transition types within the generic module
 * framework. This class is stateless, and calling 'follow' on an instance must
 * not modify state as instances of Transition within States and Modules are
 * shared across the population.
 */
public abstract class Transition {

  protected List<String> remarks;

  /**
   * Get the name of the next state.
   * 
   * @param person : person being processed
   * @param time   : time of this transition
   * @return name : name of the next state
   */
  public abstract String follow(Person person, long time);

  /**
   * Direct transitions are the simplest of transitions. They transition directly
   * to the indicated state. The value of a direct_transition is simply the name
   * of the state to transition to.
   */
  public static class DirectTransition extends Transition {
    private String transition;

    public DirectTransition(String transition) {
      this.transition = transition;
    }

    @Override
    public String follow(Person person, long time) {
      return transition;
    }
  }

  /**
   * A TransitionOption represents a single destination state that may be
   * transitioned to.
   */
  private abstract static class TransitionOption {
    protected String transition;
  }

  /**
   * A DistributedTransitionOption represents a single destination state, with a
   * given distribution percentage, or a named distribution representing an
   * attribute containing the distribution percentage.
   */
  public static final class DistributedTransitionOption extends TransitionOption {
    private Object distribution;
    public Double numericDistribution;
    private NamedDistribution namedDistribution;
  }

  /**
   * Distributed transitions will transition to one of several possible states
   * based on the configured distribution. Distribution values are from 0.0 to
   * 1.0, such that a value of 0.55 would indicate a 55% chance of transitioning
   * to the corresponding state. A distributed_transition consists of an array of
   * distribution/transition pairs for which the distribution values should sum up
   * to 1.0. If the distribution values do not sum up to 1.0, the remaining
   * distribution will transition to the last defined transition state. For
   * example, given distributions of 0.3 and 0.6, the effective distribution of
   * the last transition will actually be 0.7. If the distribution values sum up
   * to more than 1.0, the remaining distributions are ignored (for example, if
   * distribution values are 0.75, 0.5, and 0.3, then the second transition will
   * have an effective distribution of 0.25, and the last transition will have an
   * effective distribution of 0.0).
   */
  public static final class DistributedTransition extends Transition {
    private List<DistributedTransitionOption> transitions;

    public DistributedTransition(List<DistributedTransitionOption> transitions) {
      this.transitions = transitions;
    }

    @Override
    public String follow(Person person, long time) {
      return pickDistributedTransition(transitions, person);
    }
  }

  /**
   * A LookupTableTransitionOption represents a destination state which will be
   * chosen based on the result of a table lookup.
   */
  public static final class LookupTableTransitionOption extends TransitionOption {
    private String lookupTableName;
  }

  /**
   * LookupTable transitions will transition to one of two possible states based
   * on a probability extacted from a table. A LookUpTableTransition will have one
   * field denoting the lookuptable to use and a field donating the probability
   * that the subject of that table is true. A table may have any set of
   * attributes to determine the probability based on a variety of attributes
   * which will be compared to the attributes within the current person. If a
   * person does not correspond to any of the sets of attributes, the probaiblity
   * will default to 0.0.
   */

  public static class LookupTableTransition extends Transition {

    // Map of lookupTables Data
    private static HashMap<String, HashMap<LookupTableKey, Double>> lookupTables = new HashMap<String, HashMap<LookupTableKey, Double>>();
    private List<LookupTableTransitionOption> transitions;
    private ArrayList<String> attributes;

    public LookupTableTransition(List<LookupTableTransitionOption> lookupTableTransitions) {

      this.transitions = lookupTableTransitions;
      this.attributes = new ArrayList<String>();

      if (!lookupTables.containsKey(transitions.get(0).lookupTableName)) {

        String newTableName = transitions.get(0).lookupTableName;
        System.out.println("Loading Lookup Table: " + newTableName);
        HashMap<LookupTableKey, Double> newTable = new HashMap<LookupTableKey, Double>();

        // Parse CSV
        File csvData = new File("/Users/rscalfani/Documents/code/synthea/src/main/resources/modules/lookup_tables/"
            + this.transitions.get(0).lookupTableName);
        CSVParser parser;
        try {
          parser = CSVParser.parse(csvData, Charset.defaultCharset(), CSVFormat.RFC4180);
          List<CSVRecord> records = parser.getRecords();
          // Parse out the list of attributes based on names of columns.
          int numAttributes = records.get(0).size() - 1;
          // First entry in CSV has extra char at beginning
          int startIndex = 1;
          for (int attributeName = 0; attributeName < numAttributes; attributeName++) {
            this.attributes.add(records.get(0).get(attributeName).toString().substring(startIndex).toLowerCase());
            startIndex = 0;
          }
          // Fill new table within lookupTables hashmap
          for (int currentRecord = 1; currentRecord < records.size(); currentRecord++) {
            // Parse the list of attributes for current record
            ArrayList<String> attributeRecords = new ArrayList<String>();
            for (int currentAttribute = 0; currentAttribute < numAttributes; currentAttribute++) {
              attributeRecords.add(records.get(currentRecord).get(currentAttribute));
            }
            LookupTableKey attributeRecordsLookupKey = new LookupTableKey(attributeRecords, this.attributes.indexOf("age"));
            // Insert new record into new table
            newTable.put(attributeRecordsLookupKey, Double.parseDouble(records.get(currentRecord).get(numAttributes)));
          }
          // Insert new table into lookupTables Hashmap
          lookupTables.put(newTableName, newTable);
        } catch (IOException e) {
          e.printStackTrace();
        }
      }
    }

    @Override
    public String follow(Person person, long time) {

      // If none of the records match, then probability will default to 0.0.
      double numericDistribution = 0.0;

      // Extract Person's list of relevant attributes
      ArrayList<String> personsAttributes = new ArrayList<String>();
      for (int attributeToAdd = 0; attributeToAdd < this.attributes.size(); attributeToAdd++) {
        if(attributes.get(attributeToAdd).equals("age")){
          personsAttributes.add( Integer.toString(person.ageInYears(time)));
        }else{
        personsAttributes.add( (String) person.attributes.get(this.attributes.get(attributeToAdd).toLowerCase()));
        }
      }

      LookupTableKey personsAttributesLookupKey = new LookupTableKey(personsAttributes, this.attributes.indexOf("age"));

      if (lookupTables.get(this.transitions.get(0).lookupTableName).containsKey(personsAttributesLookupKey)) {
        // Person's attributes match a lookup table entry
        numericDistribution = lookupTables.get(this.transitions.get(0).lookupTableName).get(personsAttributesLookupKey);
      } else {
        // No attribute match
        // implement '*' here
        // implement default value
      }

      // Randomly determine which transition to go to.
      Random r = new Random();
      if ((r.nextInt(1000) + 1) <= numericDistribution * 1000) {
        // Return the first transition
        return transitions.get(0).transition;
      } else {
        // Return second transition
        return transitions.get(1).transition;
      }
    }

    class LookupTableKey{

      ArrayList<String> attributes;
      int ageIndex;

      LookupTableKey(ArrayList<String> attributes, int ageIndex){
        System.out.println(ageIndex);
        this.attributes = attributes;
        this.ageIndex = ageIndex;
      }

      @Override
      public int hashCode() {
        if(this.ageIndex > -1){
          System.out.println("#Hashing Age");
          ArrayList<String> ageRemovedAttributes = new ArrayList<String>();
          ageRemovedAttributes = this.attributes;

          System.out.println(ageRemovedAttributes);

          ageRemovedAttributes.remove(ageIndex);
          return ageRemovedAttributes.hashCode();// * attributes.get(attributes.indexOf("age")).hashCode();
        }else{
          //System.out.println("#Hashing");
          return this.attributes.hashCode();
        }
      }

      @Override
      public boolean equals(Object obj){

        ///System.out.println("Checking if it equals...");

        if(getClass() != obj.getClass()){
          return false;
        }

        ArrayList<String> personAttributes = ((LookupTableKey) obj).attributes;

        if(this.attributes.contains("age")){
          int ageIndex = this.attributes.indexOf("age");
          String personAge = personAttributes.get(ageIndex);
          String ageRange = this.attributes.get(ageIndex);

          ArrayList<String> ageRemovedAttributes = new ArrayList<String>();
          ageRemovedAttributes = this.attributes;
          ageRemovedAttributes.remove(ageIndex);

          ArrayList<String> ageRemovedPersonAttributes = new ArrayList<String>();
          ageRemovedPersonAttributes = personAttributes;
          ageRemovedPersonAttributes.remove(ageIndex);

          if(ageRemovedAttributes.equals(ageRemovedPersonAttributes)){
            //parse out age
  
            String lowAge = ageRange.substring(0,ageRange.indexOf("-"));
            String highAge = ageRange.substring(ageRange.indexOf("-"));

            if(Integer.parseInt(lowAge) < Integer.parseInt(personAge) && Integer.parseInt(highAge) > Integer.parseInt(personAge)){
              return true;
            }else{
              return false;
            }
          }else{
            return false;
          }
        }else{
          //System.out.println("Equals without age");
          return this.attributes.equals(personAttributes);
        }
      }
    }
  }

  /**
   * A ConditionalTransitionOption represents a single destination state, with a
   * given logical condition that must be true in order for the state to be
   * transitioned to.
   */
  public static final class ConditionalTransitionOption extends TransitionOption {
    private Logic condition;
  }

  /**
   * Conditional transitions will transition to one of several possible states
   * based on conditional logic. A conditional_transition consists of an array of
   * condition/transition pairs which are tested in the order they are defined.
   * The first condition that evaluates to true will result in a transition to its
   * corresponding transition state. The last element in the condition_transition
   * array may contain only a transition (with no condition) to indicate a
   * "fallback transition" when all other conditions are false. If none of the
   * conditions evaluated to true, and no fallback transition was specified, the
   * module will transition to the last transition defined.
   */
  public static class ConditionalTransition extends Transition {
    private List<ConditionalTransitionOption> transitions;

    public ConditionalTransition(List<ConditionalTransitionOption> transitions) {
      this.transitions = transitions;
    }

    @Override
    public String follow(Person person, long time) {
      for (ConditionalTransitionOption option : transitions) {
        if (option.condition == null || option.condition.test(person, time)) {
          return option.transition;
        }
      }

      // fallback, just return the last transition
      TransitionOption last = transitions.get(transitions.size() - 1);
      return last.transition;
    }

  }

  /**
   * A DistributedTransitionOption represents a transition option with any of: - a
   * single state to transition to, - a condition that must be true, or - a set of
   * distributions and transitions.
   */
  public static final class ComplexTransitionOption extends TransitionOption {
    private Logic condition;
    private List<DistributedTransitionOption> distributions;
  }

  /**
   * Complex transitions are a combination of direct, distributed, and conditional
   * transitions. A complex_transition consists of an array of
   * condition/transition pairs which are tested in the order they are defined.
   * The first condition that evaluates to true will result in a transition based
   * on its corresponding transition or distributions. If the module defines a
   * transition, it will transition directly to that named state. If the module
   * defines distributions, it will then transition to one of these according to
   * the same rules as the distributed_transition. See Distributed for more
   * detail. The last element in the complex_transition array may omit the
   * condition to indicate a fallback transition when all other conditions are
   * false. If none of the conditions evaluated to true, and no fallback
   * transition was specified, the module will transition to the last transition
   * defined.
   */
  public static class ComplexTransition extends Transition {
    private List<ComplexTransitionOption> transitions;

    public ComplexTransition(List<ComplexTransitionOption> transitions) {
      this.transitions = transitions;
    }

    @Override
    public String follow(Person person, long time) {
      for (ComplexTransitionOption option : transitions) {
        if (option.condition == null || option.condition.test(person, time)) {
          return follow(option, person);
        }
      }

      // fallback, just return the last transition
      ComplexTransitionOption last = transitions.get(transitions.size() - 1);
      return follow(last, person);
    }

    private String follow(ComplexTransitionOption option, Person person) {
      if (option.transition != null) {
        return option.transition;
      } else if (option.distributions != null) {
        return pickDistributedTransition(option.distributions, person);
      }
      throw new IllegalArgumentException("Complex Transition must have either transition or distributions");
    }
  }

  private static String pickDistributedTransition(List<DistributedTransitionOption> transitions, Person person) {
    double p = person.rand();
    double high = 0.0;
    for (DistributedTransitionOption option : transitions) {
      processDistributedTransition(option);
      if (option.numericDistribution != null) {
        high += option.numericDistribution;
      } else {
        NamedDistribution nd = option.namedDistribution;
        double dist = nd.defaultDistribution;
        if (person.attributes.containsKey(nd.attribute)) {
          dist = (Double) person.attributes.get(nd.attribute);
        }

        high += dist;
      }

      if (p < high) {
        return option.transition;
      }
    }
    // fallback, just return the last transition
    TransitionOption last = transitions.get(transitions.size() - 1);
    return last.transition;
  }

  private static void processDistributedTransition(DistributedTransitionOption option) {
    if (option.numericDistribution != null || option.namedDistribution != null) {
      return;
    }

    if (option.distribution instanceof Double) {
      option.numericDistribution = (Double) option.distribution;
    } else {
      @SuppressWarnings("unchecked")
      LinkedTreeMap<String, Object> map = (LinkedTreeMap<String, Object>) option.distribution;
      option.namedDistribution = new NamedDistribution(map);
    }
  }

  /**
   * Helper class for distributions, which may either be a double, or a
   * NamedDistribution with an attribute to fetch the desired probability from and
   * a default.
   */
  public static class NamedDistribution {
    public String attribute;
    public double defaultDistribution;

    public NamedDistribution(JsonObject definition) {
      this.attribute = definition.get("attribute").getAsString();
      this.defaultDistribution = definition.get("default").getAsDouble();
    }

    public NamedDistribution(LinkedTreeMap<String, ?> definition) {
      this.attribute = (String) definition.get("attribute");
      this.defaultDistribution = (Double) definition.get("default");
    }
  }
}