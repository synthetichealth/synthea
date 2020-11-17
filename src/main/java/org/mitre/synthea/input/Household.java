package org.mitre.synthea.input;

import java.util.ArrayList;
import java.util.List;

import org.mitre.synthea.world.agents.Person;

public class Household {

    // The id of the household, corresponding to the household IDs of the input
    // records.
    public int id;
    // The initial person for the household. May be the "head of household" and
    // cannot be a null value.
    private Person firstAdult;
    // A second person for the household. May be null.
    private Person secondAdult;
    // The list of dependents in the household. May be empty.
    private List<Person> dependents;

    /**
     * Household Constructor.
     * 
     * @param id
     */
    public Household(int id) {
        this.id = id;
        this.dependents = new ArrayList<Person>();
    }

    /**
     * Adds an adult to the Household.
     * 
     * @param newAdult
     */
    public void addAdult(Person newAdult) {
        if (newAdult == null) {
            throw new RuntimeException("New adult of a household cannot be null.");
        } else if (this.firstAdult == null) {
            this.firstAdult = newAdult;
        } else if (this.secondAdult == null) {
            this.secondAdult = newAdult;
        } else {
            throw new RuntimeException(
                    "There can only be a max of 2 adults per household and a 3rd was added. Household already includes adults "
                            + this.firstAdult.attributes.get(Person.NAME) + " and "
                            + this.secondAdult.attributes.get(Person.NAME) + ". New adult "
                            + newAdult.attributes.get(Person.NAME) + " was attempted to be added.");
        }
    }

    /**
     * Adds a child to the dependents of the household. Should be called by the
     * HouseholdModule when it processes births to be added to the household.
     * 
     * @param newChild
     */
    public void addChild(Person newChild) {
        if (newChild == null) {
            throw new RuntimeException("New child of a household cannot be null.");
        }
        this.dependents.add(newChild);
    }

    /**
     * Get the list of adults in this household.
     */
    public List<Person> getAdults() {
        List<Person> adults = new ArrayList<Person>();
        if (firstAdult != null)
            adults.add(firstAdult);
        if (secondAdult != null)
            adults.add(secondAdult);
        return adults;
    }

    /**
     * Get the list of dependents in this household.
     */
    public List<Person> getDependents() {
        return this.dependents;
    }

    /**
     * Returns whether the given person exists in the current household.
     */
    public boolean includesPerson(Person person) {
        FixedRecord seedRecord = (FixedRecord) person.attributes.get(Person.SEED_RECORD);
        boolean matchesFirstAdult = (this.firstAdult == null) ? false : this.firstAdult.attributes.get(Person.SEED_RECORD).equals(seedRecord);
        boolean matchesSecondAdult = (this.secondAdult == null) ? false : this.firstAdult.attributes.get(Person.SEED_RECORD).equals(seedRecord);
        boolean matchesDependent = this.dependents.stream().anyMatch(dependent -> dependent.attributes.get(Person.SEED_RECORD).equals(seedRecord));
        System.out.println(matchesFirstAdult + " " + matchesSecondAdult + " " + matchesDependent);
        return matchesFirstAdult || matchesSecondAdult || matchesDependent;
    }

}
