package primemover.util;

import org.apache.commons.lang3.StringUtils;
import org.json.JSONObject;
import org.openqa.selenium.InvalidArgumentException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;

import primemover.capture.RecordingProxy;
import primemover.model.Crawler;
import primemover.model.IAgent;
import primemover.model.MultiLoginAgent;
import primemover.model.MultiLoginIBrowserType;
import primemover.model.MultiLoginIOsType;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URL;

/**
 * 
 * @author dburkolter
 *
 *	Add a timeout for the request to MultiLogin. MultiLogin itself has no timeout for any request!
 */
public class MulitLoginBrowserProfile {
	
	private static Logger logger = LoggerFactory.getLogger(MulitLoginBrowserProfile.class);
	
	
	@Value("${mulitlogin.ApiWaitSeconds:10}")
	Long apiWaitSeconds = 10l;
	
	String profileId;
	
	
	/**
	 * Loads an existing profile
	 * 
	 * @param profileId: The profile-id to load
	 */
	public MulitLoginBrowserProfile(String profileId) {
		super();

		this.profileId = profileId;
	}
	
	
	/**
	 * Tests for an existing profile in the crawler. Creates a new profile if required
	 * 
	 * @param crawler
	 * @throws Exception 
	 */
	public MulitLoginBrowserProfile(Crawler crawler, RecordingProxy proxy) {
		super();

		IAgent agent = crawler.getAgent();
		
		if (!(agent instanceof MultiLoginAgent)) {
			throw new InvalidArgumentException("Only multilogin-agents are supported");
		}
		
		MultiLoginAgent typedAgent = (MultiLoginAgent) agent;
		String id = typedAgent.getMultiLoginId();
		
		if (!StringUtils.isEmpty(id)) {
			this.profileId = id;
			return; // done
		}
		
		// create a new profile
		try {
			this.profileId = createNewProfile(typedAgent, proxy);
		} catch (Exception ex) {
			logger.error("Error creating new profile id for agent " + typedAgent.getId(), ex);
			
			throw new IllegalStateException("profile was not created", ex);
		}
	}

	private void runApiWait() {
		if (apiWaitSeconds > 0) {
			logger.trace("make API wait of {} seconds", apiWaitSeconds);
			try {
				Thread.sleep(apiWaitSeconds * 1000);
			} catch (InterruptedException e) {
				// nothing to do
			}
		}
	}
	
	public String getProfileId() {
		return this.profileId;
	}
	
	public void updateProxy(RecordingProxy proxy) throws Exception {
		String url = "http://127.0.0.1:35000/api/v2/profile/" + this.profileId;

		URL obj = new URL(url);
		HttpURLConnection con = (HttpURLConnection) obj.openConnection();
		con.setConnectTimeout(10000);
		con.setReadTimeout(10000);

		// setup method
		con.setRequestMethod("POST");
		con.setRequestProperty("Content-Type", "application/json; utf-8");
		con.setDoOutput(true);
		
		// Request data
		String jsonInputString = "{\"network\": {" + 
				"    \"proxy\": {" + 
				"      \"type\": \"HTTP\"," + 
				"      \"host\": \"" + proxy.getIp() + "\"," + 
				"      \"port\": \"" + proxy.getPort() + "\"," + 
				"      \"username\": \"\"," + 
				"      \"password\": \"\"" + 
				"    }," +
				" \"dns\": []" +
				"}}";
		
		try(OutputStream os = con.getOutputStream()) {
		    byte[] input = jsonInputString.getBytes("utf-8");
		    os.write(input, 0, input.length);			
		} catch (Exception ex) {
			logger.warn("Exception writing proxy data to profile {}", this.profileId, ex);
			throw new IllegalStateException("Could not write proxy to MultiLogin - Service " + this.profileId, ex);
		}
				
		if (con.getResponseCode() != 204) {
			logger.warn("Could not update proxy on profile {}, MultiLogin response was {}: {}", this.profileId, con.getResponseCode(), con.getResponseMessage());
			throw new IllegalStateException("Proxy was not properly set on MultiLogin profile " + this.profileId);
		}
		
		runApiWait();
	}
	
	public Boolean isActive() throws Exception {
		// no API wait as this is requesting a state
		String url = "http://127.0.0.1:35000/api/v1/profile/active?profileId=" + this.profileId;

		
		URL obj = new URL(url);
		HttpURLConnection con = (HttpURLConnection) obj.openConnection();
		con.setConnectTimeout(10000);
		con.setReadTimeout(10000);
		con.setRequestMethod("GET");
		
		Integer responseCode = con.getResponseCode();

		if (responseCode == HttpURLConnection.HTTP_OK) {
			BufferedReader br = new BufferedReader(new InputStreamReader((con.getInputStream())));
			StringBuilder sb = new StringBuilder();
			String output;
			while ((output = br.readLine()) != null) {
			  sb.append(output);
			}
			logger.trace("isActive Response for {} is {}", this.profileId, sb.toString());
			
			
			// Get JSON text from the response and return the value by key "value"
			JSONObject jsonResponse = new JSONObject(sb.toString());
			Boolean resp =  jsonResponse.getBoolean("value");
			
			return resp;
		} else {
			return false;
		}
	}
	
