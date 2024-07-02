package primemover.service.datawriter;

import java.nio.charset.StandardCharsets;
import java.util.Date;

import org.apache.commons.lang3.StringUtils;

import primemover.model.IJob;

public class LambdaResultWriter extends SingleZipEntryDataWriterBase {

	private String identifier;
	private String baseName;
	private String data;

	public LambdaResultWriter(IJob job, String data) {
		super();
		String datePrefix = StorageUtility.formatDateForFilePrefix(new Date());
		this.identifier = "lambda_jobdata_" + job.getId();
		this.baseName = datePrefix + "_" + identifier;
		this.data = data;
	}

	@Override
	public String getIdentifier() {
		return this.identifier;
	}

	@Override
	public String getFileName() {
		return baseName + ".zip";
	}

	@Override
	protected String getZipEntryFilename() {
		return baseName + ".json";
	}

	@Override
	public String getDescription() {
		return "Result of the Lambda-Selection";
	}

	@Override
	public boolean hasOutput() {
		return !StringUtils.isEmpty(data);
	}

	@Override
	protected byte[] getData() throws Exception {
		return this.data.getBytes(StandardCharsets.UTF_8);
	}

}
