package primemover.service.datawriter;

import java.util.Date;

import primemover.runner.RecordedJobData;

public abstract class RecordedJobDataBaseWriter implements IDataWriter {
	private RecordedJobData data;
	private String fileName;
	private String identifier;
	private String contentType;
	private String description;	
	
	public RecordedJobDataBaseWriter(RecordedJobData data, String identifierPrefix, String fileSufix, String contentType, String description) {
		super();
		String datePrefix = StorageUtility.formatDateForFilePrefix(new Date());
		this.data = data;
		this.identifier = StorageUtility.getIdentifier(identifierPrefix, data.getJob());
		this.fileName = datePrefix + "-" + identifier + "." + fileSufix;
		this.contentType = contentType;
	}

	
	public RecordedJobData getData() {
		return this.data;
	}
	
	@Override
	public String getIdentifier() {
		return identifier;
	}

	@Override
	public String getFileName() {
		return fileName;
	}

	@Override
	public String getDescription() {
		return description;
	}

	@Override
	public String getContentType() {
		return contentType;
	}

}