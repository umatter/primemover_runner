package primemover.runner;

import java.net.MalformedURLException;
import java.net.URL;
import java.time.ZonedDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicBoolean;

import org.apache.commons.lang3.StringUtils;
import org.openqa.selenium.OutputType;
import org.openqa.selenium.TakesScreenshot;
import org.openqa.selenium.chrome.ChromeDriver;
import org.openqa.selenium.chrome.ChromeOptions;
import org.openqa.selenium.firefox.FirefoxDriver;
import org.openqa.selenium.firefox.FirefoxOptions;
import org.openqa.selenium.remote.CapabilityType;
import org.openqa.selenium.remote.DesiredCapabilities;
import org.openqa.selenium.remote.RemoteWebDriver;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;

import com.browserup.harreader.model.Har;

import primemover.anticaptcha.CaptchaSolverFactory;
import primemover.anticaptcha.ICaptchaSolver;
import primemover.capture.RecordingProxy;
import primemover.capture.RecordingProxyFactory;
import primemover.model.Crawler;
import primemover.model.IJob;
import primemover.model.JobState;
import primemover.model.Joblist;
import primemover.model.LocationInfo;
import primemover.model.MultiLoginAgent;
import primemover.model.jobs.VisitJob;
import primemover.multilogin.MulitLoginBrowserProfile;
import primemover.multilogin.MultiLoginBrowserProfileFactory;
import primemover.runner.context.ChromeContext;
import primemover.runner.context.ContextBase;
import primemover.runner.context.FirefoxContext;
import primemover.runner.context.IJobAwareRunnerContext;
import primemover.runner.context.IJobDataRecorder;
import primemover.runner.context.JobAwarnessContextDecorator;
import primemover.runner.context.JobDataRecorder;
import primemover.runner.context.MultiloginContext;
import primemover.runner.job.JobDispatcher;
import primemover.service.AgentService;
import primemover.service.IJobService;
import primemover.service.IStorageService;
import primemover.service.IpService;
import primemover.service.IpServiceFactory;
import primemover.service.datawriter.DataWriterCachingDecorator;
import primemover.service.datawriter.IDataWriter;
import primemover.service.datawriter.IDataWriterFactory;

/**
 * Entry point for a task run. The operator will first setup the running context
 * (browser, proxy, anti-captcha) and run some checks. Then the containing
 * joblist will execute job by job. Data is gathered along the execution and
 * stored.
 * 
 * @author dburkolter
 *
 */
public class TaskOperator implements ITaskOperator {
	private static Logger logger = LoggerFactory.getLogger(TaskOperator.class);

	@Value("${multilogin.StartupRetryCount:3}")
	private Integer maxRetryCountMultiLogin;
	@Value("${multilogin.StartupDelaySeconds:30}")
	private Integer retryDelayMultiLogin;
	@Value("${runner.StartupRetryCount:3}")
	private Integer maxRetryCountInit;
	@Value("${anticaptcha.check.enabled:true}")
	private Boolean runCaptchaChecks;
	@Value("${runner.skipIpRecording:false}")
	private boolean skipIpRecording = false;

	private ContextBase<?> context = null;

	@Autowired
	private IJobService jobService;

	@Autowired
	private AgentService agentService;

	@Autowired(required = false)
	List<IStorageService> storages = new ArrayList<IStorageService>();

	@Autowired(required = false)
	List<IDataWriterFactory> writerFactories = new ArrayList<IDataWriterFactory>();

	@Autowired
	private RecordingProxyFactory recorderFactory;

	@Autowired
	private MultiLoginBrowserProfileFactory browserProfileFactory;

	@Autowired
	private JobDispatcher runJobHandler;

	@Autowired
	IpServiceFactory ipServiceFactory;

	@Autowired
	CaptchaSolverFactory captchaSolverFac;

	private IpService ipService;

	private AtomicBoolean isSetup = new AtomicBoolean(false);

	private void setupIpService(RecordingProxy recorder) {
		ipService = ipServiceFactory.getInstance(recorder);
	}

	private ContextBase<?> startupFirefox(Crawler crawler, DesiredCapabilities capabilities, RecordingProxy recorder,
			ICaptchaSolver captchaSolver) {
		FirefoxContext ffContext = new FirefoxContext();
		ffContext.setCrawler(crawler);
		ffContext.setRecordingProxy(recorder);

		FirefoxOptions firefoxOptions = new FirefoxOptions();
		firefoxOptions.merge(capabilities);
		firefoxOptions.setAcceptInsecureCerts(true);
		captchaSolver.setOptions(crawler, firefoxOptions, recorder);
		ffContext.setFirefoxOptions(firefoxOptions);
		ffContext.setDriver(new FirefoxDriver(firefoxOptions));

		return ffContext;
	}

