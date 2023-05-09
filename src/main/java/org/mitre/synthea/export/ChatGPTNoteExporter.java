package org.mitre.synthea.export;

import com.google.gson.Gson;

import okhttp3.MediaType;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.RequestBody;
import okhttp3.Response;
import org.mitre.synthea.helpers.Config;
import org.mitre.synthea.modules.DeathModule;
import org.mitre.synthea.world.agents.Person;
import org.mitre.synthea.world.concepts.HealthRecord;

import java.io.IOException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.*;

/**
 * This class works with the OpenAI API to use ChatGPT to generate/enhance clinical notes.
 * Actual prompt generation takes place in the ClinicalNoteExporter.
 * Because the OpenAI API will throttle requests, this class imposes rate limiting. Calls to the
 * export method are queued in a single thread request executor.
 */
public class ChatGPTNoteExporter {
  public static final String OPEN_AI_EDIT_ENDPOINT = "https://api.openai.com/v1/chat/completions";
  public static final String OPEN_AI_MODEL = "gpt-3.5-turbo";
  private static final String AUTH_HEADER = "Authorization";
  public static final MediaType JSON
          = MediaType.get("application/json; charset=utf-8");
  public static final ExecutorService requester = Executors.newSingleThreadExecutor();

  /**
   * Generate a clinical note for a person by creating a prompt based on information in the
   * simulation and sending it to ChatGPT.
   * @param person The person to generate the note for
   * @return Plain text clinical note
   * @throws ExecutionException if the process is interrupted
   * @throws InterruptedException if the process is interrupted
   */
  public static String export(Person person, HealthRecord.Encounter encounter)
          throws ExecutionException, InterruptedException {

    String templatedNote;
    if (encounter.codes != null && encounter.codes.contains(DeathModule.DEATH_CERTIFICATION)) {
      templatedNote = ClinicalNoteExporter.chatGPTDeathCertificatePrompt(person, encounter);
    } else {
      templatedNote = ClinicalNoteExporter.chatGPTEncounterPrompt(person, encounter);
    }

    Future<String> chatGPTNote = requester.submit(() -> {
      String apiKey = Config.get("openai.apikey");
      OkHttpClient client = new OkHttpClient.Builder()
              .readTimeout(2, TimeUnit.MINUTES)
              .build();

      String requestBodyJSON = createRequestBodyJSON(templatedNote);
      RequestBody body = RequestBody.create(requestBodyJSON, JSON);
      Request request = new Request.Builder()
              .url(OPEN_AI_EDIT_ENDPOINT)
              .post(body)
              .header(AUTH_HEADER, String.format("Bearer %s", apiKey))
              .build();
      try (Response response = client.newCall(request).execute()) {
        String rawJSONResponse = response.body().string();
        return extractResponseFromJson(rawJSONResponse);
      } catch (IOException ioe) {
        throw new RuntimeException("Error calling the OpenAI API", ioe);
      }
    });
    return chatGPTNote.get();
  }

  /**
   * Parse the JSON response from the OpenAI API
   * @param response The raw JSON
   * @return the first ChatGPT generated text response
   */
  public static String extractResponseFromJson(String response) {
    Gson gson = new Gson();
    CompletionResponse cr = gson.fromJson(response, CompletionResponse.class);
    return cr.choices.get(0).message.content;
  }

  private static String createRequestBodyJSON(String prompt) {
    Map<String, Object> requestBody = new HashMap<>();
    requestBody.put("model", OPEN_AI_MODEL);
    Message message = new Message();
    message.role = "user";
    message.content = prompt;
    ArrayList<Message> messages = new ArrayList<>();
    messages.add(message);
    requestBody.put("messages", messages);
    Gson gson = new Gson();
    return gson.toJson(requestBody);
  }

  /**
   * Model of the OpenAI API response JSON.
   */
  public static class CompletionResponse {
    public String object;
    public Integer created;
    public List<Choice> choices;
    public Usage usage;
  }

  public static class Choice {
    public Message message;
    public Integer index;
  }

  public static class Message {
    public String role;
    public String content;

  }

  public static class Usage {
    public Integer promptTokens;
    public Integer completionTokens;
    public Integer totalTokens;
  }
}
