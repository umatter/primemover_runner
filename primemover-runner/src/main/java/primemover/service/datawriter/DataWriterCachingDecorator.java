package primemover.service.datawriter;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.annotation.Nonnull;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.OutputStream;

public class DataWriterCachingDecorator implements IDataWriterDecorator {
	private static Logger logger = LoggerFactory.getLogger(DataWriterCachingDecorator.class);

	@Nonnull private IDataWriter innerWriter;

	private boolean cached = false;
	private byte[] dataCache;

	public void cacheData() {
		// already called
		if (cached) {
			return;
		}
		
		// no input data
		if (!innerWriter.hasOutput()) {
			logger.debug("nothing to cache");
			return;
		}
		
		// cache data
		logger.debug("caching data");
		try (ByteArrayOutputStream os = new ByteArrayOutputStream()) {
			innerWriter.writeData(os);
			dataCache = os.toByteArray();
			cached = true;
		} catch (IOException e) {
			logger.warn("failed caching data", e);
		}
	}
	
	public DataWriterCachingDecorator(IDataWriter innerWriter) {
		super();
		this.innerWriter = innerWriter;
	}

	@Override
	public String getIdentifier() {
		return innerWriter.getIdentifier();
	}

	@Override
	public String getFileName() {
		return innerWriter.getFileName();
	}

	@Override
	public String getDescription() {
		return innerWriter.getDescription();
	}

	@Override
	public String getContentType() {
		return innerWriter.getContentType();
	}

	@Override
	public boolean writeData(OutputStream os) {
		try {
			// prepare data
			cacheData();
			if (!cached && !innerWriter.hasOutput()) {
				return false;
			}
			
			// write output
			os.write(dataCache);
			return true;
		} catch (IOException e) {
			logger.warn("failed writing from cache", e);
			return false;
		}
	}

	@Override
	public IDataWriter getInnerWriter() {
		return this.innerWriter;
	}

	@Override
	public boolean hasOutput() {
		return this.innerWriter.hasOutput();
	}

}
