package org.mitre.synthea.engine;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;
import static org.mockito.Mockito.withSettings;

import java.nio.file.Paths;
import java.util.LinkedList;

import org.junit.Before;
import org.junit.BeforeClass;
import org.junit.Test;
import org.mitre.synthea.helpers.Config;
import org.mitre.synthea.helpers.Utilities;
import org.mitre.synthea.world.agents.PayerManager;
import org.mitre.synthea.world.agents.Person;
import org.mitre.synthea.world.agents.Provider;
import org.mitre.synthea.world.concepts.HealthRecord;
import org.mitre.synthea.world.concepts.HealthRecord.EncounterType;
import org.mitre.synthea.world.geography.Location;
import org.mockito.Mockito;

public class DentalDeviceEndTest {
  private static final long TIME = 1577836800000L;
  private static Module dentures;
  private static Module dentalExam;
  private Person person;

  /**
   * Load the production dental modules.
   * @throws Exception if a module cannot be loaded
   */
  @BeforeClass
  public static void loadModules() throws Exception {
    dentures = Module.loadFile(Paths.get("modules", "dentures.json"), false, null, false);
    dentalExam = Module.loadFile(Paths.get("modules", "dental_and_oral_examination.json"),
        false, null, false);
    PayerManager.loadPayers(new Location(Config.get("test_state.default", "Massachusetts"), null));
  }

  /**
   * Create a 70 year old patient with a wellness provider and no insurance.
   */
  @Before
  public void setup() {
    person = new Person(0L);
    person.history = new LinkedList<>();
    Provider provider = Mockito.mock(Provider.class, withSettings().serializable());
    Mockito.when(provider.getResourceID()).thenReturn("Mock-UUID");
    person.setProvider(EncounterType.WELLNESS, provider);
    long birthTime = TIME - Utilities.convertTime("years", 70);
    person.attributes.put(Person.BIRTHDATE, birthTime);
    person.coverage.setPlanToNoInsurance(birthTime);
    person.coverage.setPlanToNoInsurance(TIME + Utilities.convertTime("years", 1));
  }

  private void assertDeviceEndStopsDevice(Module module, String deviceState,
      String deviceEndState) {
    assertTrue(module.getState(deviceState).clone().process(person, TIME));
    HealthRecord.Device device = person.record.currentEncounter(TIME).devices.get(0);
    long end = TIME + Utilities.convertTime("minutes", 30);
    assertTrue(module.getState(deviceEndState).clone().process(person, end));
    assertEquals(deviceEndState + " should stop the device from " + deviceState,
        end, device.stop);
  }

  @Test
  public void denturesXraySystemIsRemoved() {
    assertDeviceEndStopsDevice(dentures, "Dental_x_ray_system", "Finish_Dental_x_ray_system");
  }

  @Test
  public void denturesOperatingRoomIsRemoved() {
    assertDeviceEndStopsDevice(dentures, "Operating_room", "Exit_Operating_room");
  }

  @Test
  public void dentalExamOperatingRoomIsRemoved() {
    assertDeviceEndStopsDevice(dentalExam, "Operating_room", "Exit_Operating_room");
  }

  @Test
  public void dentalExamSecondOperatingRoomIsRemoved() {
    assertDeviceEndStopsDevice(dentalExam, "Operating_room_2", "Exit_Operating_room_2");
  }
}
