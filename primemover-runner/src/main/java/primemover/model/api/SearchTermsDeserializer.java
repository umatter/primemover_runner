package primemover.model.api;

import java.io.IOException;

import org.apache.commons.lang3.StringUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.fasterxml.jackson.core.JsonParser;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.DeserializationContext;
import com.fasterxml.jackson.databind.JsonDeserializer;
import com.fasterxml.jackson.databind.ObjectMapper;


public class SearchTermsDeserializer extends JsonDeserializer<SearchTerms> {

	private static Logger logger = LoggerFactory.getLogger(SearchTermsDeserializer.class);
	
	public SearchTermsDeserializer() {
	}
	

	@Override
	public SearchTerms deserialize(JsonParser p, DeserializationContext ctxt)
			throws IOException, JsonProcessingException {
		String text = p.getText();
        ObjectMapper mapper = (ObjectMapper) p.getCodec();
                
        if (StringUtils.isEmpty(text)) {
        	return null;
        }
        
        if ("[]".equals(text)) {
        	return null;
        }
        
        SearchTerms res = null;
        try {
        	res = mapper.readValue(text, SearchTerms.class);
		} catch (Exception e) {
			logger.warn("error deserializing: {}", text, e);
		}        
        
        return res;
        
	}

}
