package primemover.service.datawriter;

import java.io.OutputStream;

public interface IDataWriter {

	String getIdentifier();
	String getFileName();
	String getDescription();
	String getContentType();
	boolean writeData(OutputStream os);
}