	private ContextBase<?> startupChrome(Crawler crawler, DesiredCapabilities capabilities, RecordingProxy recorder,
			ICaptchaSolver captchaSolver) {
		ChromeContext chContext = new ChromeContext();
		chContext.setCrawler(crawler);
		chContext.setRecordingProxy(recorder);

		ChromeOptions chromeOptions = new ChromeOptions();
		chromeOptions.merge(capabilities);
		chromeOptions.addArguments("--start-maximized");
		// make navigator.webriver disappear
		// see
		// https://stackoverflow.com/questions/53039551/selenium-webdriver-modifying-navigator-webdriver-flag-to-prevent-selenium-detec
		chromeOptions.addArguments("--disable-blink-features");
		chromeOptions.addArguments("--disable-blink-features=AutomationControlled");

		captchaSolver.setOptions(crawler, chromeOptions, recorder);

		chContext.setChromeOptions(chromeOptions);

		chContext.setDriver(new ChromeDriver(chromeOptions));

		return chContext;
	}

	private ContextBase<?> startupMultilogin(Crawler crawler, DesiredCapabilities capabilities, RecordingProxy recorder,
			ICaptchaSolver captchaSolver) {
		MultiloginContext mlContext = new MultiloginContext();
		mlContext.setCrawler(crawler);
		mlContext.setRecordingProxy(recorder);

		// setup anticaptcha
		captchaSolver.setOptions(crawler, capabilities, recorder);

		Boolean done = false;
		Integer retryCount = 0;

		MultiLoginAgent typedAgent = (MultiLoginAgent) crawler.getAgent();

		// ensure multilogin profile
		MulitLoginBrowserProfile bp;

		if (StringUtils.isEmpty(typedAgent.getMultiLoginId())) {
			try {
				String verifiyId = agentService.getMultiLoginIdFromServer(typedAgent);

				if (StringUtils.isEmpty(verifiyId)) {
					bp = browserProfileFactory.getItem(crawler, recorder);
					agentService.updateMultiloginProfile(typedAgent, bp.getProfileId());
				} else {
					bp = browserProfileFactory.getItem(verifiyId);
				}
			} catch (Exception ex) {
				logger.warn("could not create MultiLoginProfile for agent {}", typedAgent.getId(), ex);

				throw new IllegalStateException("could not create MultiLoginProfile", ex);
			}
		} else {
			bp = browserProfileFactory.getItem(typedAgent.getMultiLoginId());
		}

		// startup
		while (!done && retryCount < maxRetryCountMultiLogin) {
			retryCount += 1;
			if (retryCount > 1) {
				logger.info("retry starting profile");
			}

			try {
				// apply proxy
				logger.debug("updating proxy");
				bp.updateProxy(recorder);

				logger.debug("starting remote MultiLogin driver");
				URL remoteUrl = new URL(bp.startProfile());
				RemoteWebDriver driver = new RemoteWebDriver(remoteUrl, capabilities);
				done = true;

				mlContext.setProfile(bp);
				mlContext.setDriver(driver);
			} catch (MalformedURLException e) {
				logger.error("catched exception starting profile", e);
			} catch (Exception e) {
				logger.error("catched exception starting profile", e);
			}

			if (!done) {
				try {
					logger.debug("sleep for {} second(s) before retry", this.retryDelayMultiLogin);
					Thread.sleep(this.retryDelayMultiLogin * 1000);
				} catch (InterruptedException e) {
					// nothing to do
				}
			}
		}

		if (!done) {
			crawler = null;
			logger.error("Could not start MulitLogin-Profile for agent {}", typedAgent.getId());
			throw new IllegalStateException("Could not start MulitLogin-Profile");
		}

		return mlContext;
	}

