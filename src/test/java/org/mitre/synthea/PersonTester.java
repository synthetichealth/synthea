package org.mitre.synthea;

import org.mitre.synthea.world.agents.Person;

import java.util.List;

public interface PersonTester {
  List<String> test(Person person) throws Exception;
}
