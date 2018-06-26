package org.mitre.synthea.helpers;

import java.util.Random;

import org.junit.Assert;
import org.junit.Test;

public class RandomCollectionTest {
  
  @SuppressWarnings("serial")
  private class Fixed extends Random {
    // nextDouble returns value between 0.0 (inclusive) and 1.0 (exclusive)
    private final double[] values = {0.0, 0.5, 0.999};
    private int index = 0;
    
    @Override
    public double nextDouble() {
      double returnValue = values[index];
      index++;
      if (index >= values.length) {
        index = 0;
      }
      return returnValue;
    }
  }

  @Test
  public void testZeroWeights() {
    RandomCollection<String> rc = new RandomCollection<String>();
    rc.add(0.0, "white");
    rc.add(1.0, "black");
    rc.add(0.0, "asian");
    
    Random random = new Random();
    for (int i = 0; i < 10; i++) {
      String randomString = rc.next(random);
      Assert.assertEquals("black", randomString);
    }
  }
  
  @Test
  public void testFixed() {
    Fixed fixed = new Fixed();
    double[] expected = {0.0, 0.5, 0.999, 0.0, 0.5, 0.999, 0.0, 0.5, 0.999};
    for (int i = 0; i < expected.length; i++) {
      Assert.assertTrue(fixed.nextDouble() == expected[i]);
    }
  }
  
  @Test
  public void testFixedCounts() {
    RandomCollection<String> rc = new RandomCollection<String>();
    rc.add(0.33, "white");
    rc.add(0.33, "black");
    rc.add(0.33, "asian");
    
    int white = 0;
    int black = 0;
    int asian = 0;
    
    Fixed fixed = new Fixed();
    for (int i = 0; i < 9; i++) {
      String notRandomString = rc.next(fixed);
      switch (notRandomString) {
        case "white":
          white++;
          break;
        case "black":
          black++;
          break;
        case "asian":
          asian++;
          break;
        default:
          break;
      }
    }
    Assert.assertTrue(3 == white);
    Assert.assertTrue(3 == black);
    Assert.assertTrue(3 == asian);
  }

}