	/**
	 * 
	 * @param currentTiming: current timing to compare against
	 * @param comparee:      compare currentTiming to this value
	 * @param data:          JobData to store captured location info
	 * @param job:           current job
	 */
	private void checkIpRecording(IJobAwareRunnerContext<?> jobContext, IpRecordingTiming currentTiming,
			IpRecordingTiming comparee) {
		if (currentTiming == IpRecordingTiming.NONE) {
			logger.debug("Found {} as current ip-recording timing", currentTiming);
			return;
		}

		// grab ip-location if matching above
		if (currentTiming == comparee || currentTiming == IpRecordingTiming.ALWAYS) {
			LocationInfo loc = ipService.getLocationInfo();
			logger.debug("captured location info for job {} for timing {} is {}", jobContext.getJob().getId(),
					currentTiming, loc);
			jobContext.getJobDataRecorder().setLocation(loc);
		}
	}

	private Boolean runJob(IJobAwareRunnerContext<?> jobContext, IpRecordingTiming ipRecording) {
		boolean runJobResult = false;

		IJob job = jobContext.getJob();
		IJobDataRecorder dataRecorder = jobContext.getJobDataRecorder();

		logger.info("Starting job {}", job.getId());

		try {
			jobService.updateJobState(job, JobState.RUNNING, null, null, ZonedDateTime.now(), null); // setting

			// IP-Checking
			try {
				checkIpRecording(jobContext, ipRecording, IpRecordingTiming.BEFORE_JOB);
			} catch (Exception e) {
				logger.warn("IP-Checking (before job) was unsuccessfull for job {}", job.getId());
			}

			// start MITM
			this.context.getRecordingProxy().startCapturing(job.getDescription(), job.getId() + '_' + job.getName());

			// running job
			try {
				runJobHandler.handle(jobContext);
			} catch (Exception e) {
				logger.warn("Catched exception running job {}", job.getId(), e);
				runJobResult = false;
			}

			// reading page-source ready to re-use
			String pageSource = null;
			try {
				pageSource = primemover.util.Page.getBodyeFromJavascript(context.getDriver());
			} catch (Exception ex) {
				logger.debug(
						"Could not read page source for job via Javascript. Falling back to direct driver read. jobid {}",
						job.getId(), ex);

				try {
					pageSource = context.getDriver().getPageSource();
				} catch (Exception e) {
					logger.warn("Could not read page source from driver. No more Fallback possible. jobid {}",
							job.getId(), ex);
				}
			}

			// captcha verification
			Boolean captchaFound = null;
			if (this.runCaptchaChecks) {
				if (StringUtils.isNotEmpty(pageSource)) {
					String lowerPageSource = pageSource.toLowerCase();
					if (StringUtils.contains(lowerPageSource, "captcha")) {
						captchaFound = true;
						logger.warn("captcha found in job {}", job.getId());
					} else {
						captchaFound = false;
					}
				} else {
					logger.warn("no page source available for captcha-check for job {}", job.getId());
				}
			}

			jobService.updateJobState(job, JobState.STORINGDATA, captchaFound, null, null, null);

			// save capturing screenshot
			try {
				if (context.getDriver() instanceof TakesScreenshot) {

					TakesScreenshot camera = (TakesScreenshot) context.getDriver();
					byte[] screenshot = camera.getScreenshotAs(OutputType.BYTES);

					dataRecorder.setScreenshot(screenshot);
				}
			} catch (Exception e) {
				// noting to do
			}

			// capture trafic data
			Har har = context.getRecordingProxy().stopCapturing();
			dataRecorder.setHar(har);

			// capture page data
			if (StringUtils.isEmpty(pageSource)) {
				dataRecorder.setPageSource(null);
				logger.warn("no page source available for job {}", job.getId());
			} else {
				dataRecorder.setPageSource(pageSource);
			}

			// capture cookies, localstorage, etc.
			try {
				dataRecorder.setCookies(context.getDriver().manage().getCookies());
			} catch (Exception ex) {
				logger.warn("Could not read cookies for job {}", job.getId(), ex);
				dataRecorder.setCookies(null);
			}

			try {
				dataRecorder.setLocalStorage(primemover.util.LocalStorage.getItems(context.getDriver()));
			} catch (Exception ex) {
				logger.warn("Could not read local storage for job {}", job.getId(), ex);
				dataRecorder.setLocalStorage(null);
			}

			try {
				dataRecorder.setSessionStorage(primemover.util.SessionStorage.getItems(context.getDriver()));
			} catch (Exception ex) {
				logger.warn("Could not read session storage for job {}", job.getId(), ex);
				dataRecorder.setSessionStorage(null);
			}

			// IP-Checking
			try {
				checkIpRecording(jobContext, ipRecording, IpRecordingTiming.AFTER_JOB);
			} catch (Exception e) {
				logger.warn("IP-Checking (after job) was unsuccessfull for job {}", job.getId());
			}

			// Prepare writers
			List<IDataWriter> writers = new ArrayList<>();
			writerFactories.forEach((factory) -> {
				writers.add(factory.getWriter(jobContext.getJobDataRecorder().getRecordings()));
			});

			boolean writeRes = true;
			// writing data

			for (IDataWriter writer : writers) {
				IDataWriter cachedWriter = new DataWriterCachingDecorator(writer);
				for (IStorageService storage : storages) {
					try {
						writeRes = writeRes && storage.writeData(job, cachedWriter);
					} catch (Exception e) {
						logger.warn("Error writing to job-data to storage({}) for job {}", storage, job.getId(), e);
						writeRes = false;
					}
				}
			}

			if (writeRes) {
				logger.info("Done running job {}", job.getId());
				jobService.updateJobState(job, JobState.SUCCESS, null, null, null, ZonedDateTime.now()); // setting
																											// endtime
			} else {
				logger.warn("Done running job with warning {}", job.getId());
				jobService.updateJobState(job, JobState.WARNING_JOBDATA, null, null, null, ZonedDateTime.now()); // setting
																													// endtime
			}

			runJobResult = true;

		} catch (Exception ex) {
			logger.error("Exception executing job {}", job.getId(), ex);

			try {
				jobService.updateJobState(job, JobState.FAILURE, null, null, null, ZonedDateTime.now()); // setting
																											// endtime
			} catch (Exception e) {
				logger.error("Could not write Job-State for {}", job.getId(), ex); // FATAL!!!
			}

			runJobResult = false;
		}

		return runJobResult;
	}

