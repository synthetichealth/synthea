package org.mitre.synthea;

import java.util.List;

import org.mitre.synthea.world.agents.Person;

/**
 * Single method interface (so it can be implemented using a lambda), for testing people in
 * parallel. This interface is intended to be used with the ParallelTestingService. It is currently
 * used for testing various exporters.
 */
public interface PersonTester {
  List<String> test(Person person) throws Exception;
}
