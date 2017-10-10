package org.mitre.synthea.modules;

import static org.junit.Assert.assertEquals;

import org.junit.Test;

public class GrowthChartTest {
    @Test public void testGrowthChartLookupMin() throws Exception {
    	double height = LifecycleModule.lookupGrowthChart("height", "M", 20, 0.0);
        assertEquals(78.06971429, height, 0.01);
    }

    @Test public void testGrowthChartLookupMiddle() throws Exception {
    	double height = LifecycleModule.lookupGrowthChart("height", "M", 20, 0.6);
        assertEquals(84.24783394, height, 0.01);
    }
    
    @Test public void testGrowthChartLookupMax() throws Exception {
    	double height = LifecycleModule.lookupGrowthChart("height", "M", 20, 1.0);
        assertEquals(90.68153436, height, 0.01);
    }
}
