import java.io.IOException;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;

public class PopulateData {
    public static void main(String[] args) {
        String url = "your FHIR server endpoint ending with /fhir/";
        String dataDirectory = "./output/fhir";

        try {
            Files.walk(Paths.get(dataDirectory))
                    .filter(Files::isRegularFile)
                    .forEach(file -> {
                        String fileName = file.getFileName().toString();
                        if (fileName.startsWith("hospital") && fileName.endsWith(".json") ||
                                fileName.startsWith("practitioner") && fileName.endsWith(".json")) {
                            try {
                                String data = Files.readString(file);
                                sendPostRequest(url, data);
                                System.out.println(fileName);
                            } catch (IOException e) {
                                e.printStackTrace();
                            }
                        }
                    });
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    private static void sendPostRequest(String url, String data) {
        try {
            URL urlObj = new URL(url);
            HttpURLConnection con = (HttpURLConnection) urlObj.openConnection();
            con.setRequestMethod("POST");
            con.setRequestProperty("Content-Type", "application/fhir+json;charset=utf-8");
            con.setDoOutput(true);
            con.getOutputStream().write(data.getBytes("UTF-8"));
            con.getOutputStream().close();

            if (con.getResponseCode() != 200) {
                throw new IOException("Wrong status code: " + con.getResponseCode());
            }

            con.disconnect();
        } catch (IOException e) {
            e.printStackTrace();
        }
    }
}
