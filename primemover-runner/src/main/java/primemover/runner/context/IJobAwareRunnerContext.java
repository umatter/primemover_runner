package primemover.runner.context;

import org.openqa.selenium.MutableCapabilities;
import primemover.model.IJob;

import javax.annotation.Nonnull;

public interface IJobAwareRunnerContext<OptionsType extends MutableCapabilities> extends IRunnerContext<OptionsType> {

	@Nonnull IJob getJob();
	@Nonnull IJobDataRecorder getJobDataRecorder();
}
