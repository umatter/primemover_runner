package primemover.runner;

import java.time.Duration;
import io.github.bonigarcia.wdm.WebDriverManager;
import org.apache.commons.lang3.StringUtils;
import org.openqa.selenium.OutputType;
import org.openqa.selenium.TakesScreenshot;
import org.openqa.selenium.chrome.ChromeDriver;
import org.openqa.selenium.chrome.ChromeOptions;
import org.openqa.selenium.firefox.FirefoxDriver;
import org.openqa.selenium.firefox.FirefoxOptions;
import org.openqa.selenium.remote.Augmenter;
import org.openqa.selenium.remote.DesiredCapabilities;
import org.openqa.selenium.remote.RemoteWebDriver;
import org.openqa.selenium.chromium.HasCdp;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.slf4j.MDC;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import primemover.anticaptcha.CaptchaSolverFactory;
import primemover.anticaptcha.ChallengePageDetector;
import primemover.anticaptcha.ICaptchaSolver;
import primemover.capture.HarRecordingFilter;
import primemover.capture.RecordingProxy;
import primemover.capture.RecordingProxyFactory;
import primemover.model.*;
import primemover.model.jobs.LambdaSelectJob;
import primemover.model.jobs.VisitJob;
import primemover.util.BrowserUnresponsiveException;
import primemover.util.PageLoadGuard;
import primemover.util.RendererRecycler;
import primemover.util.StartupBackoff;
import primemover.multilogin.ml6.MulitLoginBrowserProfile;
import primemover.multilogin.ml6.MultiLoginBrowserProfileFactory;
import primemover.runner.context.*;
import primemover.runner.job.JobDispatcher;
import primemover.service.*;
import primemover.service.datawriter.DataWriterCachingDecorator;
import primemover.service.datawriter.IDataWriter;
import primemover.service.datawriter.IDataWriterFactory;

import java.net.MalformedURLException;
import java.net.URL;
import java.time.ZonedDateTime;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.atomic.AtomicBoolean;

