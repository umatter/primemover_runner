package primemover.util;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.HashMap;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.ObjectMapper;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;

public class MultiLoginDefaultTemplateLoader {

	private static Logger logger = LoggerFactory.getLogger(MultiLoginDefaultTemplateLoader.class);

	private static MultiLoginDefaultTemplateLoader instance = new MultiLoginDefaultTemplateLoader();
	
	@Value("${multilogin.DefaultProfileJsonPath:config/DefaultMultiLoginProfile.json}")
	private String multiLoginDefaultJsonPath;
	
	private String defaultTemplate;
	
	private Map<String, String> defaultTokens = new HashMap<String, String>();
	private Pattern pattern;
	
	private ObjectMapper mapper = new ObjectMapper();
	
	private MultiLoginDefaultTemplateLoader() {
	}
	
	public void init() {
		initDefaultTemplate();
		initTokenReplacement();
	}
	
	private void initTokenReplacement() {
		// keywords
		defaultTokens.put("%%AGENTID%%", "");
		defaultTokens.put("%%OS%%", "");
		defaultTokens.put("%%BROWSER%%", "");
		defaultTokens.put("%%PROXYTYPE%%", "");
		defaultTokens.put("%%PROXYHOST%%", "");
		defaultTokens.put("%%PROXYPORT%%", "");
		defaultTokens.put("%%PROXYUSERNAME%%", "");
		defaultTokens.put("%%PROXYPASSWORD%%", "");
		
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
        	logger.warn("Failed to load default profile from {}. Falling back to hard-coded default.", multiLoginDefaultJsonPath);
        	
        	template = "{\r\n" + 
        			"	\"name\": \"%%AGENTID%%\",\r\n" + 
        			"	\"os\": \"%%OS%%\",\r\n" + 
        			"	\"browser\": \"%%BROWSER%%\",\r\n" + 
        			"	\"network\": {\r\n" + 
        			"		\"proxy\": {\r\n" + 
        			"			\"type\": \"%%PROXYTYPE%%\",\r\n" + 
        			"			\"host\": \"%%PROXYHOST%%\",\r\n" + 
        			"			\"port\": \"%%PROXYPORT%%\",\r\n" + 
        			"			\"username\": \"%%PROXYUSERNAME%%\",\r\n" + 
        			"			\"password\": \"%%PROXYPASSWORD%%\"\r\n" + 
        			"		},\r\n" + 
        			"		\"dns\": []\r\n" + 
        			"	}\r\n" + 
        			"}";
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
	
	public String getTemplate() {
		return this.defaultTemplate;
	}
	
	public String replaceTokens(String template, Map<String, String> tokens) {
		logger.debug("replacing tokens");
		
		// verification
		if (!tokens.containsKey("%%AGENTID%%")) {
			throw new IllegalArgumentException("%%AGENTID%% is mandatory");
		}
		if (!tokens.containsKey("%%OS%%")) {
			throw new IllegalArgumentException("%%OS%% is mandatory");
		}
		if (!tokens.containsKey("%%BROWSER%%")) {
			throw new IllegalArgumentException("%%BROWSER%% is mandatory");
		}
		
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
	
	public String getTemplateAndReplaceTokens(Map<String, String> tokens) {
		String template = getTemplate();
		return replaceTokens(template, tokens);
	}
	
	public static MultiLoginDefaultTemplateLoader getInstance() {
		return instance;
	}
}
