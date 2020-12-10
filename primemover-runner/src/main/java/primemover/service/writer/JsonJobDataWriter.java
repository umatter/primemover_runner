package primemover.service.writer;

import com.fasterxml.jackson.databind.ObjectMapper;

public abstract class JsonJobDataWriter<OutType> extends ZipJobDataWriter {

	private static final ObjectMapper mapper = new ObjectMapper();
	
	public JsonJobDataWriter() {
	}

	protected abstract OutType getObject();
	
	@Override
	protected String getData() throws Exception{
		return mapper.writer().writeValueAsString(this.getObject());
	}
}
