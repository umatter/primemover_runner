package primemover.service.writer;

import java.io.OutputStream;
import java.nio.charset.StandardCharsets;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public abstract class SingleZipEntryJobDataWriter implements IJobDataWriter {
	private static final Logger logger = LoggerFactory.getLogger(SingleZipEntryJobDataWriter.class);

	public SingleZipEntryJobDataWriter() {
	}

	protected abstract String getData() throws Exception;
	protected abstract String getZipEntryFilename();
	
	@Override
	public String getContentType() {
		return "application/zip";
	}
	
	@Override
	public boolean writeData(OutputStream os) {
		logger.info("writting jobdata as zipped String");
		
		// prepare streams
		try (ZipOutputStream zipOut = new ZipOutputStream(os)) {
			ZipEntry zipEntry = new ZipEntry(this.getZipEntryFilename());
			zipOut.putNextEntry(zipEntry);
			byte[] bytes = this.getData().getBytes(StandardCharsets.UTF_8);
			
			zipOut.write(bytes);
			// make sure everything is written
			zipOut.finish();
			zipOut.flush();
			
			return true;
		} catch (Exception e) {
			logger.warn("Error writing jobdata", e);
			return false;
		}		
	}
}
