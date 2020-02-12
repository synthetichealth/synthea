package org.mitre.synthea.helpers;

import com.google.gson.Gson;
import org.apache.http.HttpResponse;
import org.apache.http.client.ClientProtocolException;
import org.apache.http.client.HttpClient;
import org.apache.http.client.methods.HttpPost;
import org.apache.http.entity.StringEntity;
import org.apache.http.impl.client.HttpClients;
import org.apache.http.util.EntityUtils;
import org.mitre.synthea.world.agents.Person;
import org.mitre.synthea.world.concepts.HealthRecord;

import java.io.IOException;
import java.io.UnsupportedEncodingException;
import java.util.LinkedList;
import java.util.List;


public class ClinicalNoteService {
  public static String NOTE_SERVICE_URL = "";
  public static String NOTE_TYPE = "ooGENERALoo";

  public static class RequestBody {
    public String noteType;
    public int age;
    public String gender;
    public String ethnicity;
    public String doctorName;
    public List<String> diagnosis;
    public List<String> procedures;
    public List<String> drugs;

    public RequestBody(Person person, long time) {
      this.noteType = NOTE_TYPE;
      this.age = person.ageInYears(time);
      this.gender = (String) person.attributes.get(Person.GENDER);
      this.ethnicity = (String) person.attributes.get(Person.ETHNICITY);
      this.diagnosis = new LinkedList();
      this.procedures = new LinkedList();
      this.drugs = new LinkedList();

      HealthRecord record = person.record;
      if (record.provider != null) {
        this.doctorName = record.provider.name;
      }
      record.encounters.forEach(encounter -> {
        encounter.conditions.forEach(condition -> {
          if (!this.diagnosis.contains(condition.name)) {
            this.diagnosis.add(condition.name);
          }
        });
        encounter.procedures.forEach(procedure -> {
          if(!this.procedures.contains(procedure.name)) {
            this.procedures.add(procedure.name);
          }
        });
        encounter.medications.forEach(medication -> {
          if(!this.drugs.contains(medication.name)) {
            this.drugs.add(medication.name);
          }
        });
      });
    }

    public String toJSON() {
      Gson gson = new Gson();
      return gson.toJson(this);
    }
  }

  public static void generateNote(Person person, long time) {
    HttpClient client = HttpClients.createDefault();
    RequestBody requestBody = new RequestBody(person, time);
    HttpPost post = new HttpPost(NOTE_SERVICE_URL);
    try {
      post.setEntity(new StringEntity(requestBody.toJSON()));
      HttpResponse response = client.execute(post);
      String note = EntityUtils.toString(response.getEntity());
      person.record.note = new HealthRecord.Note(note, time);
    } catch (UnsupportedEncodingException e) {
      throw new RuntimeException(e);
    } catch (ClientProtocolException e) {
      throw new RuntimeException(e);
    } catch (IOException e) {
      throw new RuntimeException(e);
    }

  }
}
