package primemover.runner.context;

import org.openqa.selenium.MutableCapabilities;
import org.openqa.selenium.WebDriver;
import primemover.anticaptcha.ICaptchaSolver;
import primemover.capture.RecordingProxy;
import primemover.model.Crawler;
import primemover.model.IJob;

import javax.annotation.Nonnull;

public class JobAwarnessContextDecorator<OptionsType extends MutableCapabilities> implements IJobAwareRunnerContext<OptionsType> {

	@Nonnull private IRunnerContext<OptionsType> decorated;
	@Nonnull private IJob job;
	@Nonnull private JobDataRecorder jobDataRecorder;
		
	private JobAwarnessContextDecorator(IRunnerContext<OptionsType> decorated, IJob job,
			JobDataRecorder jobDataRecorder) {
		super();
		this.decorated = decorated;
		this.job = job;
		this.jobDataRecorder = jobDataRecorder;
	}

	@Override
	public OptionsType getOptions() {
		return decorated.getOptions();
	}

	@Override
	public RecordingProxy getRecordingProxy() {
		return decorated.getRecordingProxy();
	}

	@Override
	public WebDriver getDriver() {
		return decorated.getDriver();
	}

	@Override
	public Crawler getCrawler() {
		return decorated.getCrawler();
	}

	@Override
	public ICaptchaSolver getCaptchaSolver() {
		return decorated.getCaptchaSolver();
	}

	@Override
	public @Nonnull IJob getJob() {
		return job;
	}

	@Override
	public @Nonnull JobDataRecorder getJobDataRecorder() {
		return jobDataRecorder;
	}

	static public @Nonnull JobAwarnessContextDecorator decorate(IRunnerContext context, IJob job, JobDataRecorder recorder) {
		return new JobAwarnessContextDecorator(context, job, recorder);
	}
	
}
