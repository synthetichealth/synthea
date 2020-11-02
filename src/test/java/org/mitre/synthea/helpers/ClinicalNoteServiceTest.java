package org.mitre.synthea.helpers;

import okhttp3.mockwebserver.MockResponse;
import okhttp3.mockwebserver.MockWebServer;
import org.junit.Test;
import org.mitre.synthea.engine.Generator;
import org.mitre.synthea.world.agents.Person;
import org.mitre.synthea.world.geography.Demographics;

import java.io.IOException;

import static org.junit.Assert.*;

public class ClinicalNoteServiceTest {

  @Test
  public void generateNote() throws IOException {
    MockWebServer server = new MockWebServer();
    String testText = "Patient is alive and well";
    server.enqueue(new MockResponse().setBody(testText));
    server.start();
    Generator generator = new Generator();
    Person p = generator.generatePerson(0);
    ClinicalNoteService.noteServiceUrl = server.url("/note_service");
    ClinicalNoteService.generateNote(p, System.currentTimeMillis());
    assertEquals(testText, p.record.note.text);
  }


  @Test
  public void replaceNoteTokens() {
    Person p = new Person(0l);
    p.attributes.put(Person.FIRST_NAME, "Frodo");
    p.attributes.put(Person.LAST_NAME, "Baggins");
    p.attributes.put(Person.ADDRESS, "1 Bagg End.");
    p.attributes.put(Person.CITY, "The Shire");
    String templatedNote = "Saw ooNAMEoo in the ED due to Ring Wraith injury.";
    String expectedText = "Saw Frodo Baggins in the ED due to Ring Wraith injury.";
    assertEquals(expectedText, ClinicalNoteService.replaceNoteTokens(templatedNote, p, 0));

    templatedNote = "Saw ooNAMEoo who lives in ooLOCATIONoo in the ED due to Ring Wraith injury.";
    expectedText = "Saw Frodo Baggins who lives in The Shire in the ED due to Ring Wraith injury.";
    assertEquals(expectedText, ClinicalNoteService.replaceNoteTokens(templatedNote, p, 0));
    
  }
}