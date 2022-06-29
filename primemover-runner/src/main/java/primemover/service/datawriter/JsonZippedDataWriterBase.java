package primemover.service.datawriter;

import java.nio.charset.StandardCharsets;

import com.fasterxml.jackson.databind.ObjectMapper;

public abstract class JsonZippedDataWriterBase<OutType> extends SingleZipEntryDataWriterBase {

	private static final ObjectMapper mapper = new ObjectMapper();
	
	public JsonZippedDataWriterBase() {
		super();
	}

	protected abstract OutType getObject();
	
	@Override
	protected byte[] getData() throws Exception{
		String jsonString = mapper.writer().writeValueAsString(this.getObject());
		return jsonString.getBytes(StandardCharsets.UTF_8);
	}
}
