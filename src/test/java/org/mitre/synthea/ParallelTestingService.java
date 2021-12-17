package org.mitre.synthea;

import org.mitre.synthea.world.agents.Person;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;

public class ParallelTestingService {
  public static List<String> runInParallel(PersonTester pt) throws Exception {
    ExecutorService service = Executors.newFixedThreadPool(6);
    List<String> validationErrors = new ArrayList<>();
    int numberOfPeople = 10;
    TestHelper.getGeneratedPerson(1);
    List<Future<Exception>> potentialCrashes = new ArrayList<>(10);
    for (int i = 0; i < numberOfPeople; i++) {
      Person person = TestHelper.getGeneratedPerson(i);
      Future<Exception> maybeCrash = service.submit(() -> {
        try {
          validationErrors.addAll(pt.test(person));
          return null;
        } catch (Exception e) {
          return e;
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