import javax.annotation.Nullable;

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

	/**
	 * MDC key carrying the id of the job currently executing. Set for the
	 * duration of one job and removed afterwards -- the executor pool reuses
	 * threads, so a leaked job.id would mislabel every later line on that thread.
	 * Kept as an individual key (like crawler.id / joblist.id) because promtail
	 * extracts those by name from the rendered MDC map.
	 */
	private static final String MDC_JOB_ID = "job.id";

	/** MDC key holding the hierarchical correlation id; set by JobListRunner. */
	private static final String MDC_CORRELATION_ID = "correlationId";

	/**
	 * Why a MULTILOGIN crawler cannot start on a runner with
	 * {@code multilogin.enabled=false}.
	 *
	 * The point of the flag is that a box with no Multilogin account still boots,
	 * so the refusal has to cost this crawler its run and nothing else: no beans
	 * exist to call, but the JVM keeps serving every chrome crawler behind it.
	 */
	static final String MULTILOGIN_DISABLED_MESSAGE =
			"Multilogin is disabled on this runner (multilogin.enabled=false); use a chrome agent or enable it";

	/**
	 * Delay between startup retries for an ordinary failure. Short: a transient
	 * local problem should retry promptly.
	 */
	@Value("${multilogin.StartupRetryDelaySeconds:5}")
	private int startupRetryDelaySeconds;

	/**
	 * Delay between startup retries after a rate-limit rejection. Must exceed the
	 * Multilogin window (50 operations per minute) or the retry is spent inside
	 * the same window that rejected the first attempt.
	 */
	@Value("${multilogin.RateLimitBackoffSeconds:75}")
	private int rateLimitBackoffSeconds;

	@Value("${multilogin.StartupRetryCount:3}")
	private Integer maxRetryCountMultiLogin;
	@Value("${multilogin.StartupDelaySeconds:30}")
	private Integer retryDelayMultiLogin;
	@Value("${runner.StartupRetryCount:3}")
	private Integer maxRetryCountInit;
	@Value("${anticaptcha.check.enabled:true}")
	private Boolean runCaptchaChecks;
	// How many navigations one renderer may serve before it is bounced. The V8
	// heap leaks ~14 MB per same-site SPA navigation, so this bounds peak heap
	// instead of letting it grow with queue length. 0 disables recycling.
	@Value("${runner.RendererRecycleAfterNavigations:25}")
	private int rendererRecycleAfterNavigations;

	// Upper bound for one page load. A page that never finishes (a bot-protection
	// page on experiment 189) used to hold a visit job for ~27 minutes. 0 keeps
	// Selenium's own 300 s default.
	@Value("${runner.PageLoadTimeoutSeconds:60}")
	private int pageLoadTimeoutSeconds;

	// Set when the joblist has been cut off (processing timeout). The job loop stops
	// at the next job boundary; the one in flight fails as its browser goes away.
	private volatile boolean aborted = false;

	// Set when a job found the browser frozen (BrowserUnresponsiveException). Such a
	// browser did not come back on experiment 189, so the rest of the queue is
	// skipped instead of spending ~4 minutes per visit failing the same way.
	private volatile boolean browserLost = false;

	// The state the last finished job was reported with, so runJobList can tell a
	// clean success from a completed-but-degraded one. Safe as a field: a
	// TaskOperator is created per joblist by TaskOperatorFactory and its jobs run
	// sequentially.
	private JobState lastJobState;

	@Value("${runner.skipIpRecording:false}")
	private boolean skipIpRecording = false;

	// Whether this runner may drive Multilogin at all. False removes the whole
	// MlxConfiguration (see its javadoc), so there is no MLX context builder to
	// call and a MULTILOGIN crawler has to be refused rather than started.
	@Value("${multilogin.enabled:true}")
	private boolean multiloginEnabled = true;

	// Developer convenience only: run plain Chrome with no X display. Production
	// runs Multilogin on a real display and leaves this false.
	@Value("${runner.chromeHeadless:false}")
	private boolean chromeHeadless = false;

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

	// required=false: with multilogin.enabled=false there are NO IContextBuilder
	// beans, and Spring fails a Map injection that matches nothing. That would
	// break TaskOperatorFactory.getInstance() for every crawler, chrome ones
	// included -- the exact opposite of what disabling Multilogin is for.
	@Autowired(required = false)
	Map<String, IContextBuilder> contextBuilders = Collections.emptyMap();

	@Autowired
	private HarRecordingFilter harRecordingFilter;

	private IpService ipService;

	private AtomicBoolean isSetup = new AtomicBoolean(false);

	private void setupIpService(RecordingProxy recorder) {
		ipService = ipServiceFactory.getInstance(recorder);
	}

	private ContextBase<?> startupFirefox(Crawler crawler, DesiredCapabilities capabilities, RecordingProxy recorder,
			ICaptchaSolver captchaSolver) {

		WebDriverManager.firefoxdriver().setup();

		FirefoxContext ffContext = new FirefoxContext();
		ffContext.setCrawler(crawler);
		ffContext.setRecordingProxy(recorder);

		FirefoxOptions firefoxOptions = new FirefoxOptions();
		firefoxOptions = firefoxOptions.merge(capabilities);
		firefoxOptions.setAcceptInsecureCerts(true);

		captchaSolver.setOptions(crawler, firefoxOptions, recorder);
		ffContext.setFirefoxOptions(firefoxOptions);
		ffContext.setDriver(new FirefoxDriver(firefoxOptions));

		return ffContext;
	}

	/**
	 * The ChromeOptions a plain-Chrome crawler is started with.
	 *
	 * Package-private and static so the option set can be asserted without a
	 * browser, a Spring context or a display. With {@code headless} false the
	 * result is exactly what this method produced before the flag existed --
	 * production runs Multilogin, not this path, and must not shift.
	 *
	 * @param headless value of {@code runner.chromeHeadless}
	 */
	static ChromeOptions buildChromeOptions(DesiredCapabilities capabilities, boolean headless) {
		ChromeOptions chromeOptions = new ChromeOptions();
		chromeOptions = chromeOptions.merge(capabilities); // Bound to changes in the versions for application of
															// desired capabilities...
		chromeOptions.addArguments("--start-maximized");
		// make navigator.webriver disappear
		// see
		// https://stackoverflow.com/questions/53039551/selenium-webdriver-modifying-navigator-webdriver-flag-to-prevent-selenium-detec
		chromeOptions.addArguments("--disable-blink-features");
		chromeOptions.addArguments("--disable-blink-features=AutomationControlled");
		chromeOptions.setAcceptInsecureCerts(true);

		if (headless) {
			// Chrome's current headless implementation ("new"), not the old
			// --headless, which is a different browser build and behaves
			// differently around media.
			chromeOptions.addArguments("--headless=new");
			// Deliberately short: these three are what a display-less container
			// or CI runner needs, nothing more. --start-maximized above is a
			// no-op without a window manager, so give the window a real size.
			chromeOptions.addArguments("--disable-gpu");
			chromeOptions.addArguments("--no-sandbox");
			chromeOptions.addArguments("--window-size=1920,1080");
		}

		return chromeOptions;
	}

	private ContextBase<?> startupChrome(Crawler crawler, DesiredCapabilities capabilities, RecordingProxy recorder,
			ICaptchaSolver captchaSolver) {

		WebDriverManager.chromedriver().setup();

		ChromeContext chContext = new ChromeContext();
		chContext.setCrawler(crawler);
		chContext.setRecordingProxy(recorder);

		ChromeOptions chromeOptions = buildChromeOptions(capabilities, chromeHeadless);

		logger.debug("chrome options", chromeOptions);

		captchaSolver.setOptions(crawler, chromeOptions, recorder);

		chContext.setChromeOptions(chromeOptions);

		chContext.setDriver(new ChromeDriver(chromeOptions));

		return chContext;
	}
	
	private MultiloginContext startupMultiloginX(Crawler crawler, DesiredCapabilities capabilities, RecordingProxy recorder,
			ICaptchaSolver captchaSolver) {
		
		return contextBuilders.get("MlxContextBuilder").buildContext(crawler, capabilities, recorder, captchaSolver);
	}

	private ContextBase<?> startupMultilogin(Crawler crawler, DesiredCapabilities capabilities, RecordingProxy recorder,
			ICaptchaSolver captchaSolver) {
		MultiLoginAgent typedAgent = (MultiLoginAgent) crawler.getAgent();
		
		if (typedAgent.getVersion() == MultiLoginVersion.MLX) {
			return startupMultiloginX(crawler, capabilities, recorder, captchaSolver);
		}
		
		// ensure multilogin profile (create or return)
		// needs to be done before setting params to the profile (Plugins, Proxy, etc)
		MulitLoginBrowserProfile bp;

		if (StringUtils.isEmpty(typedAgent.getMultiLoginId())) {
			try {
				bp = browserProfileFactory.getItem(crawler, recorder);
				agentService.updateMultiloginProfile(typedAgent, bp.getProfileId());
			} catch (Exception ex) {
				logger.warn("could not create MultiLoginProfile for agent {}", typedAgent.getId(), ex);

				throw new IllegalStateException("could not create MultiLoginProfile", ex);
			}
		} else {
			bp = browserProfileFactory.getItem(typedAgent.getMultiLoginId());
		}

		// build context
		MultiloginContext mlContext = new MultiloginContext();
		mlContext.setCrawler(crawler);
		mlContext.setRecordingProxy(recorder);

		// setup anticaptcha
		captchaSolver.setOptions(crawler, capabilities, recorder);

		Boolean done = false;
		Integer retryCount = 0;

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

	/**
	 * Report a job state to the API and complain loudly if the report did not
	 * land.
	 *
	 * <p>
	 * {@code updateJobState} returns {@code false} when the update fails. Every
	 * caller used to discard that, so a finished job could sit at "running" in the
	 * database forever with nothing in the log saying why. It is an ERROR: the job
	 * really ran, and the record of it is now wrong.
	 *
	 * <p>
	 * Exceptions are deliberately <b>not</b> caught here -- the callers' existing
	 * error handling depends on them propagating.
	 */
	private void reportJobState(IJob job, JobState state, String detail, Boolean captchaFound, Boolean captchaSolved,
			ZonedDateTime startTime, ZonedDateTime endTime) {
		reportJobState(job, state, detail, captchaFound, captchaSolved, startTime, endTime, null);
	}

	/**
	 * As above, additionally carrying the decision function's verdict for this job.
	 *
	 * <p>
	 * Only the <em>terminal</em> reports pass one: the verdict does not exist yet
	 * when the job is reported RUNNING or STORINGDATA, and a SKIPPED job never ran
	 * a handler at all. {@code null} means "no verdict", which the service turns
	 * into "send no decision fields" and the API reads as unknown -- never as
	 * clean.
	 */
	private void reportJobState(IJob job, JobState state, String detail, Boolean captchaFound, Boolean captchaSolved,
			ZonedDateTime startTime, ZonedDateTime endTime, DecisionVerdict decision) {
		Boolean reported = jobService.updateJobState(job, state, detail, captchaFound, captchaSolved, startTime,
				endTime, decision);

		if (!Boolean.TRUE.equals(reported)) {
			logger.error("Could not report state {} for job {}."
					+ " The job stays at its previous state in the database.", state, job.getId());
		}
	}

	private Boolean runJob(IJobAwareRunnerContext<?> jobContext, IpRecordingTiming ipRecording) {
		IJob job = jobContext.getJob();

		// Job-scoped logging context. Keep the individual keys promtail reads
		// (crawler.id, crawler.name, joblist.id are set by JobListRunner) and add
		// job.id plus the |j-N segment of the canonical correlation id for the
		// duration of this job only.
		String outerCorrelationId = MDC.get(MDC_CORRELATION_ID);
		MDC.put(MDC_JOB_ID, job.getId().toString());
		if (outerCorrelationId != null) {
			MDC.put(MDC_CORRELATION_ID, JobDiagnostics.withJob(outerCorrelationId, job.getId()));
		}

		// Warnings a job handler attaches while it runs; see JobDiagnostics.
		JobDiagnostics.beginJob();

		try {
			return runJobInContext(jobContext, ipRecording);
		} finally {
			JobDiagnostics.endJob();
			MDC.remove(MDC_JOB_ID);
			if (outerCorrelationId == null) {
				MDC.remove(MDC_CORRELATION_ID);
			} else {
				MDC.put(MDC_CORRELATION_ID, outerCorrelationId);
			}
		}
	}

	private Boolean runJobInContext(IJobAwareRunnerContext<?> jobContext, IpRecordingTiming ipRecording) {
		boolean runJobResult = true;

		IJob job = jobContext.getJob();
		IJobDataRecorder dataRecorder = jobContext.getJobDataRecorder();

		logger.info("Starting job {}", job.getId());

		// the exception that made the handler fail, kept so it can be reported to
		// the API instead of being reduced to a boolean and thrown away
		Exception jobFailure = null;

		try {
			reportJobState(job, JobState.RUNNING, null, null, null, ZonedDateTime.now(), null); // setting

			// IP-Checking
			try {
				checkIpRecording(jobContext, ipRecording, IpRecordingTiming.BEFORE_JOB);
			} catch (Exception e) {
				// The exception is logged: without it this failed silently on every session
				// for weeks, leaving job archives with no location entry and every job
				// still reported successful.
				logger.warn("IP-Checking (before job) was unsuccessfull for job {}", job.getId(), e);
			}

			// start MITM
			// Determine if HAR recording should be enabled for this job type
			boolean shouldRecordHar = harRecordingFilter.shouldRecordHar(job);

			// start MITM (conditionally based on job type)
			if (shouldRecordHar) {
				this.context.getRecordingProxy().startCapturing(job.getId() + '_' + job.getName());
			} else {
				logger.debug("Skipping HAR recording for job {} (type: {})", job.getId(), job.getClass().getSimpleName());
			}

			// running job
			try {
				runJobHandler.handle(jobContext);
			} catch (Exception e) {
				logger.warn("Catched exception running job {}", job.getId(), e);
				jobFailure = e;
				runJobResult = false;
				if (BrowserUnresponsiveException.isCausedBy(e)) {
					browserLost = true;
					logger.warn("browser is unresponsive after job {}; the rest of the queue will be skipped",
							job.getId());
				}
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

			// Challenge-page verification. Deliberately independent of the
			// `anticaptcha.check.enabled` flag above and of the job's own
			// captchaMode: a job that ENDS ON a block page is degraded whatever
			// those say, and switching off the naive substring check must not
			// switch off the one signal that means something.
			String currentUrl = null;
			try {
				currentUrl = context.getDriver().getCurrentUrl();
			} catch (Exception ex) {
				// same tolerance as the page-source read above: an unreadable URL
				// costs us the URL signature, not the job
				logger.debug("Could not read current URL for challenge-page check. jobid {}", job.getId(), ex);
			}

			CaptchaSolverResultHolder captchaResults = captchaResultsFor(dataRecorder);
			Optional<String> challenge = challengeWarning(currentUrl, pageSource, solvedOnFinalPage(captchaResults));
			if (challenge.isPresent()) {
				// a challenge page IS a captcha, so say so on the same field the API
				// already stores -- without touching the substring rule that sets it
				captchaFound = true;
				JobDiagnostics.addWarning(challenge.get());
			}
			if (captchaResults != null && captchaResults.isAnyCaptchaDetected()) {
				// so is one the solver met and got past before the page was read
				captchaFound = true;
			}

			reportJobState(job, JobState.STORINGDATA, null, captchaFound, captchaSolvedReport(captchaResults), null,
					null);

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

			// capture trafic data (conditionally based on job type)
			if (shouldRecordHar) {
				String har = context.getRecordingProxy().stopCapturing();
				dataRecorder.setHar(har);
			} else {
				// No HAR recording for this job type
				dataRecorder.setHar(null);
			}

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
				// The exception is logged: without it this failed silently on every session
				// for weeks, leaving job archives with no location entry and every job
				// still reported successful.
				logger.warn("IP-Checking (after job) was unsuccessfull for job {}", job.getId(), e);
			}

			// A dead renderer does not fail the write -- it produces a successful,
			// EMPTY archive. Five sessions on 2026-08-27 crashed mid-queue and every
			// one still reported success, three of them writing a 194-byte zip whose
			// only entry was `page_cookies: []`. WARNING_JOBDATA cannot catch that,
			// because the write genuinely succeeded. Absence of every browser-derived
			// field is the honest signal, and it routes through the existing warning
			// branch below without needing a new JobState.
			if (context.getDriver() != null) {
				RecordedJobData captured = dataRecorder.getRecordings();
				if (captured != null && captured.getPageSource() == null && captured.getScreenshot() == null
						&& captured.getLocalStorage() == null && captured.getSessionStorage() == null) {
					JobDiagnostics.addWarning(
							"no browser data recorded: page source, screenshot, local storage and session storage "
									+ "were all unavailable (the browser was most likely dead)");
				}
			}

			// warnings the handler attached to this job (e.g. the decision function
			// fell back to random selection); carried along with the recorded data
			List<String> jobWarnings = JobDiagnostics.warnings();

			// The decision function's verdict for this job, if it made a decision at
			// all. Only the lambda-select handler ever records one and the slot is
			// per-job, so this is null for every other job type -- which is what makes
			// the API store "unknown" for them rather than a manufactured "clean".
			// It is attached to every terminal state, failures included: a job that
			// could not click still had a decision, and whether that decision lost the
			// treatment is exactly what an analysis needs to know about the session.
			DecisionVerdict decision = JobDiagnostics.decisionVerdict();

			// Prepare writers
			List<IDataWriter> writers = new ArrayList<>();
			writerFactories.forEach((factory) -> {
				RecordedJobData recordings = jobContext.getJobDataRecorder().getRecordings();
				recordings.addWarnings(jobWarnings);
				writers.add(factory.getWriter(recordings));
			});

			boolean writeRes = true;
			// distinct causes of a failed write, so "problems writing jobresult" can
			// say which backend failed and why instead of only that it did
			Set<String> storageProblems = new LinkedHashSet<>();
			// writing data

			for (IDataWriter writer : writers) {
				IDataWriter cachedWriter = new DataWriterCachingDecorator(writer);
				for (IStorageService storage : storages) {
					try {
						// Invoke writeData first, THEN combine — do not use `writeRes && storage.writeData(...)`,
						// whose short-circuit skips every remaining storage once one has failed. With the S3
						// backends ordered ahead of FileStorageService, a failing S3 write (e.g. bad/rotated
						// credentials) would otherwise suppress the local write entirely, silently dropping HAR
						// and job data on disk. Each storage backend must be independent.
						boolean storageOk = storage.writeData(job, cachedWriter);
						if (!storageOk) {
							storageProblems.add(storage.getClass().getSimpleName() + ": write refused");
						}
						writeRes = writeRes && storageOk;
					} catch (Exception e) {
						logger.warn("Error writing to job-data to storage({}) for job {}", storage, job.getId(), e);
						storageProblems.add(storage.getClass().getSimpleName() + ": " + JobDiagnostics.describe(e));
						writeRes = false;
					}
				}
			}

			if (writeRes && runJobResult && jobWarnings.isEmpty()) {
				logger.info("Done running job {}", job.getId());
				lastJobState = JobState.SUCCESS;
				reportJobState(job, JobState.SUCCESS, null, null, null, null, ZonedDateTime.now(), decision); // setting
																												// endtime
			} else if (!runJobResult) {
				logger.warn("Done running job with failure {}", job.getId());
				// the real cause, not just "error during execution"
				lastJobState = JobState.FAILURE;
				reportJobState(job, JobState.FAILURE, JobDiagnostics.describe(jobFailure), null, null, null,
						ZonedDateTime.now(), decision); // setting endtime
			} else if (!writeRes) {
				logger.warn("Done running job with warning (job data not written) {}", job.getId());
				lastJobState = JobState.WARNING_JOBDATA;
				reportJobState(job, JobState.WARNING_JOBDATA, joinDetails(storageProblems, jobWarnings), null, null,
						null, ZonedDateTime.now(), decision); // setting endtime
			} else {
				// handler completed, but the result is degraded in a way the
				// experiment cares about -- must not render as a green Success pill
				logger.warn("Done running job with warning {}: {}", job.getId(), jobWarnings);
				lastJobState = JobState.WARNING;
				reportJobState(job, JobState.WARNING, String.join("; ", jobWarnings), null, null, null,
						ZonedDateTime.now(), decision); // setting endtime
			}

		} catch (Exception ex) {
			logger.error("Exception executing job {}", job.getId(), ex);

			try {
				// read here rather than reusing the local above: this catch also covers
				// the path that threw before that local was assigned
				reportJobState(job, JobState.FAILURE, JobDiagnostics.describe(jobFailure == null ? ex : jobFailure),
						null, null, null, ZonedDateTime.now(), JobDiagnostics.decisionVerdict()); // setting endtime
			} catch (Exception e) {
				// log the failure to WRITE the state (e), not the exception that got
				// us here (ex) -- that one is already logged above, and this branch is
				// the one nobody could diagnose
				logger.error("Could not write Job-State for {}", job.getId(), e); // FATAL!!!
			}

			runJobResult = false;
		}

		return runJobResult;
	}

	/**
	 * The warning a job earns for ending on a known challenge page, or empty.
	 *
	 * <p>
	 * A bot that finishes its job looking at "our systems have detected unusual
	 * traffic ... not a robot" did not do what the experiment asked, and nothing
	 * else in the pipeline notices: the handler completed, the data was written,
	 * and the job was therefore reported {@code SUCCESS}. Experiment 185 on
	 * 2026-09-20 is the measurement -- 22 of 22 bots that reached their first
	 * Google search landed on {@code /sorry/index}, the solver was disabled, and
	 * all 22 jobs are green in the database. Routing it through
	 * {@link JobDiagnostics#addWarning(String)} makes the existing final-state
	 * block report {@link JobState#WARNING} with the detail in
	 * {@code status_message}, and the queue rollup count it as warned. No new
	 * state, no API change.
	 *
	 * <p>
	 * A challenge the solver got through is not a degradation: the session
	 * continued to the page it wanted, so {@code solved} suppresses the warning.
	 *
	 * @param currentUrl the browser's current URL, or {@code null} if unreadable
	 * @param pageSource the page source, or {@code null} if unreadable
	 * @param solved     whether the solver got the page this job ended on past a
	 *                   challenge ({@link #solvedOnFinalPage})
	 */
	static Optional<String> challengeWarning(String currentUrl, String pageSource, boolean solved) {
		if (solved) {
			return Optional.empty();
		}

		return ChallengePageDetector.detect(currentUrl, pageSource)
				.map(label -> "challenge page: " + label + ", not solved");
	}

	/**
	 * The captcha solver's results for this job, or {@code null} when there are
	 * none or they cannot be read.
	 *
	 * <p>
	 * Both solve attempts ({@code BEFORE} and {@code AFTER}) run inside the job
	 * handler via {@code CaptchaJobHelper}, so by the time the page source is read
	 * the recorder already holds whichever results there were.
	 */
	private static @Nullable CaptchaSolverResultHolder captchaResultsFor(IJobDataRecorder dataRecorder) {
		try {
			RecordedJobData recordings = dataRecorder.getRecordings();
			return recordings == null ? null : recordings.getCaptchaResults();
		} catch (Exception ex) {
			logger.debug("Could not read captcha solver results", ex);
			return null;
		}
	}

	/**
	 * Whether the solver got the page this job <b>ended on</b> past a challenge.
	 *
	 * <p>
	 * Only the {@code AFTER} attempt looks at that page. A challenge solved
	 * {@code BEFORE} the job's action says nothing about where the action landed --
	 * a search can solve its way in and land on a fresh challenge -- and letting it
	 * count would report that job as a success. Anything we cannot read counts as
	 * <b>not solved</b>, which is the honest default: the page in front of us says
	 * "prove you are not a robot" and nothing told us otherwise.
	 */
	static boolean solvedOnFinalPage(@Nullable CaptchaSolverResultHolder results) {
		return results != null && results.getAfterJob() != null && results.getAfterJob().isCaptchaSolved();
	}

	/**
	 * {@code captcha_solved} as the API stores it: whether the challenge the solver
	 * met during this job was got past -- the {@code AFTER} attempt's verdict when it
	 * met one, else the {@code BEFORE} attempt's -- or {@code null} when it met none.
	 * {@code null} means "no challenge seen, or no solver ran", never "not solved".
	 */
	static @Nullable Boolean captchaSolvedReport(@Nullable CaptchaSolverResultHolder results) {
		if (results == null) {
			return null;
		}
		if (results.getAfterJob() != null && results.getAfterJob().isCaptchaDetected()) {
			return results.getAfterJob().isCaptchaSolved();
		}
		if (results.getBeforeJob() != null && results.getBeforeJob().isCaptchaDetected()) {
			return results.getBeforeJob().isCaptchaSolved();
		}
		return null;
	}

	/**
	 * Join storage problems and handler warnings into one single-line detail.
	 * JobService truncates it to the length the API column should carry.
	 */
	private static String joinDetails(Collection<String> first, Collection<String> second) {
		List<String> parts = new ArrayList<>(first);
		parts.addAll(second);
		return parts.isEmpty() ? null : String.join("; ", parts);
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
	public JobListOutcome runJobList(Joblist jobs) {
		JobListOutcome outcome = new JobListOutcome();
		if (!isSetup.get()) {
			throw new IllegalStateException("please setup service first");
		}

		// run job list
		try {
			logger.info("start running joblist {} of crawler {}", jobs.getId(), this.context.getCrawler().getId());
			boolean previousResult = true;
			IJob previousJob = null;
			List<IJob> jobList = jobs.getJobs();

			// Navigations served by the current renderer. Counted by observing the
			// URL actually change, so it stays correct whatever the workflow does.
			int navigationsThisRenderer = 0;
			String lastSeenUrl = null;

			for (int i = 0; i < jobList.size(); i++) {
				IJob job = jobList.get(i);

				String skipReason = sessionSkipReason(aborted, Thread.currentThread().isInterrupted(), browserLost);
				if (skipReason != null) {
					logger.warn("joblist {}: {}; not starting job ({})", jobs.getId(), skipReason, job.getId());
					reportJobState(job, JobState.SKIPPED, skipReason, null, null, ZonedDateTime.now(),
							ZonedDateTime.now());
					outcome.countSkipped();
					continue;
				}

				// Bounce only immediately before a job that navigates anyway, so the
				// reload costs nothing the workflow was not already going to do. A
				// LambdaSelectJob reads the CURRENT page to choose a recommendation,
				// so recycling before it hands it a freshly-loaded copy of the same
				// URL rather than destroying a page a later job still needs.
				boolean jobNavigates = (job instanceof VisitJob) || (job instanceof LambdaSelectJob);
				if (rendererRecycleAfterNavigations > 0 && jobNavigates
						&& navigationsThisRenderer >= rendererRecycleAfterNavigations) {
					if (RendererRecycler.recycle(this.context.getDriver(), job.getId())) {
						navigationsThisRenderer = 0;
						lastSeenUrl = null;
					}
				}

				boolean runCurrentJob = previousResult;

				// fallback from failure
				if (job.resetPreviousFailure()) {
					runCurrentJob = true;
				}

				if (runCurrentJob) {
					lastJobState = null;
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

					// Count what actually happened, so the queue's own status can
					// reflect it. JobDiagnostics still holds this job's warnings at
					// this point only if the handler attached any.
					if (!currentResult) {
						outcome.countFailed();
					} else if (lastJobState == JobState.WARNING || lastJobState == JobState.WARNING_JOBDATA) {
						outcome.countWarned();
					} else {
						outcome.countSucceeded();
					}

					// Count a navigation only when the URL really changed; a wait or a
					// scroll leaves the renderer where it was and leaks nothing extra.
					if (rendererRecycleAfterNavigations > 0 && RendererRecycler.onRecyclablePage(this.context.getDriver())) {
						try {
							String url = this.context.getDriver().getCurrentUrl();
							if (url != null && !url.equals(lastSeenUrl)) {
								if (lastSeenUrl != null) {
									navigationsThisRenderer++;
								}
								lastSeenUrl = url;
							}
						} catch (Exception e) {
							logger.debug("could not read URL after job {} for navigation counting", job.getId());
						}
					}

					previousJob = job;
					previousResult = currentResult;
				} else {
					logger.info("skipping job ({}) due to error in previous job ({})", job.getId(),
							previousJob == null ? -1 : previousJob.getId());
					reportJobState(job, JobState.SKIPPED,
							previousJob == null ? null : "previous job " + previousJob.getId() + " did not succeed",
							null, null, ZonedDateTime.now(), ZonedDateTime.now());
					outcome.countSkipped();
				}
			}

		} catch (

		Exception ex) {
			logger.warn("Error running joblist {}", jobs.getId(), ex);
		}

		return outcome;
	}

	/**
	 * Why the remaining jobs of a session must not start, or null if they may.
	 *
	 * Package-private and static: a pure decision, asserted without a browser.
	 */
	static String sessionSkipReason(boolean aborted, boolean interrupted, boolean browserLost) {
		if (aborted || interrupted) {
			return "session cut off after the processing timeout";
		}
		if (browserLost) {
			return "browser unresponsive; session ended";
		}
		return null;
	}

	/**
	 * Why this agent cannot be started on this runner, or null if it can.
	 *
	 * Package-private and static: the decision is pure, so it is asserted
	 * directly rather than through a browser startup.
	 */
	static String startupRefusalReason(AgentType type, boolean multiloginEnabled) {
		if (type == AgentType.MULTILOGIN && !multiloginEnabled) {
			return MULTILOGIN_DISABLED_MESSAGE;
		}

		return null;
	}

	@Override
	public Boolean prepareExeuction(Crawler crawler) {

		// Checked before the retry loop: no amount of retrying creates beans that
		// a condition removed, and three rounds of backoff would only delay the
		// same answer.
		String refusal = startupRefusalReason(crawler.getAgent().getType(), multiloginEnabled);
		if (refusal != null) {
			logger.error("cannot start crawler {} (agent {}): {}", crawler.getId(), crawler.getAgent().getId(),
					refusal);
			return false;
		}

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
				// capabilities.setCapability(CapabilityType., true);
				capabilities.setAcceptInsecureCerts(true);
				// capabilities.setCapability("webSocketUrl", true);

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

				PageLoadGuard.apply(context.getDriver(), pageLoadTimeoutSeconds);

				// Inject H.264 codec override via CDP so it runs before any page script.
				// This forces YouTube to use H.264 instead of VP9/AV1, which require
				// software decoding on headless VMs and saturate the CPU.
				injectCodecOverrideViaCdp(context.getDriver());

				logger.debug("finished starting up crawler {}, success: {}", crawler.getId(), true);

				isSetup.set(true);
				done = true;
			} catch (Exception ex) {
				logger.error("error during setup for crawler {}", crawler.getId(), ex);

				// Wait before going round again. This loop used to retry with no
				// delay at all, which made the retries worthless against the one
				// failure that actually costs bots: Multilogin's 50 operations
				// per minute. On experiment 147 all three rounds for one crawler
				// ran inside eleven seconds, so all three drew the same 429 and
				// the bot was lost with its 284 jobs. A rate limit is a clock --
				// only time clears it.
				if (curRetry < maxRetryCountInit) {
					Duration wait = StartupBackoff.afterFailure(ex,
							Duration.ofSeconds(startupRetryDelaySeconds),
							Duration.ofSeconds(rateLimitBackoffSeconds));
					logger.info("waiting {}s before startup round {} for crawler {}{}", wait.toSeconds(),
							curRetry + 1, crawler.getId(),
							StartupBackoff.isRateLimited(ex) ? " (rate limited)" : "");
					try {
						Thread.sleep(wait.toMillis());
					} catch (InterruptedException ie) {
						Thread.currentThread().interrupt();
						break;
					}
				}
			}
		}

		// Verify result and return
		return done && context.isValid();
	}

	/**
	 * Injects H.264 codec override via CDP's Page.addScriptToEvaluateOnNewDocument.
	 * This runs the override script before any page JavaScript on every navigation,
	 * ensuring YouTube never initializes VP9/AV1 codecs.
	 */
	private void injectCodecOverrideViaCdp(org.openqa.selenium.WebDriver driver) {
		String script =
			"(function() {" +
			"  if (window.__h264OverrideApplied) return;" +
			"  var origIsTypeSupported = MediaSource.isTypeSupported.bind(MediaSource);" +
			"  MediaSource.isTypeSupported = function(mimeType) {" +
			"    if (mimeType && (mimeType.indexOf('vp9') !== -1 || mimeType.indexOf('vp09') !== -1 || mimeType.indexOf('av01') !== -1)) {" +
			"      return false;" +
			"    }" +
			"    return origIsTypeSupported(mimeType);" +
			"  };" +
			"  MediaSource.isTypeSupported.toString = function() { return 'function isTypeSupported() { [native code] }'; };" +
			"  Object.defineProperty(MediaSource.isTypeSupported, 'name', { value: 'isTypeSupported', configurable: true });" +
			"  window.__h264OverrideApplied = true;" +
			"})();";

		try {
			org.openqa.selenium.WebDriver augmented = new Augmenter().augment(driver);
			if (augmented instanceof HasCdp) {
				((HasCdp) augmented).executeCdpCommand(
					"Page.addScriptToEvaluateOnNewDocument",
					java.util.Map.of("source", script)
				);
				logger.info("H.264 codec override registered via CDP (runs before every page load)");
			} else {
				logger.warn("Driver does not support CDP, H.264 override will rely on VisitJobHandler injection");
			}
		} catch (Exception e) {
			logger.warn("Failed to register H.264 codec override via CDP: {}. " +
				"Falling back to VisitJobHandler injection.", e.getMessage());
		}
	}

	/**
	 * Releases the browser, the profile and the recorder.
	 *
	 * <p>
	 * Safe to call from two threads. The cut-off path ({@code immediate}) runs on a
	 * different thread from the worker's own teardown, so the context is claimed
	 * under a lock and the second caller finds nothing to do.
	 *
	 * @param immediate the joblist was cut off: mark it aborted, kill the profile
	 *                  first so a call the worker is blocked in fails at once, and
	 *                  skip the 25 s wait that lets a finished profile upload
	 */
	@Override
	public void tearDown(boolean immediate) {
		if (immediate) {
			aborted = true;
		}

		ContextBase<?> ctx;
		synchronized (this) {
			ctx = context;
			if (ctx == null) {
				return; // nothing to do, or the other thread is already on it
			}
			context = null;
		}

		logger.info("Tearing down crawler {}{}", ctx.getCrawler().getId(), immediate ? " (immediate)" : "");

		isSetup.set(false);
		ipService = null;

		if (immediate) {
			stopBrowserProfile(ctx);
		}

		// close webdriver
		try {
			ctx.getDriver().quit();
		} catch (Exception e) {
			logger.warn("Exception quitting driver for crawler {}", ctx.getCrawler().getId());
		}

		// Multilogin needs a wait to finish of profile tear down and storage. Upload
		// will run through proxy!
		if (!immediate) {
			stopBrowserProfile(ctx);
		}

		// quit the Webdriver (fallback in case of MultiLogin)
		try {
			ctx.getDriver().quit();
		} catch (Exception e) {
			logger.error("Error closing webdriver for agent {}", ctx.getCrawler().getAgent().getId(), e);
		}

		try {
			ctx.getRecordingProxy().tearDown();
		} catch (Exception e) {
			logger.warn("Exception quitting recorder for crawler {}", ctx.getCrawler().getId());
		}
	}

	/**
	 * Stops the Multilogin profile behind a context, if it has one. The graceful
	 * form waits for the profile to finish uploading; the immediate form (a cut-off
	 * joblist) stops it once and moves on.
	 */
	private void stopBrowserProfile(ContextBase<?> ctx) {
		if (!(ctx instanceof MultiloginContext)) {
			return;
		}

		MultiloginContext typedContext = (MultiloginContext) ctx;

		if (typedContext.getProfile() == null) {
			contextBuilders.get("MlxContextBuilder").destroyContext(typedContext);
			return;
		}

		MulitLoginBrowserProfile bp = typedContext.getProfile();

		try {
			if (aborted) {
				bp.stopProfile();
				return;
			}

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
