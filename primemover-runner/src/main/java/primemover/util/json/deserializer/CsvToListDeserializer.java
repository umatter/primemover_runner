package primemover.util.json.deserializer;

import com.fasterxml.jackson.core.JacksonException;
import com.fasterxml.jackson.core.JsonParser;
import com.fasterxml.jackson.databind.DeserializationContext;
import com.fasterxml.jackson.databind.JsonDeserializer;

import java.io.IOException;
import java.util.Arrays;
import java.util.List;

public class CsvToListDeserializer extends JsonDeserializer<List<String>> {

	@Override
	public List<String> deserialize(JsonParser p, DeserializationContext ctxt) throws IOException, JacksonException {
		String csv = p.getText();
		String[] vals = csv.split(",");
		return Arrays.asList(vals);
	}

}
