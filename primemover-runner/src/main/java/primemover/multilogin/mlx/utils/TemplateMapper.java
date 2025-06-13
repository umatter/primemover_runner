package primemover.multilogin.mlx.utils;

import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import primemover.capture.BasicAuthorization;
import primemover.capture.RecordingProxy;
import primemover.model.MultiLoginAgent;
import primemover.model.MultiLoginIBrowserType;
import primemover.model.MultiLoginIOsType;
import wiremock.com.github.jknack.handlebars.internal.lang3.StringUtils;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class TemplateMapper {

	private static Logger logger = LoggerFactory.getLogger(TemplateMapper.class);

	@Value("${multilogin.DefaultProfileJsonPath:config/DefaultMultiLoginProfile.json}")
	private String multiLoginDefaultJsonPath;

	@Value("${multilogin.defaultFolderId}")
	private String defaultFolderId;
	
	private String defaultTemplate;
	
	private Map<String, String> defaultTokens = new HashMap<String, String>();
	private Pattern pattern;
	
	private ObjectMapper mapper = new ObjectMapper();
	
	public TemplateMapper() {
	}
	
	public void init() {
		initDefaultTemplate();
		initDefaultReplacementToken();
	}
	
	private void initDefaultReplacementToken() {
		// keywords
		defaultTokens.put("%%AGENTID%%", "");
		defaultTokens.put("%%OS%%", "");
		defaultTokens.put("%%BROWSER%%", "");
		defaultTokens.put("%%PROXYTYPE%%", "");
		defaultTokens.put("%%PROXYHOST%%", "");
		defaultTokens.put("%%PROXYPORT%%", "");
		defaultTokens.put("%%PROXYUSERNAME%%", "");
		defaultTokens.put("%%PROXYPASSWORD%%", "");
		defaultTokens.put("%%FOLDERID%%", this.defaultFolderId);
		
		// Create pattern of the format "%(cat|beverage)%"
		StringBuilder sb = new StringBuilder();
		sb.append("(");
		int cnt = 0;
		for (String key : defaultTokens.keySet()) {
			if (cnt > 0) {
				sb.append("|");
			}
			sb.append((String) key);
			cnt++;
		}
		sb.append(")");
		pattern = Pattern.compile(sb.toString());
	}

	
	private void initDefaultTemplate() {
		String template;
		
		try
        {
			logger.debug("Try to load default profile from {}", multiLoginDefaultJsonPath);
            template = new String ( Files.readAllBytes( Paths.get(multiLoginDefaultJsonPath) ) );
        } 
        catch (IOException e) 
        {
        	logger.error("Failed to load default profile from {}. Falling back to hard-coded default.", multiLoginDefaultJsonPath);
        	
        	throw new RuntimeException("Failed to load profile", e);
        }
 
		// ensure valid JSON
		try {
			mapper.enable(DeserializationFeature.FAIL_ON_READING_DUP_TREE_KEY);
			mapper.readTree(template);
		} catch (Exception e) {
			logger.error("Failed to verify JSON-template.", e);
        	throw new ExceptionInInitializerError("Failed to verify JSON-template.");
		}
		
		
		// set content
		this.defaultTemplate = template;
	}
	
	public String getReplacedDefaultTemplate(Map<String, String> tokens) {
		return replaceTokens( this.defaultTemplate, tokens);
	}
	
	public String getReplacedTemplate(MultiLoginAgent agent, RecordingProxy proxy) {
		// default values
		MultiLoginIOsType operatingSytem = MultiLoginIOsType.WINDOWS;
		MultiLoginIBrowserType browser = MultiLoginIBrowserType.MIMIC;
		String agentId = agent.getName() + "_AgentId " + agent.getId();

		Optional<BasicAuthorization> auth = proxy.getAuthorization();

		String username = "";
		String password = "";
		if (auth.isPresent()) {
			username = auth.get().getUsername();
			password = auth.get().getPassword();
		}

		// replacement values
		Map<String, String> tokens = new HashMap<String, String>();
		tokens.put("%%AGENTID%%", agentId);
		tokens.put("%%OS%%", operatingSytem.getMultiLoginValue());
		tokens.put("%%BROWSER%%", browser.getMultiLoginValue());
		tokens.put("%%PROXYTYPE%%", proxy.getType());
		tokens.put("%%PROXYHOST%%", proxy.getIp());
		tokens.put("%%PROXYPORT%%", proxy.getHttpPort().toString());
		tokens.put("%%PROXYUSERNAME%%", username);
		tokens.put("%%PROXYPASSWORD%%", password);
		tokens.put("%%FOLDERID%%", this.defaultFolderId);
				
		// template
		String template = agent.getMultiLoginCreationJson();
		
		if (StringUtils.isEmpty(template)) {
			template = this.defaultTemplate;
		}
		
		return replaceTokens(template, tokens);
	}

	private void validateAndThrowMandatoryToken(Map<String, String> tokens) throws IllegalArgumentException {
		if (!tokens.containsKey("%%AGENTID%%")) {
			throw new IllegalArgumentException("%%AGENTID%% is mandatory");
		}
		if (!tokens.containsKey("%%OS%%")) {
			throw new IllegalArgumentException("%%OS%% is mandatory");
		}
		if (!tokens.containsKey("%%BROWSER%%")) {
			throw new IllegalArgumentException("%%BROWSER%% is mandatory");
		}
	}
	
	private String replaceTokens(String template, Map<String, String> tokens) {
		logger.debug("replacing tokens");
		
		// verification
		validateAndThrowMandatoryToken(tokens);

		// setting defaults
		defaultTokens.keySet().forEach(key -> {
			if (!tokens.containsKey(key)) {
				tokens.put(key, defaultTokens.get(key));
			}
		});
		
		// search and replace
		StringBuffer sb = new StringBuffer();
		Matcher matcher = pattern.matcher(template);
		while(matcher.find()) {
		    matcher.appendReplacement(sb, tokens.get(matcher.group(1)));
		}
		matcher.appendTail(sb);
		String res = sb.toString();
		logger.debug("template with replaced tokens: {}", res);
		
		return res;
	}
}

