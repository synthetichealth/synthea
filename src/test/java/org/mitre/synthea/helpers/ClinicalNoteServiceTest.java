package org.mitre.synthea.helpers;

import okhttp3.mockwebserver.MockResponse;
import okhttp3.mockwebserver.MockWebServer;
import org.junit.Test;
import org.mitre.synthea.engine.Generator;
import org.mitre.synthea.world.agents.Person;

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


}