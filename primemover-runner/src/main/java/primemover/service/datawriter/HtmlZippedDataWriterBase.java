package primemover.service.datawriter;

import java.nio.charset.StandardCharsets;

import org.apache.commons.lang3.StringUtils;

/**
 * Base class for writing a single HTML-file zipped
 * 
 * @author dburkolter
 *
 */
public abstract class HtmlZippedDataWriterBase extends SingleZipEntryDataWriterBase {
	private String data;
	
	public HtmlZippedDataWriterBase(String data) {
		super();
		
		this.data = data;
	}
	
	protected abstract String getBaseName();

	@Override
	public String getFileName() {
		return getBaseName() + ".zip";
	}

	@Override
	protected String getZipEntryFilename() {
		return getBaseName() + ".html";
	}

	@Override
	protected byte[] getData() throws Exception {
		return data.getBytes(StandardCharsets.UTF_8);
	}
	
	@Override
	public boolean hasOutput() {
		return StringUtils.isNotEmpty(data);
	}
}
