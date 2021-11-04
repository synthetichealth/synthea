package org.mitre.synthea.helpers;

import java.util.List;

import org.apache.commons.math3.distribution.EnumeratedDistribution;
import org.apache.commons.math3.exception.MathArithmeticException;
import org.apache.commons.math3.exception.NotANumberException;
import org.apache.commons.math3.exception.NotFiniteNumberException;
import org.apache.commons.math3.exception.NotPositiveException;
import org.apache.commons.math3.util.Pair;
import org.mitre.synthea.world.agents.Person;

/**
 * Class that wraps EnumeratedDistribution for thread safe use in Synthea. In several places in
 * Synthea, EnumeratedDistributions are used following a
 * <a href="https://en.wikipedia.org/wiki/Singleton_pattern">singleton pattern</a>. One distribution
 * is used in a particular aspect of simulation. To support reproducibility of simulations, its
 * source of randomness is reseeded for each individual. Since multiple threads may be accessing
 * the distribution at the same time, it would be possible to reseed the distribution in one thread
 * but sample on that seed in another thread. This class offers a synchronized method to prevent
 * that with the handy side benefit that it takes the well-known and loved Synthea Person as a
 * source of randomness.
 * @param <T> The type to be returned when sampling from the distribiution.
 */
public class SyncedEnumeratedDistro<T> extends EnumeratedDistribution {
  /**
   * Just calls super. Look at the docs for EnumeratedDistributed for more details.
   * @param pmf List of pairs of values and their weight in the distribution.
   */
  public SyncedEnumeratedDistro(List<Pair<T, Double>> pmf) throws NotPositiveException,
      MathArithmeticException, NotFiniteNumberException, NotANumberException {
    super(pmf);
  }

  /**
   * Sample from the distribution after it has been reseeded using the Person provided as a source
   * of randomness.
   * @param person Where the randomness comes from
   * @return a value from the distribution based on a weighted, random selection
   */
  public synchronized T syncedReseededSample(Person person) {
    reseedRandomGenerator(person.randLong());
    return (T) sample();
  }
}
