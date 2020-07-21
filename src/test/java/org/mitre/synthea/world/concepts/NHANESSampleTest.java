package org.mitre.synthea.world.concepts;

import static org.junit.Assert.assertEquals;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

import org.apache.commons.math3.distribution.EnumeratedDistribution;
import org.junit.Test;

public class NHANESSampleTest {

  @Test
  public void loadSamples() {
    List<NHANESSample> list = NHANESSample.loadSamples();
    NHANESSample first = list.get(0);
    assertEquals(11.9, first.wt, 0.001);
  }

  @Test
  public void predictableSamplesSingleThread() {
    EnumeratedDistribution<NHANESSample> nhanesSamples = NHANESSample.loadDistribution();

    List<NHANESSample> foo = new ArrayList<NHANESSample>();
    List<NHANESSample> bar = new ArrayList<NHANESSample>();

    for (int i = 0; i < 10; i++) {
      nhanesSamples.reseedRandomGenerator(0L);
      foo.add(nhanesSamples.sample());

      nhanesSamples.reseedRandomGenerator(9L);
      bar.add(nhanesSamples.sample());
    }

    for (int j = 1; j < foo.size(); j++) {
      assertEquals(foo.get(j - 1).toString(), foo.get(j).toString());
      assertEquals(bar.get(j - 1).toString(), bar.get(j).toString());
    }
  }

  @Test
  public void predictableSamplesMultiThread() throws InterruptedException {
    EnumeratedDistribution<NHANESSample> nhanesSamples = NHANESSample.loadDistribution();
    ExecutorService threadPool = Executors.newFixedThreadPool(8);
    List<List<NHANESSample>> results = new ArrayList<List<NHANESSample>>();

    for (int i = 0; i < 10; i++) {
      results.add(new ArrayList<NHANESSample>());
    }

    for (int i = 0; i < 10; i++) {
      final int k = i;
      threadPool.submit(() -> {
        for (int j = 0; j < 1000; j++) {
          results.get(k).add(threadSample(nhanesSamples, j));
        }
      });
    }

    threadPool.shutdown();
    while (!threadPool.awaitTermination(30, TimeUnit.SECONDS)) { /* wait */ }

    for (int i = 1; i < results.size(); i++) {
      for (int j = 0; j < results.get(i).size(); j++) {
        assertEquals(results.get(i - 1).get(j).toString(), results.get(i).get(j).toString());
      }
    }
  }

  private NHANESSample threadSample(EnumeratedDistribution<NHANESSample> nhanesSamples, long seed) {
    synchronized (nhanesSamples) {
      nhanesSamples.reseedRandomGenerator(seed);
      return nhanesSamples.sample();
    }
  }
}