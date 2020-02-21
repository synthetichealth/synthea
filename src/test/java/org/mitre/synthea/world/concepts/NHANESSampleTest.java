package org.mitre.synthea.world.concepts;

import org.junit.Test;

import java.util.List;

import static org.junit.Assert.*;

public class NHANESSampleTest {

  @Test
  public void loadSamples() {
    List<NHANESSample> list = NHANESSample.loadSamples();
    NHANESSample first = list.get(0);
    assertEquals(11.9, first.wt, 0.001);
  }
}