package org.mitre.synthea;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;

import org.mitre.synthea.world.agents.Person;

public class ParallelTestingService {
  /**
   * Runs the provided PersonTester in parallel. The PersonTester will be supplied with 10 people.
   * Tests are run in a fixed thread pool with some of the tests having to wait until the first few
   * tests complete. Tests can return a List of validation errors (in String form).
   * @param pt An implementation of PersonTester
   * @return A list of errors
   * @throws Exception when bad things happen during the test
   */
  public static List<String> runInParallel(PersonTester pt) throws Exception {
    ExecutorService service = Executors.newFixedThreadPool(6);
    List<String> validationErrors = new ArrayList<>();
    int numberOfPeople = 10;
    List<Future<Exception>> potentialCrashes = new ArrayList<>(10);
    Person[] people = TestHelper.getGeneratedPeople();
    for (int i = 0; i < numberOfPeople; i++) {
      Person person = people[i];
      final int counter = i;
      Future<Exception> maybeCrash = service.submit(() -> {
        long start = System.currentTimeMillis();
        System.out.println(String.format("Starting person %d at %d", counter, start));
        try {
          validationErrors.addAll(pt.test(person));
          return null;
        } catch (Exception e) {
          return e;
        } finally {
          long end = System.currentTimeMillis();
          long duration = end - start;
          System.out.println(String.format("Finished %d at %d, which took %d", counter,
              end, duration));
        }
      });
      potentialCrashes.add(i, maybeCrash);
    }
    service.shutdown();
    service.awaitTermination(1, TimeUnit.HOURS);
    for (int i = 0; i < potentialCrashes.size(); i++) {
      Future<Exception> potentalCrash = potentialCrashes.get(i);
      Exception e = potentalCrash.get();
      if (e != null) {
        throw e;
      }
    }
    return validationErrors;
  }
}