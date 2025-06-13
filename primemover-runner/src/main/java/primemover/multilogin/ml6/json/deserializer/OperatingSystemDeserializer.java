package primemover.multilogin.ml6.json.deserializer;

import com.fasterxml.jackson.core.JacksonException;
import com.fasterxml.jackson.core.JsonParser;
import com.fasterxml.jackson.databind.DeserializationContext;
import com.fasterxml.jackson.databind.JsonDeserializer;
import primemover.multilogin.ml6.model.Browser;

import java.io.IOException;

public class OperatingSystemDeserializer extends JsonDeserializer<Browser> {

	@Override
	public Browser deserialize(JsonParser p, DeserializationContext ctxt) throws IOException, JacksonException {
		return Browser.valueOfDisplayName(p.getValueAsString());
	}

}