	private RecordingProxy setupRecorder(Crawler crawler) {
		Integer curRetry = 1;

		while (curRetry <= 3) {
			try {
				logger.debug("setting up recorder for {} ({} of {})", crawler.getId(), curRetry, 3);

				// create a recording proxy. Check also for any additional proxy
				return recorderFactory.createRecordingProxy(crawler.getProxy());
			} catch (IllegalStateException ex) {
				if (curRetry < 3) {
					logger.warn("setting up recorder for {} failed ({} of {})", crawler.getId(), curRetry, 3);
				} else {
					logger.error("could not set up recorder for crawler {}", crawler.getId(), ex);
					throw ex;
				}
			}

			curRetry++;
		}

		throw new IllegalStateException("Could not establish a recording proxy");
	}

	/**
	 * prepare context etc. before the job run
	 */
	public IJobAwareRunnerContext<?> getJobAwareContext(IJob job) {

		return JobAwarnessContextDecorator.decorate(context, job, new JobDataRecorder(job));
	}

	@Override
	public Boolean runJobList(Joblist jobs) {
		Boolean res = false;
		if (!isSetup.get()) {
			throw new IllegalStateException("please setup service first");
		}

		// run job list
		try {
			logger.info("start running joblist {} of crawler {}", jobs.getId(), this.context.getCrawler().getId());
			boolean previousResult = true;
			IJob previousJob = null;
			List<IJob> jobList = jobs.getJobs();

			for (int i = 0; i < jobList.size(); i++) {
				IJob job = jobList.get(i);

				boolean runCurrentJob = previousResult;

				// fallback from failure
				if (job instanceof VisitJob) {
					runCurrentJob = true;
				}

				if (runCurrentJob) {
					boolean currentResult = previousResult;
					IJobAwareRunnerContext<?> jobAwareContext = getJobAwareContext(job);

					if (skipIpRecording) {
						currentResult = runJob(jobAwareContext, IpRecordingTiming.NONE);
					} else if (i == 0) {
						currentResult = runJob(jobAwareContext, IpRecordingTiming.BEFORE_JOB);
					} else if (i == jobList.size() - 1) {
						currentResult = runJob(jobAwareContext, IpRecordingTiming.AFTER_JOB);
					} else {
						currentResult = runJob(jobAwareContext, IpRecordingTiming.NONE);
					}

					logger.info("finished job ({}) with {}", job.getId(), currentResult ? "success" : "failure");

					previousJob = job;
					previousResult = currentResult;
				} else {
					logger.info("skipping job ({}) due to error in previous job ({})", job.getId(),
							previousJob == null ? -1 : previousJob.getId());
					jobService.updateJobState(job, JobState.SKIPPED, null, null, ZonedDateTime.now(),
							ZonedDateTime.now());
				}
			}

			res = true;
		} catch (

		Exception ex) {
			logger.warn("Error running joblist {}", jobs.getId(), ex);
		}

		return res;
	}

