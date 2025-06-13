package primemover.service.datawriter;

import primemover.model.IJob;
import primemover.runner.RecordedJobData;

import java.util.Date;

public class PageSourceWriter extends HtmlZippedDataWriterBase {

	private String identifier;
	private String baseName;

	public PageSourceWriter(RecordedJobData data) {
		this(data.getJob(), data.getPageSource());
	}

	public PageSourceWriter(IJob job, String data) {
		super(data);

		String datePrefix = StorageUtility.formatDateForFilePrefix(new Date());

		this.identifier = StorageUtility.getIdentifier(datePrefix + "-page-finalsource", job);
		this.baseName = identifier;
	}

	@Override
	public String getDescription() {
		return "page source";
	}

	@Override
	public String getIdentifier() {
		return identifier;
	}

	@Override
	protected String getBaseName() {
		return baseName;
	}
}
