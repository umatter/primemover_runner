package primemover.runner.context;

import javax.annotation.Nonnull;

import org.openqa.selenium.MutableCapabilities;

import primemover.model.IJob;

public interface IJobAwareRunnerContext<OptionsType extends MutableCapabilities> extends IRunnerContext<OptionsType> {

	@Nonnull IJob getJob();
	@Nonnull IJobDataRecorder getJobDataRecorder();
}
