package primemover.service.datawriter;

import java.io.OutputStream;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public abstract class SingleZipEntryDataWriterBase implements IDataWriter {
	private static final Logger logger = LoggerFactory.getLogger(SingleZipEntryDataWriterBase.class);

	public SingleZipEntryDataWriterBase() {
	}

	protected abstract byte[] getData() throws Exception;

	protected abstract String getZipEntryFilename();

	@Override
	public String getContentType() {
		return "application/zip";
	}

	@Override
	public boolean writeData(OutputStream os) {
		logger.debug("writting jobdata zipped\n-filename: {}\n-contenttype: {}\n-description", this.getFileName(),
				this.getContentType(), this.getDescription());

		// prepare streams
		try (ZipOutputStream zipOut = new ZipOutputStream(os)) {
			ZipEntry zipEntry = new ZipEntry(this.getZipEntryFilename());
			zipOut.putNextEntry(zipEntry);
			zipOut.write(getData());

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
