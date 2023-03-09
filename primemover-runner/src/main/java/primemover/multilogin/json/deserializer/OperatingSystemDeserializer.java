package primemover.multilogin.json.deserializer;

import java.io.IOException;

import com.fasterxml.jackson.core.JacksonException;
import com.fasterxml.jackson.core.JsonParser;
import com.fasterxml.jackson.databind.DeserializationContext;
import com.fasterxml.jackson.databind.JsonDeserializer;

import primemover.multilogin.model.Browser;

public class OperatingSystemDeserializer extends JsonDeserializer<Browser> {

	@Override
	public Browser deserialize(JsonParser p, DeserializationContext ctxt) throws IOException, JacksonException {
		return Browser.valueOfDisplayName(p.getValueAsString());
	}

}
