package primemover.service.datawriter;

import primemover.runner.RecordedJobData;

import javax.annotation.Nonnull;

/**
 * Factory to create writers. Required for spring-configuration.
 * 
 * @author dominik
 *
 */
public interface IDataWriterFactory {
	@Nonnull IDataWriter getWriter(RecordedJobData data);
}
