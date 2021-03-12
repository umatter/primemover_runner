package primemover.service.jobdatawriter;

import java.util.Date;

import primemover.model.IJob;
import primemover.util.Selection;

public class SelectionJobDataWriter extends JsonAsZipJobDataWriter<Selection> {

	private String identifier;
	private String baseName;
	private Selection data;
	

	public SelectionJobDataWriter(IJob job, Selection data) {
		String datePrefix = StorageHelper.formatDateForFilePrefix(new Date());
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

	
}
