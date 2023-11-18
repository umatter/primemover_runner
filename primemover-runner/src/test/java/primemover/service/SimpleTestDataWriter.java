package primemover.service;

import java.io.IOException;
import java.io.OutputStream;

import com.mysql.cj.util.StringUtils;

import primemover.service.datawriter.IDataWriter;

public class SimpleTestDataWriter implements IDataWriter {
	private String identifier = "identifier";
	private String fileName = "filename";
	private String description = "description";
	private String contentType = "test/data";
	private Boolean hasOutput = true;
	private byte[] testData = StringUtils.getBytes("test-data", "UTF-8");

	private boolean wasWriteCalled = false;

	public boolean wasWriteCalled() {
		return wasWriteCalled;
	}

	public SimpleTestDataWriter() {
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

	@Override
	public boolean hasOutput() {
		return hasOutput;
	}

	@Override
	public boolean writeData(OutputStream os) {
		wasWriteCalled = true;
		try {
			if (hasOutput()) {
				os.write(testData);
			}
		} catch (IOException e) {
			return false;
		}
		return true;
	}

}
