package primemover.service.writer;

import java.io.OutputStream;

public interface IJobDataWriter {

	String getFileName();
	String getDescription();
	String getContentType();
	boolean writeData(OutputStream os);
}