	@Override
	public Boolean prepareExeuction(Crawler crawler) {

		boolean done = false;
		Integer curRetry = 0;

		while (!done && curRetry < maxRetryCountInit) {

			curRetry++;

			// create driver
			try {
				logger.info("starting up crawler {}, round {} of {}", crawler.getId(), curRetry, maxRetryCountInit);
				// Default capabilities for ssl handling
				DesiredCapabilities capabilities = new DesiredCapabilities();

				// siehe https://www.guru99.com/ssl-certificate-error-handling-selenium.html
				capabilities.setCapability(CapabilityType.ACCEPT_INSECURE_CERTS, true);

				// stop recorder if in retry
				RecordingProxy recorder = null;
				if (context != null) {
					recorder = context.getRecordingProxy();
					if (recorder != null) {
						recorder.tearDown();
					}
				}

				// start new recorder
				recorder = setupRecorder(crawler);
				recorder.applyProxy(capabilities);

				// setup IpService
				setupIpService(recorder);

				// setup anti-captcha
				ICaptchaSolver captchaSolver = captchaSolverFac.getCaptchaSolver();

				switch (crawler.getAgent().getType()) {
				case CHROME:
					this.context = startupChrome(crawler, capabilities, recorder, captchaSolver);
					break;
				case FIREFOX:
					this.context = startupFirefox(crawler, capabilities, recorder, captchaSolver);
					break;
				case MULTILOGIN:
					this.context = startupMultilogin(crawler, capabilities, recorder, captchaSolver);
					break;

				default:
					logger.error("unsupported type {} for agent {}", crawler.getAgent().getType(),
							crawler.getAgent().getId());
					throw new UnsupportedOperationException("type not supported: " + crawler.getAgent().getType());
				}

				// apply captcha solver to context
				context.setCaptchaSolver(captchaSolver);

				// setup driver in running browser
				captchaSolver.setupDriver(context);

				logger.debug("finished starting up crawler {}, success: {}", crawler.getId(), true);

				isSetup.set(true);
				done = true;
			} catch (Exception ex) {
				logger.error("error during setup for crawler {}", crawler.getId(), ex);
			}
		}

		// Verify result and return
		return done && context.isValid();
	}

	@Override
	public void tearDown(boolean immediate) {

		if (context == null) {
			return; // nothing to do
		}

		logger.info("Tearing down crawler {}", context.getCrawler().getId());

		isSetup.set(false);
		ipService = null;

		// close webdriver
		try {
			context.getDriver().quit();
		} catch (Exception e) {
			logger.warn("Exception quitting driver for crawler {}", context.getCrawler().getId());
		}

		// Multilogin needs a wait to finish of profile tear down and storage. Upload
		// will run through proxy!
		if (context instanceof MultiloginContext) {
			MultiloginContext typedContext = (MultiloginContext) context;
			MulitLoginBrowserProfile bp = typedContext.getProfile();

			try {
				int round = 0;
				int maxRounds = 10;
				int delayMillis = 25000;
				Thread.sleep(delayMillis); // will need to sleep anyways. Profile is inactive while still uploading

				while (round < maxRounds && bp.isActive()) {
					logger.debug("MultiLogin Profile is still actice {}, retry {} of {}", bp.getProfileId(), round + 1,
							maxRounds);
					Thread.sleep(delayMillis);
					bp.stopProfile();
					round++;
				}
			} catch (Exception e) {
				logger.error("Error closing browser profile for agent {}", typedContext.getCrawler().getAgent().getId(),
						e);
			}
		}

		// quit the Webdriver (fallback in case of MultiLogin)
		try {
			context.getDriver().quit();
		} catch (Exception e) {
			logger.error("Error closing webdriver for agent {}", context.getCrawler().getAgent().getId(), e);
		}

		try {
			context.getRecordingProxy().tearDown();
		} catch (Exception e) {
			logger.warn("Exception quitting recorder for crawler {}", context.getCrawler().getId());
		}

		context = null;
	}

	public void tearDown() {
		tearDown(false);
	}

	public boolean isSkipIpRecording() {
		return skipIpRecording;
	}

	public void setSkipIpRecording(boolean skipIpRecording) {
		this.skipIpRecording = skipIpRecording;
	}

}
