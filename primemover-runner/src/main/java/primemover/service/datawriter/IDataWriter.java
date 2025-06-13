package primemover.service.datawriter;

import javax.annotation.Nonnull;
import java.io.OutputStream;

public interface IDataWriter {

	/**
	 * file identifier
	 * 
	 * @return identifier for this file
	 */
	@Nonnull String getIdentifier();
	
	/**
	 * filename
	 * 
	 * @return filename
	 */
	@Nonnull String getFileName();
	
	/**
	 * Short description of the content
	 * 
	 * @return
	 */
	@Nonnull String getDescription();
	
	/**
	 * Mime-type of the file
	 * @return
	 */
	@Nonnull String getContentType();
	
	/**
	 * has this writer an output?
	 * 
	 * @return
	 */
	boolean hasOutput();
	
	/**
	 * writes this data to the outputstream provided
	 * @param os
	 * @return
	 */
	boolean writeData(OutputStream os);
}
