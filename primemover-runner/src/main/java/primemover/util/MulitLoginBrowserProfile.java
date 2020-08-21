package primemover.util;

import org.json.JSONObject;

import primemover.capture.RecordingProxy;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URL;

public class MulitLoginBrowserProfile {
	String profileId;

	public MulitLoginBrowserProfile(String profileId) {
		super();

		this.profileId = profileId;
	}

	public void updateProxy(RecordingProxy proxy) throws Exception {
		String url = "http://127.0.0.1:35000/api/v2/profile/" + this.profileId;

		URL obj = new URL(url);
		HttpURLConnection con = (HttpURLConnection) obj.openConnection();

		// setup method
		con.setRequestMethod("POST");
		con.setRequestProperty("Content-Type", "application/json; utf-8");
		con.setDoOutput(true);
		
		// Request data
		String jsonInputString = "{\"network\": {\r\n" + 
				"    \"proxy\": {\r\n" + 
				"      \"type\": \"HTTP\",\r\n" + 
				"      \"host\": \"" + proxy.getIp() + "\",\r\n" + 
				"      \"port\": \"" + proxy.getPort() + "\",\r\n" + 
				"      \"username\": \"\",\r\n" + 
				"      \"password\": \"\"\r\n" + 
				"    }," +
				" \"dns\": []" +
				"}}";
		
		try(OutputStream os = con.getOutputStream()) {
		    byte[] input = jsonInputString.getBytes("utf-8");
		    os.write(input, 0, input.length);			
		}
		
		
		if (con.getResponseCode() != 204) {
			throw new IllegalStateException("Proxy was not properly set on MultiLogin profile " + this.profileId);
		}	
	}
	
	public Boolean isActive() throws Exception {
		String url = "http://127.0.0.1:35000/api/v1/profile/active?profileId=" + this.profileId;

		
		URL obj = new URL(url);
		HttpURLConnection con = (HttpURLConnection) obj.openConnection();
		con.setRequestMethod("GET");
		
		Integer responseCode = con.getResponseCode();

		if (responseCode == HttpURLConnection.HTTP_OK) {
			return true;
		} else {
			return false;
		}
	}
	
	public Boolean stopProfile() throws Exception {
		String url = "http://127.0.0.1:35000/api/v1/profile/stop?profileId=" + this.profileId;

		
		URL obj = new URL(url);
		HttpURLConnection con = (HttpURLConnection) obj.openConnection();
		con.setRequestMethod("GET");
		
		Integer responseCode = con.getResponseCode();

		if (responseCode == HttpURLConnection.HTTP_OK) {
			return true;
		} else {
			return false;
		}
	}
	
	public String startProfile() throws Exception {

		/*
		 * Send GET request to start the browser profile by profileId. Returns response
		 * in the following format: '{"status":"OK","value":"http://127.0.0.1:XXXXX"}',
		 * where XXXXX is the localhost port on which browser profile is launched.
		 * Please make sure that you have Multilogin listening port set to 35000.
		 * Otherwise please change the port value in the url string
		 */
		String url = "http://127.0.0.1:35000/api/v1/profile/start?automation=true&profileId=" + this.profileId;

		
		URL obj = new URL(url);
		HttpURLConnection con = (HttpURLConnection) obj.openConnection();
		con.setRequestMethod("GET");
		
		Integer responseCode = con.getResponseCode();

		if (responseCode == HttpURLConnection.HTTP_OK) {
			BufferedReader in = new BufferedReader(new InputStreamReader(con.getInputStream()));
			String inputLine;
			StringBuffer response = new StringBuffer();
	
			while ((inputLine = in.readLine()) != null) {
				response.append(inputLine);
			}
			in.close();
	
			// Get JSON text from the response and return the value by key "value"
			JSONObject jsonResponse = new JSONObject(response.toString());
			return jsonResponse.getString("value");
		}
		
		throw new IllegalStateException("Could not load Mulitlogin profile");
	}

}
