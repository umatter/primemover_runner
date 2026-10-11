package primemover.runner.job;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.BeansException;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.ApplicationContext;
import org.springframework.context.ApplicationContextAware;
import primemover.config.StartupMode;
import primemover.model.IJob;
import primemover.model.jobs.*;
import primemover.runner.context.IJobAwareRunnerContext;
import primemover.util.consent.ConsentChoice;
import primemover.util.consent.ConsentPolicy;
import primemover.runner.job.handler.*;

import java.util.HashMap;
import java.util.Map;

public class JobDispatcher implements IJobDispatcher, ApplicationContextAware {

	private static Logger logger = LoggerFactory.getLogger(JobDispatcher.class);

	@Value("${global.mode}")
	private StartupMode mode;

	/**
	 * Runner-wide answer to cookie/GDPR consent dialogs when neither the job
	 * (behaviour {@code consent}) nor the crawler ({@code cookie_pref}) says
	 * otherwise. See {@link ConsentPolicy}.
	 */
	@Value("${consent.choice:accept}")
	private String consentChoiceProperty;

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

		// A job a study uploads belongs above the early return below: in PRODUCTION nothing
		// after it is registered, and an unregistered type fails as "not yet supported".
		logger.trace("creating ClearStorage-Handler");
		tmp = new ClearStorageJobHandler();
		ctx.getAutowireCapableBeanFactory().autowireBean(tmp);
		addHandler(ClearStorageJob.class, tmp);

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

		logger.trace("creating AssertProxyByOxylabs-Handler");
		tmp = new AssertProxyByOxylabsJobHandler();
		ctx.getAutowireCapableBeanFactory().autowireBean(tmp);
		addHandler(AssertProxyByOxylabsJob.class, tmp);
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

		// Bind the consent policy for this job to the executor thread so that
		// navigation code and the modal dismisser answer consent dialogs the way
		// this bot's arm requires.
		ConsentChoice consent = ConsentPolicy.resolve(job, context.getCrawler(),
				ConsentPolicy.runnerDefault(consentChoiceProperty));
		ConsentPolicy.bind(consent);
		try {
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
		} finally {
			ConsentPolicy.unbind();
		}

	}
}
