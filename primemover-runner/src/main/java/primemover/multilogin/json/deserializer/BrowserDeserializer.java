package primemover.multilogin.json.deserializer;

import java.io.IOException;

import com.fasterxml.jackson.core.JacksonException;
import com.fasterxml.jackson.core.JsonParser;
import com.fasterxml.jackson.databind.DeserializationContext;
import com.fasterxml.jackson.databind.JsonDeserializer;

import primemover.multilogin.model.OperatingSystem;

public class BrowserDeserializer extends JsonDeserializer<OperatingSystem> {

	@Override
	public OperatingSystem deserialize(JsonParser p, DeserializationContext ctxt) throws IOException, JacksonException {
		return OperatingSystem.valueOfDisplayName(p.getValueAsString());
	}

}
