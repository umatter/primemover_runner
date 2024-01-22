package primemover.multilogin;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.apache.commons.lang3.StringUtils;
import org.json.JSONObject;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;

import primemover.capture.RecordingProxy;
import primemover.model.MultiLoginAgent;
import primemover.model.MultiLoginIBrowserType;
import primemover.model.MultiLoginIOsType;

/**
 * 
 * @author dburkolter
 *
 *         Add a timeout for the request to MultiLogin. MultiLogin itself has no
 *         timeout for any request!
 */
public class MulitLoginBrowserProfile {

	private static Logger logger = LoggerFactory.getLogger(MulitLoginBrowserProfile.class);

	private static final String MULTILOGIN_API_V1_BASE_URL = "http://127.0.0.1:%%PORT%%/api/v1";
	private static final String MULTILOGIN_API_V2_BASE_URL = "http://127.0.0.1:%%PORT%%/api/v2";

	@Value("${multilogin.api.port:35000}")
	private Long apiPort;

	@Value("${mulitlogin.ApiWaitSeconds:10}")
	private Long apiWaitSeconds;

	@Value("${mulitlogin.ConnectTimeoutSeconds:20}")
	private int apiConnectTimeoutSeconds;

	@Value("${mulitlogin.ReadTimeoutSeconds:90}")
	private int apiReadTimeoutSeconds;

	@Autowired()
	private MultiLoginDefaultTemplateLoader defaultTemplateLoader;

	private String profileId;

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
	 * Tests for an existing profile in the crawler. Creates a new profile if
	 * required
	 * 
	 * @param crawler
	 * @throws Exception
	 */
	public MulitLoginBrowserProfile() {
		super();
		this.profileId = "";
	}

	private String replaceApiPlaceholders(String url) {
		return url.replace("%%PORT%%", this.apiPort.toString());
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

	public boolean updateExtensions(List<String> extensions) {
		logger.debug("updating extensions for profile {} to {}", this.profileId, String.join(",", extensions));

		final String url = replaceApiPlaceholders(MULTILOGIN_API_V2_BASE_URL) + "/profile/" + this.profileId;

		HttpURLConnection con = null;
		try {
			// setup connection
			URL obj = new URL(url);
			con = (HttpURLConnection) obj.openConnection();
			con.setConnectTimeout(apiConnectTimeoutSeconds * 1000);
			con.setReadTimeout(apiReadTimeoutSeconds * 1000);

			// setup method
			con.setRequestMethod("POST");
			con.setRequestProperty("Content-Type", "application/json; utf-8");
			con.setDoOutput(true);

			// setup data
			String jsonInputString = "{ \"extensions\": { \"enable\": true, \"names\": [ \""
					+ String.join(",", extensions) + "\" ] } }";

			try (OutputStream os = con.getOutputStream()) {
				byte[] input = jsonInputString.getBytes("utf-8");
				os.write(input, 0, input.length);
			}

			Integer responseCode = con.getResponseCode();
			String responseMessage = "";
			try {
				responseMessage = con.getResponseMessage();
			} catch (Exception ex) {
				// nothing to do
			}

			if (responseCode != 204) {
				logger.warn("Could not update proxy on profile {}, MultiLogin response was {}: {}", this.profileId,
						responseCode, responseMessage);
				return false;
			}

			runApiWait();
		} catch (IOException e) {
			logger.warn("Exception writing extensions data to profile {}", this.profileId, e);
			return false;
		} finally {
			if (con != null) {
				con.disconnect();
			}
		}

		return true;
	}

	public void updateProxy(RecordingProxy proxy) throws Exception {
		final String url = replaceApiPlaceholders(MULTILOGIN_API_V2_BASE_URL) + "/profile/" + this.profileId;

		logger.debug("Updating proxy for profile {}", this.profileId);

		URL obj = new URL(url);
		HttpURLConnection con = (HttpURLConnection) obj.openConnection();
		con.setConnectTimeout(apiConnectTimeoutSeconds * 1000);
		con.setReadTimeout(apiReadTimeoutSeconds * 1000);

		// setup method
		con.setRequestMethod("POST");
		con.setRequestProperty("Content-Type", "application/json; utf-8");
		con.setDoOutput(true);

		// Request data
		String jsonInputString = "{\"network\": {" + "    \"proxy\": {" + "      \"type\": \"HTTP\","
				+ "      \"host\": \"" + proxy.getIp() + "\"," + "      \"port\": \"" + proxy.getPort() + "\","
				+ "      \"username\": \"\"," + "      \"password\": \"\"" + "    }," + " \"dns\": []" + "}}";

		try (OutputStream os = con.getOutputStream()) {
			byte[] input = jsonInputString.getBytes("utf-8");
			os.write(input, 0, input.length);
		} catch (Exception ex) {
			logger.warn("Exception writing proxy data to profile {}", this.profileId, ex);
			throw new IllegalStateException("Could not write proxy to MultiLogin - Service " + this.profileId, ex);
		}

		Integer responseCode = con.getResponseCode();
		String responseMessage = "";
		try {
			responseMessage = con.getResponseMessage();
		} catch (Exception ex) {
			// nothing to do
		}

		if (responseCode != 204) {
			logger.warn("Could not update proxy on profile {}, MultiLogin response was {}: {}", this.profileId,
					responseCode, responseMessage);
			throw new IllegalStateException("Proxy was not properly set on MultiLogin profile " + this.profileId);
		}

		runApiWait();
	}

	public Boolean isActive() throws Exception {
		// no API wait as this is requesting a state
		final String url = replaceApiPlaceholders(MULTILOGIN_API_V1_BASE_URL) + "/profile/active?profileId="
				+ this.profileId;

		URL obj = new URL(url);
		HttpURLConnection con = (HttpURLConnection) obj.openConnection();
		con.setConnectTimeout(apiConnectTimeoutSeconds * 1000);
		con.setReadTimeout(apiReadTimeoutSeconds * 1000);
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
			Boolean resp = jsonResponse.getBoolean("value");

			return resp;
		} else {
			String responseMessage = "";
			try {
				responseMessage = con.getResponseMessage();
			} catch (Exception ex) {
				// nothing to do
			}

			logger.warn("could not check active state of profile {}; MulitLogin-Response {} {}", this.profileId,
					responseCode, responseMessage);

			return false;
		}
	}

