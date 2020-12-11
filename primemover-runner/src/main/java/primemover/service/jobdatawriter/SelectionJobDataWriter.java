package primemover.service.jobdatawriter;

import java.util.Date;

import primemover.model.IJob;
import primemover.util.Selection;

public class SelectionJobDataWriter extends JsonAsZipJobDataWriter<Selection> {

	private String baseName;
	private Selection data;
	

	public SelectionJobDataWriter(IJob job, Selection data) {
		String datePrefix = StorageHelper.formatDateForFilePrefix(new Date());
		this.baseName = datePrefix + "_dynamic_jobdata_" + job.getId(); 
		this.data = data;
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