	public Boolean stopProfile() throws Exception {
		String url = "http://127.0.0.1:35000/api/v1/profile/stop?profileId=" + this.profileId;

		
		URL obj = new URL(url);
		HttpURLConnection con = (HttpURLConnection) obj.openConnection();
		con.setConnectTimeout(10000);
		con.setReadTimeout(60000);
		con.setRequestMethod("GET");
		
		Integer responseCode = con.getResponseCode();

		if (responseCode == HttpURLConnection.HTTP_OK) {
			BufferedReader br = new BufferedReader(new InputStreamReader((con.getInputStream())));
			StringBuilder sb = new StringBuilder();
			String output;
			while ((output = br.readLine()) != null) {
			  sb.append(output);
			}
			logger.trace("stopProfile Response for {} is {}", this.profileId, sb.toString());
			
			
			// Get JSON text from the response and return the value by key "value"
			// JSONObject jsonResponse = new JSONObject(sb.toString());
			
			runApiWait();
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
		con.setConnectTimeout(5000);
		con.setReadTimeout(60000);
		
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
			runApiWait();
			return jsonResponse.getString("value");
		}
		
		throw new IllegalStateException("Could not load Mulitlogin profile");
	}
	
	public Boolean deleteProfile() throws Exception {
		String url = "http://127.0.0.1:35000/api/v2/profile/" + this.profileId;

		
		URL obj = new URL(url);
		HttpURLConnection con = (HttpURLConnection) obj.openConnection();
		con.setConnectTimeout(10000);
		con.setReadTimeout(60000);
		con.setRequestMethod("DELETE");
		
		Integer responseCode = con.getResponseCode();

		if (responseCode == 204) {
			runApiWait();
			return true;
		} else {
			return false;
		}
	}
	
	/* not supported yet to grab an individual profile
	public Boolean isValidProfile() throws Exception {
		String url = "http://127.0.0.1:35000/api/v2/profile/" + this.profileId;

		
		URL obj = new URL(url);
		HttpURLConnection con = (HttpURLConnection) obj.openConnection();
		con.setConnectTimeout(10000);
		con.setReadTimeout(60000);
		con.setRequestMethod("GET");
		
		Integer responseCode = con.getResponseCode();

		if (responseCode == HttpURLConnection.HTTP_OK) {
			return true;
		} else {
			return false;
		}
	}
	*/
	
	private String createNewProfile(MultiLoginAgent agent, RecordingProxy proxy) throws Exception{
		String url = "http://127.0.0.1:35000/api/v2/profile";		
		
		// default
		MultiLoginIOsType operatingSytem = MultiLoginIOsType.WINDOWS;
		MultiLoginIBrowserType browser = MultiLoginIBrowserType.MIMIC;
		
		String jsonInput = "{" + 
				" \"name\": \"" + agent.getName() + "_AgentId " + agent.getId() + "\"," +
				" \"os\": \"" + operatingSytem.getMultiLoginValue() + "\"," +
				" \"browser\": \"" + browser.getMultiLoginValue()+ "\"," + 
				" \"network\": {" + 
						"    \"proxy\": {" + 
						"      \"type\": \"HTTP\"," + 
						"      \"host\": \"" + proxy.getIp() + "\"," + 
						"      \"port\": \"" + proxy.getPort() + "\"," + 
						"      \"username\": \"\"," + 
						"      \"password\": \"\"" + 
						"    }," +
						" \"dns\": []" +
						"}" +
				"}";

		// setup method
		URL obj = new URL(url);
		HttpURLConnection con = (HttpURLConnection) obj.openConnection();
		con.setConnectTimeout(10000);
		con.setReadTimeout(60000);
		con.setRequestMethod("POST");
		con.setRequestProperty("Content-Type", "application/json; utf-8");
		con.setDoOutput(true);
				
		try(OutputStream os = con.getOutputStream()) {
		    byte[] input = jsonInput.getBytes("utf-8");
		    os.write(input, 0, input.length);			
		}		
		
		if (con.getResponseCode() != HttpURLConnection.HTTP_OK) {
			throw new IllegalStateException("Could not create browser profile for agent " + agent.getId());
		}
		
		
		// read in id of the profile
		String newProfileId = "";
		try (BufferedReader in = new BufferedReader(new InputStreamReader(con.getInputStream()));){
			String inputLine;
			StringBuffer response = new StringBuffer();

			while ((inputLine = in.readLine()) != null) {
				response.append(inputLine);
			}
			
			// Get JSON text from the response and return the value by key "value"
			JSONObject jsonResponse = new JSONObject(response.toString());
			newProfileId =  jsonResponse.getString("uuid");
		}	
		
		if (StringUtils.isEmpty(newProfileId)) {
			throw new IllegalStateException("no profile-id was created for agent " + agent.getId());
		}
		
		runApiWait();
		
		return newProfileId;
	}

}
