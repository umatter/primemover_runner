package primemover.runner.job;

import java.util.HashMap;
import java.util.Map;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.BeansException;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.ApplicationContext;
import org.springframework.context.ApplicationContextAware;

import primemover.config.StartupMode;
import primemover.model.IJob;
import primemover.model.jobs.AssertElementExistsJob;
import primemover.model.jobs.AssertPageLoadedJob;
import primemover.model.jobs.AssertProxyByGeosurfJob;
import primemover.model.jobs.AssertProxyByPrimemoverApiJob;
import primemover.model.jobs.AssertScrolledMaxJob;
import primemover.model.jobs.AssertTextInFieldJob;
import primemover.model.jobs.ClickJob;
import primemover.model.jobs.EnterTextFieldJob;
import primemover.model.jobs.HandleAlertJob;
import primemover.model.jobs.LambdaSelectJob;
import primemover.model.jobs.ScrollByDurationJob;
import primemover.model.jobs.ScrollByLengthJob;
import primemover.model.jobs.SingleSelectJob;
import primemover.model.jobs.SwitchToJob;
import primemover.model.jobs.VisitJob;
import primemover.model.jobs.WaitJob;
import primemover.runner.context.IJobAwareRunnerContext;
import primemover.runner.job.handler.AlertJobHandler;
import primemover.runner.job.handler.AssertElementExistsJobHandler;
import primemover.runner.job.handler.AssertPageLoadedJobHandler;
import primemover.runner.job.handler.AssertProxyByGeosurfJobHandler;
import primemover.runner.job.handler.AssertProxyByPrimemoverApiJobHandler;
import primemover.runner.job.handler.AssertScrolledMaxJobHandler;
import primemover.runner.job.handler.AssertTextInFieldJobHandler;
import primemover.runner.job.handler.ClickJobHandler;
import primemover.runner.job.handler.EnterTextFieldJobHandler;
import primemover.runner.job.handler.LambdaSelectJobHandler;
import primemover.runner.job.handler.ScrollByDurationJobHandler;
import primemover.runner.job.handler.ScrollByLengthJobHandler;
import primemover.runner.job.handler.SingleSelectUrlJobHandler;
import primemover.runner.job.handler.SwitchToJobHandler;
import primemover.runner.job.handler.VisitJobHandler;
import primemover.runner.job.handler.WaitJobHandler;

public class JobDispatcher implements IJobDispatcher, ApplicationContextAware {

	private static Logger logger = LoggerFactory.getLogger(JobDispatcher.class);

	@Value("${global.mode}")
	private StartupMode mode;

	private ApplicationContext ctx;

	@Override
	public void setApplicationContext(ApplicationContext applicationContext) throws BeansException {
		this.ctx = applicationContext;
	}

	// Handler registration
	@SuppressWarnings("rawtypes")
	private final Map<Class, IJobHandler> dispatch = new HashMap<Class, IJobHandler>();

	@SuppressWarnings("rawtypes")
	protected void addHandler(Class type, IJobHandler newHandler) {
		dispatch.put(type, newHandler);
	}

