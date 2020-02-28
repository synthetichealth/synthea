package org.mitre.synthea.helpers;

import com.google.gson.Gson;
import com.google.gson.annotations.SerializedName;
import okhttp3.HttpUrl;
import okhttp3.MediaType;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.RequestBody;
import okhttp3.Response;
import org.mitre.synthea.world.agents.Person;
import org.mitre.synthea.world.concepts.HealthRecord;

import java.io.IOException;
import java.util.LinkedList;
import java.util.List;
import java.util.concurrent.TimeUnit;

/**
 * This class is designed to interact with the RESTful notes generation service
 * of the GMHD project.
 */
public class ClinicalNoteService {
  public static HttpUrl noteServiceUrl = HttpUrl.parse(Config.get("generate.clinical_note_url",
      "http://127.0.0.1:4567/custom_input_note"));
  public static final String NOTE_TYPE = "ooGENERALoo";
  public static final MediaType JSON
      = MediaType.get("application/json; charset=utf-8");

  private static OkHttpClient client;

  /**
   * The body of the request to be sent to the GMHD service.
   */
  public static class NoteRequestBody {
    public String noteType;
    public int age;
    public String gender;
    public String ethnicity;
    public String doctorName;
    @SerializedName("marital_status")
    public String maritalStatus;
    public List<String> diagnosis;
    public List<String> procedures;
    public List<String> drugs;

    public NoteRequestBody(Person person, long time) {
      this.noteType = NOTE_TYPE;
      this.age = person.ageInYears(time);
      this.gender = (String) person.attributes.get(Person.GENDER);
      this.ethnicity = (String) person.attributes.get(Person.ETHNICITY);
      this.maritalStatus = (String) person.attributes.get(Person.MARITAL_STATUS);
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

  /**
   * Generate a note for the the supplied person at the time provided.
   * @param person To generate the note for
   * @param time Used to calculate the person's age for the note
   */
  public static void generateNote(Person person, long time) {
    if (client == null) {
      client = new OkHttpClient.Builder()
          .readTimeout(10, TimeUnit.MINUTES).build();
    }
    NoteRequestBody noteRequestBody = new NoteRequestBody(person, time);
    RequestBody body = RequestBody.create(noteRequestBody.toJSON(), JSON);
    Request request = new Request.Builder()
        .url(noteServiceUrl)
        .post(body)
        .build();
    try {
      Response response = client.newCall(request).execute();
      person.record.note = new HealthRecord.Note(response.body().string(), time);
    } catch (IOException e) {
      throw new RuntimeException(e);
    }
  }
}
