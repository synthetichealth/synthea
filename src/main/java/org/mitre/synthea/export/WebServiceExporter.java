package org.mitre.synthea.export;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.security.KeyManagementException;
import java.security.NoSuchAlgorithmException;
import java.security.cert.X509Certificate;

import javax.net.ssl.HostnameVerifier;
import javax.net.ssl.HttpsURLConnection;
import javax.net.ssl.SSLContext;
import javax.net.ssl.SSLSession;
import javax.net.ssl.TrustManager;
import javax.net.ssl.X509TrustManager;

import org.mitre.synthea.helpers.Config;

public class WebServiceExporter {
	public static int POSTRequest(String ResourceType, String ResourceToPost) throws IOException, NoSuchAlgorithmException, KeyManagementException {
		URL url = new URL(Config.get("exporter.fhir.webservice.endpoint") + ResourceType);
		
		if(Boolean.parseBoolean(Config.get("exporter.fhir.webservice.disablecertvalidation"))) {
			// Install the all-trusting trust manager
			SSLContext sc = SSLContext.getInstance("SSL");
	        sc.init(null, trustAllCerts, new java.security.SecureRandom());
	        HttpsURLConnection.setDefaultSSLSocketFactory(sc.getSocketFactory());
	     // Install the all-trusting host verifier
	        HttpsURLConnection.setDefaultHostnameVerifier(allHostsValid);
		}
		HttpsURLConnection postConnection = (HttpsURLConnection) url.openConnection();
		postConnection.setRequestMethod("POST");
		// postConnection.setRequestProperty("userId", "a1bcdefgh");
		postConnection.setRequestProperty("Content-Type", "application/json");
		postConnection.setDoOutput(true);
		OutputStream os = postConnection.getOutputStream();
		os.write(ResourceToPost.getBytes());
		os.flush();
		os.close();
		int responseCode = postConnection.getResponseCode();
		//System.out.println("POST Response Code :  " + responseCode);
		//System.out.println("POST Response Message : " + postConnection.getResponseMessage());
		if (responseCode == HttpURLConnection.HTTP_CREATED) { // success
			BufferedReader in = new BufferedReader(new InputStreamReader(postConnection.getInputStream()));
			String inputLine;
			StringBuffer response = new StringBuffer();
			while ((inputLine = in.readLine()) != null) {
				response.append(inputLine);
			}
			in.close();
			// print result
			// System.out.println(response.toString());
		} else {
			// System.out.println("POST NOT WORKED");
		}
		return responseCode;
	}
	
	// Create a trust manager that does not validate certificate chains
    static TrustManager[] trustAllCerts = new TrustManager[] {new X509TrustManager() {
            public java.security.cert.X509Certificate[] getAcceptedIssuers() {
                return null;
            }
            public void checkClientTrusted(X509Certificate[] certs, String authType) {
            }
            public void checkServerTrusted(X509Certificate[] certs, String authType) {
            }
        }
    };
    
 // Create all-trusting host name verifier
    static HostnameVerifier allHostsValid = new HostnameVerifier() {
        public boolean verify(String hostname, SSLSession session) {
            return true;
        }
    };
}