	// populate
	@Override
	public void setup() {
		logger.trace("populating handlers");

		IJobHandler tmp;

		logger.trace("creating VisitJob-Handler");
		tmp = new VisitJobHandler();
		ctx.getAutowireCapableBeanFactory().autowireBean(tmp);
		addHandler(VisitJob.class, tmp);

		logger.trace("creating WaitJob-Handler");
		tmp = new WaitJobHandler();
		ctx.getAutowireCapableBeanFactory().autowireBean(tmp);
		addHandler(WaitJob.class, tmp);

		logger.trace("creating ScrollByLengthJob-Handler");
		tmp = new ScrollByLengthJobHandler();
		ctx.getAutowireCapableBeanFactory().autowireBean(tmp);
		addHandler(ScrollByLengthJob.class, tmp);

		logger.trace("creating ScrollByDurationJob-Handler");
		tmp = new ScrollByDurationJobHandler();
		ctx.getAutowireCapableBeanFactory().autowireBean(tmp);
		addHandler(ScrollByDurationJob.class, tmp);

		logger.trace("creating HandleAlertJob-Handler");
		tmp = new AlertJobHandler();
		ctx.getAutowireCapableBeanFactory().autowireBean(tmp);
		addHandler(HandleAlertJob.class, tmp);

		logger.trace("creating HandleAlertJob-Handler");
		tmp = new EnterTextFieldJobHandler();
		ctx.getAutowireCapableBeanFactory().autowireBean(tmp);
		addHandler(EnterTextFieldJob.class, tmp);

		logger.trace("creating SingleSelectUrlJob-Handler");
		tmp = new SingleSelectUrlJobHandler();
		ctx.getAutowireCapableBeanFactory().autowireBean(tmp);
		addHandler(SingleSelectJob.class, tmp);

		logger.trace("creating SwitchToJob-Handler");
		tmp = new SwitchToJobHandler();
		ctx.getAutowireCapableBeanFactory().autowireBean(tmp);
		addHandler(SwitchToJob.class, tmp);

		logger.trace("creating ClickJob-Handler");
		tmp = new ClickJobHandler();
		ctx.getAutowireCapableBeanFactory().autowireBean(tmp);
		addHandler(ClickJob.class, tmp);

		logger.trace("creating LambdaSelectJob-Handler");
		tmp = new LambdaSelectJobHandler();
		ctx.getAutowireCapableBeanFactory().autowireBean(tmp);
		addHandler(LambdaSelectJob.class, tmp);

		// ---------------------------------------------------------------
		// add assert-jobs only for develop and integration
		// ---------------------------------------------------------------
		if (!(mode == StartupMode.DEVELOP || mode == StartupMode.INTEGRATION)) {
			return;
		}

		logger.trace("creating AssertTextInFieldJob-Handler");
		tmp = new AssertTextInFieldJobHandler();
		ctx.getAutowireCapableBeanFactory().autowireBean(tmp);
		addHandler(AssertTextInFieldJob.class, tmp);

		logger.trace("creating AssertPageLoadedJob-Handler");
		tmp = new AssertPageLoadedJobHandler();
		ctx.getAutowireCapableBeanFactory().autowireBean(tmp);
		addHandler(AssertPageLoadedJob.class, tmp);

		logger.trace("creating AssertScrolledMaxJob-Handler");
		tmp = new AssertScrolledMaxJobHandler();
		ctx.getAutowireCapableBeanFactory().autowireBean(tmp);
		addHandler(AssertScrolledMaxJob.class, tmp);

		logger.trace("creating AssertElementExistsJob-Handler");
		tmp = new AssertElementExistsJobHandler();
		ctx.getAutowireCapableBeanFactory().autowireBean(tmp);
		addHandler(AssertElementExistsJob.class, tmp);

		logger.trace("creating AssertProxyByGeosurf-Handler");
		tmp = new AssertProxyByGeosurfJobHandler();
		ctx.getAutowireCapableBeanFactory().autowireBean(tmp);
		addHandler(AssertProxyByGeosurfJob.class, tmp);

		logger.trace("creating AssertProxyByPrimemoverApi-Handler");
		tmp = new AssertProxyByPrimemoverApiJobHandler();
		ctx.getAutowireCapableBeanFactory().autowireBean(tmp);
		addHandler(AssertProxyByPrimemoverApiJob.class, tmp);
	}

	// constructor
	public JobDispatcher() {
	}

	// Call the job specific handler
	@Override
	public void handle(IJobAwareRunnerContext<?> context) {
		IJob job = context.getJob();
		IJobHandler h = dispatch.get(job.getClass());
		if (h == null) {
			throw new UnsupportedOperationException("This type is not yet supported");
		}

		if (job.isFailsave()) {
			// catch any exception
			try {
				h.handle(context);
			} catch (Exception e) {
				logger.warn("Error in Failsave Job {} of type {}", job.getId(), job.getClass().getSimpleName(), e);
			}
		} else {
			h.handle(context);
		}

	}
}
