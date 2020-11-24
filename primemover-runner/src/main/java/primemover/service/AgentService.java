package primemover.service;

import org.json.JSONObject;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Service;
import org.springframework.web.client.ResourceAccessException;
import org.springframework.web.client.RestTemplate;

import primemover.config.StartupMode;
import primemover.model.MultiLoginAgent;
import primemover.model.api.Agent;

@Service
public class AgentService {
	
	@Value("${global.mode:PRODUCTION}")
	private StartupMode mode;

	@Value("#{'${backend.endpoint}' +'agents'}")
	private String endpointPath;

	private static Logger logger = LoggerFactory.getLogger(AgentService.class);
	
	public AgentService() {
		// nothing to do
	}

	@Autowired
	private RestTemplate restTemplate;
	
	
	private String calculateAgentUrl(Long id) {
		String url = endpointPath;
		
		if (id != null) {
			url = url + '/' + id.toString();
		}
		
		return url;
	}
	
	public Boolean updateMultiloginProfile(MultiLoginAgent agent, String newProfile) {
		String url = calculateAgentUrl(agent.getId());

		HttpHeaders headers = new HttpHeaders();
	    headers.setContentType(MediaType.APPLICATION_JSON);
	    JSONObject data = new JSONObject();
	    data.put("multilogin_id", newProfile);
	    
	    HttpEntity<String> request = new HttpEntity<String>(data.toString(), headers);
	  	   
	    try {
	    	ResponseEntity<String> response = restTemplate.exchange(url, HttpMethod.PUT, request, String.class);
		    
	    	 if (response.getStatusCode().is2xxSuccessful()) {
	 	    	logger.info("updated multilogin-profile for agent {} with {} ", agent.getId(), newProfile);
	 	    	return true;
	 	    } else {
	 	    	logger.warn("failure updating multilogin-profile for agent {} ", agent.getId());
	 	    	return false;
	 	    }	    
	    } catch (ResourceAccessException ex) {
	    	logger.warn("failure updating multilogin-profile for agent {} ", agent.getId());
	    	return false;
	    }
	}
	
	public String getMultiLoginIdFromServer(MultiLoginAgent agent) {
		String url = calculateAgentUrl(agent.getId());
	  	   
	    try {
	    	Agent response = restTemplate.getForObject(url, Agent.class);

	    	if (response == null) {
	    		return "";
	    	} else {
	    		return response.getMultilogin_id();
	    	}
	    } catch (ResourceAccessException ex) {
	    	logger.warn("failure updating multilogin-profile for agent {} ", agent.getId());
	    	return "";
	    }
	}
}
