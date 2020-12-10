package primemover.service.writer;

import com.fasterxml.jackson.databind.ObjectMapper;

public abstract class JsonAsZipJobDataWriter<OutType> extends SingleZipEntryJobDataWriter {

	private static final ObjectMapper mapper = new ObjectMapper();
	
	public JsonAsZipJobDataWriter() {
	}

	protected abstract OutType getObject();
	
	@Override
	protected String getData() throws Exception{
		return mapper.writer().writeValueAsString(this.getObject());
	}
}