	public Boolean stopProfile() throws Exception {
		String url = replaceApiPlaceholders(MULTILOGIN_API_V1_BASE_URL) + "/profile/stop?profileId=" + this.profileId;

		URL obj = new URL(url);
		HttpURLConnection con = (HttpURLConnection) obj.openConnection();
		con.setConnectTimeout(apiConnectTimeoutSeconds * 1000);
		con.setReadTimeout(apiReadTimeoutSeconds * 1000);
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

			String responseMessage = "";
			try {
				responseMessage = con.getResponseMessage();
			} catch (Exception ex) {
				// nothing to do
			}

			logger.warn("could not stop profile {}; MulitLogin-Response {} {}", this.profileId, responseCode,
					responseMessage);

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
		String url = replaceApiPlaceholders(MULTILOGIN_API_V1_BASE_URL) + "/profile/start?profileId=" + this.profileId
				+ "&automation=true";

		logger.debug("Starting profile {}", this.profileId);

		URL obj = new URL(url);
		HttpURLConnection con = (HttpURLConnection) obj.openConnection();
		con.setConnectTimeout(apiConnectTimeoutSeconds * 1000);
		con.setReadTimeout(apiReadTimeoutSeconds * 1000);

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

			// success
			return jsonResponse.getString("value");
		}

		// failure handling
		String responseMessage = "";
		try {
			responseMessage = con.getResponseMessage();
		} catch (Exception ex) {
			// nothing to do
		}

