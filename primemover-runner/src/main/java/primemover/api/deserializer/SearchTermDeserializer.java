package primemover.api.deserializer;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import org.apache.commons.lang3.StringUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.fasterxml.jackson.core.JsonParser;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.DeserializationContext;
import com.fasterxml.jackson.databind.JsonDeserializer;
import com.fasterxml.jackson.databind.ObjectMapper;

import primemover.api.model.SearchTerm;

public class SearchTermDeserializer extends JsonDeserializer<List<SearchTerm>> {

	private final static Logger LOGGER = LoggerFactory.getLogger(SearchTermDeserializer.class);

	private static final ArrayList<SearchTerm> emptyResult = new ArrayList<SearchTerm>();

	public SearchTermDeserializer() {
	}

	@Override
	public List<SearchTerm> deserialize(JsonParser p, DeserializationContext ctxt)
			throws IOException, JsonProcessingException {
		String text = p.getText();
		ObjectMapper mapper = (ObjectMapper) p.getCodec();

		if (StringUtils.isEmpty(text)) {
			return emptyResult;
		}

		if (StringUtils.isWhitespace(text)) {
			return emptyResult;
		}

		if ("[]".equals(text)) {
			return emptyResult;
		}

		SearchTerm[] res = null;
		try {
			res = mapper.readValue(text, SearchTerm[].class);
			return Arrays.asList(res);
		} catch (Exception e) {
			LOGGER.warn("error deserializing: {}", text, e);
		}

		return emptyResult;
	}

}
