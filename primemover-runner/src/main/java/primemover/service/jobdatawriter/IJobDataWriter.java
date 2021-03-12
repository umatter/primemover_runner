package primemover.service.jobdatawriter;

import java.io.OutputStream;

public interface IJobDataWriter {

	String getIdentifier();
	String getFileName();
	String getDescription();
	String getContentType();
	boolean writeData(OutputStream os);
}