		logger.warn("could not start profile {}; MulitLogin-Response: {} {}", this.profileId, responseCode,
				responseMessage);
		throw new IllegalStateException("Could not load Mulitlogin profile");
	}

	public Boolean deleteProfile() throws Exception {
		String url = replaceApiPlaceholders(MULTILOGIN_API_V2_BASE_URL) + "/profile/" + this.profileId;

		URL obj = new URL(url);
		HttpURLConnection con = (HttpURLConnection) obj.openConnection();
		con.setConnectTimeout(apiConnectTimeoutSeconds * 1000);
		con.setReadTimeout(apiReadTimeoutSeconds * 1000);
		con.setRequestMethod("DELETE");

		Integer responseCode = con.getResponseCode();

		if (responseCode == 204) {
			runApiWait();
			logger.debug("deleted profile {}", this.profileId);
			return true;
		} else {
			logger.warn("could not delete profile {}", this.profileId);
			return false;
		}
	}

	/*
	 * not supported yet to grab an individual profile public Boolean
	 * isValidProfile() throws Exception { String url =
	 * "http://127.0.0.1:35000/api/v2/profile/" + this.profileId;
	 * 
	 * 
	 * URL obj = new URL(url); HttpURLConnection con = (HttpURLConnection)
	 * obj.openConnection(); con.setConnectTimeout(10000);
	 * con.setReadTimeout(60000); con.setRequestMethod("GET");
	 * 
	 * Integer responseCode = con.getResponseCode();
	 * 
	 * if (responseCode == HttpURLConnection.HTTP_OK) { return true; } else { return
	 * false; } }
	 */

	private String replaceTemplatePlaceholders(MultiLoginAgent agent, RecordingProxy proxy, String template) {
		// default values
		MultiLoginIOsType operatingSytem = MultiLoginIOsType.WINDOWS;
		MultiLoginIBrowserType browser = MultiLoginIBrowserType.MIMIC;
		String agentId = agent.getName() + "_AgentId " + agent.getId();

		// replacement values
		Map<String, String> tokens = new HashMap<String, String>();
		tokens.put("%%AGENTID%%", agentId);
		tokens.put("%%OS%%", operatingSytem.getMultiLoginValue());
		tokens.put("%%BROWSER%%", browser.getMultiLoginValue());
		tokens.put("%%PROXYTYPE%%", proxy.getType());
		tokens.put("%%PROXYHOST%%", proxy.getIp());
		tokens.put("%%PROXYPORT%%", proxy.getPort().toString());
		tokens.put("%%PROXYUSERNAME%%", proxy.getUsername());
		tokens.put("%%PROXYPASSWORD%%", proxy.getPassword());

		return defaultTemplateLoader.replaceTokens(template, tokens);
	}

	private String getTemplate(MultiLoginAgent agent) {
		logger.debug("getting creation tempalte");
		String template = agent.getMultiLoginCreationJson();

		if (StringUtils.isEmpty(template)) {
			logger.debug("falling back to default template");
			template = defaultTemplateLoader.getTemplate();
		}

		logger.debug("using the following template: {}", template);
		return template;
	}

	public void createNewProfile(MultiLoginAgent agent, RecordingProxy proxy) {
		try {
			// setup method
			String url = replaceApiPlaceholders(MULTILOGIN_API_V2_BASE_URL) + "/profile";

			logger.debug("creating profile for agent {} at {}", agent.getId(), url);

			String template = getTemplate(agent);
			String jsonInput = replaceTemplatePlaceholders(agent, proxy, template);

			URL obj = new URL(url);
			HttpURLConnection con = (HttpURLConnection) obj.openConnection();
			con.setConnectTimeout(apiConnectTimeoutSeconds * 1000);
			con.setReadTimeout(apiReadTimeoutSeconds * 1000);
			con.setRequestMethod("POST");
			con.setRequestProperty("Content-Type", "application/json; utf-8");
			con.setDoOutput(true);

			try (OutputStream os = con.getOutputStream()) {
				byte[] input = jsonInput.getBytes("utf-8");
				os.write(input, 0, input.length);
				os.flush();
			}

			if (con.getResponseCode() != HttpURLConnection.HTTP_OK) {
				logger.error("error creating profile for agent {}; Response: {} {}", agent.getId(),
						con.getResponseCode(), con.getResponseMessage());
				throw new IllegalStateException("Could not create browser profile for agent " + agent.getId());
			}

			// read in id of the profile
			String newProfileId = "";
			try (BufferedReader in = new BufferedReader(new InputStreamReader(con.getInputStream()));) {
				String inputLine;
				StringBuffer response = new StringBuffer();

				while ((inputLine = in.readLine()) != null) {
					response.append(inputLine);
				}

				// Get JSON text from the response and return the value by key "value"
				JSONObject jsonResponse = new JSONObject(response.toString());
				newProfileId = jsonResponse.getString("uuid");
			}

			if (StringUtils.isEmpty(newProfileId)) {
				logger.error("error reading in profile id for agent {}", agent.getId());
				throw new IllegalStateException("no profile-id was created for agent " + agent.getId());
			}
			logger.debug("successfully created profile {} for agent {}", newProfileId, agent.getId());

			runApiWait();

			this.profileId = newProfileId;
			agent.setMultiLoginId(newProfileId); // set to avoid recalling the API
		} catch (IllegalStateException ex) {
			throw ex;
		} catch (Exception ex) {
			throw new IllegalStateException("Could not create a a profile for agent " + agent.getId(), ex);
		}
	}

}
