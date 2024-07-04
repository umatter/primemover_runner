package primemover.service.datawriter;

import java.util.Date;

public abstract class RecordedJobDataBaseWriter<TData extends IRecordedData> implements IDataWriter {
	private TData data;
	private String fileName;
	private String identifier;
	private String contentType;
	private String description;

	public RecordedJobDataBaseWriter(TData data, String identifierPrefix, String fileSufix, String contentType,
			String description) {
		super();
		String datePrefix = StorageUtility.formatDateForFilePrefix(new Date());
		this.data = data;
		this.identifier = StorageUtility.getIdentifier(identifierPrefix, data.getJob());
		this.fileName = datePrefix + "-" + identifier + "." + fileSufix;
		this.contentType = contentType;
	}

	public TData getData() {
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