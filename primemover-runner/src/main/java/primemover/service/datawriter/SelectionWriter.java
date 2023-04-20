package primemover.service.datawriter;

import java.util.Date;

import primemover.model.IJob;
import primemover.util.Selection;

public class SelectionWriter extends JsonZippedDataWriterBase<Selection> {

	private String identifier;
	private String baseName;
	private Selection data;
	

	public SelectionWriter(IJob job, Selection data) {
		super();
		String datePrefix = StorageUtility.formatDateForFilePrefix(new Date());
		this.identifier = "dynamic_jobdata_" + job.getId();
		this.baseName = datePrefix + "_" + identifier; 
		this.data = data;
	}

	@Override
	public String getIdentifier() {
		return this.identifier;
	}
	
	@Override
	protected Selection getObject() {
		return data;
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
		return "Calculation data of the selection-method";
	}

	@Override
	public boolean hasOutput() {
		return true;
	}
}
