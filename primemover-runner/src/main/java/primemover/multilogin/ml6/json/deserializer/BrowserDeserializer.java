package primemover.multilogin.ml6.json.deserializer;

import com.fasterxml.jackson.core.JacksonException;
import com.fasterxml.jackson.core.JsonParser;
import com.fasterxml.jackson.databind.DeserializationContext;
import com.fasterxml.jackson.databind.JsonDeserializer;
import primemover.multilogin.ml6.model.OperatingSystem;

import java.io.IOException;

public class BrowserDeserializer extends JsonDeserializer<OperatingSystem> {

	@Override
	public OperatingSystem deserialize(JsonParser p, DeserializationContext ctxt) throws IOException, JacksonException {
		return OperatingSystem.valueOfDisplayName(p.getValueAsString());
	}

}
