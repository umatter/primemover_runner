package primemover.service.datawriter;

import java.util.Date;

import primemover.model.IJob;
import primemover.service.JobData;

public class PageSourceWriter extends HtmlZippedDataWriterBase {

	private String identifier;
	private String baseName;
	
	public PageSourceWriter(JobData data) {		
		this(data.getJob(), data.getPageSource());	
	}
	
	public PageSourceWriter(IJob job, String data) {		
		super(data);
		
		String datePrefix = StorageUtility.formatDateForFilePrefix(new Date());
		this.identifier = StorageUtility.getIdentifier("page-finalsource", job);
		this.baseName = datePrefix + "-" + identifier;
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
