package primemover.service.datawriter;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.OutputStream;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class DataWriterCachingDecorator implements IDataWriter{
	private static Logger logger = LoggerFactory.getLogger(DataWriterCachingDecorator.class);
	
	private IDataWriter innerWriter;
	
	private boolean cached = false;
	private byte[] dataCache;

	public DataWriterCachingDecorator(IDataWriter innerWriter) {
		super();
		this.innerWriter = innerWriter;
		
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
		if (!cached) {
			return false;
		}
		
		try {
			os.write(dataCache);
			return true;
		} catch (IOException e) {
			logger.warn("failed writing from cahce", e);
			return false;
		}
	}
	
	

}
