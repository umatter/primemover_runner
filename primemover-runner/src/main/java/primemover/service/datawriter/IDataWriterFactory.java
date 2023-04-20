package primemover.service.datawriter;

import javax.annotation.Nonnull;

import primemover.runner.RecordedJobData;

/**
 * Factory to create writers. Required for spring-configuration.
 * 
 * @author dominik
 *
 */
public interface IDataWriterFactory {
	@Nonnull IDataWriter getWriter(RecordedJobData data);
}
