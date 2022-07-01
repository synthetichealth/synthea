package org.mitre.synthea.helpers;

import java.io.Serializable;
import java.util.Random;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicLong;

/**
 * A default implementation of the RandomNumberGenerator interface.
 * The goal is to isolate sources of randomness by consolidating the use of java.util.Random
 * or other sources of randomness for traceability.
 */
public class DefaultRandomNumberGenerator implements RandomNumberGenerator, Serializable {

  private long seed;
  private Random random;
  private AtomicLong count;

  /**
   * Create a new default random number generator.
   * @param seed The random number generator seed.
   */
  public DefaultRandomNumberGenerator(long seed) {
    this.seed = seed;
    random = new Random(this.seed);
    count = new AtomicLong();
  }

  public long getSeed() {
    return this.seed;
  }

  @Override
  public double rand() {
    count.incrementAndGet();
    return random.nextDouble();
  }

  @Override
  public boolean randBoolean() {
    count.incrementAndGet();
    return random.nextBoolean();
  }

  @Override
  public double randGaussian() {
    count.incrementAndGet();
    return random.nextGaussian();
  }

  @Override
  public int randInt() {
    count.incrementAndGet();
    return random.nextInt();
  }

  @Override
  public int randInt(int bound) {
    count.incrementAndGet();
    return random.nextInt(bound);
  }

  @Override
  public long randLong() {
    count.incrementAndGet();
    return random.nextLong();
  }

  @Override
  public UUID randUUID() {
    return new UUID(randLong(), randLong());
  }

  @Override
  public long getCount() {
    return count.get();
  }

}
