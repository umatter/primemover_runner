package primemover.runner;

import java.net.MalformedURLException;
import java.net.URL;
import java.time.ZonedDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
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

import net.lightbody.bmp.core.har.Har;
import primemover.capture.RecordingProxy;
import primemover.capture.RecordingProxyFactory;
import primemover.model.Crawler;
import primemover.model.GeoSurfProxy;
import primemover.model.IJob;
import primemover.model.IProxy;
import primemover.model.JobState;
import primemover.model.Joblist;
import primemover.model.MultiLoginAgent;
import primemover.service.AgentService;
import primemover.service.IStorageService;
import primemover.service.JobData;
import primemover.service.JobService;
import primemover.util.MulitLoginBrowserProfile;

public class TaskOperator implements ITaskOperator {
	private static Logger logger = LoggerFactory.getLogger(TaskOperator.class);
	
	@Value("${multilogin.StartupRetryCount:3}")
	private Integer maxRetryCount;
	@Value("${multilogin.StartupDelaySeconds:30}")
	private Integer retryDelay;
	
	private OperatorContext context;
	
	@Autowired
	private JobService jobService;
	
	@Autowired
	private AgentService agentService;
	
	@Autowired(required=false)
	List<IStorageService> storages = new ArrayList<IStorageService>();
	
	@Autowired
	private RecordingProxyFactory recorderFactory;
	
	private AtomicBoolean isSetup = new AtomicBoolean(false);
	
	private void startupFirefox(Crawler crawler, DesiredCapabilities capabilities, RecordingProxy recorder) {
		FirefoxContext ffContext = new FirefoxContext();
		ffContext.setCrawler(crawler);
		ffContext.setRecorder(recorder);
				
		FirefoxOptions firefoxOptions = new FirefoxOptions();
		firefoxOptions.merge(capabilities);
		firefoxOptions.setAcceptInsecureCerts(true);
		ffContext.setFirefoxOptions(firefoxOptions);
		
		ffContext.setDriver(new FirefoxDriver(firefoxOptions));
		
		// remember
		this.context = ffContext;
	}
	
	private void startupChrome(Crawler crawler, DesiredCapabilities capabilities, RecordingProxy recorder) {
		ChromeContext chContext = new ChromeContext();
		chContext.setCrawler(crawler);
		chContext.setRecorder(recorder);
		
		ChromeOptions chromeOptions = new ChromeOptions();
		chromeOptions.merge(capabilities);
		chromeOptions.addArguments("--start-maximized");
		// make navigator.webriver disappear
		// see https://stackoverflow.com/questions/53039551/selenium-webdriver-modifying-navigator-webdriver-flag-to-prevent-selenium-detec
		chromeOptions.addArguments("--disable-blink-features");
		chromeOptions.addArguments("--disable-blink-features=AutomationControlled");
		
		chContext.setChromeOptions(chromeOptions);
		
		chContext.setDriver(new ChromeDriver(chromeOptions));
		
		// remember
		this.context = chContext;
	}
	
	private void startupMultilogin(Crawler crawler, DesiredCapabilities capabilities, RecordingProxy recorder) {
		MultiloginContext mlContext = new MultiloginContext();
		mlContext.setCrawler(crawler);
		mlContext.setRecorder(recorder);
		
		Boolean done = false;
		Integer retryCount = 0;
		
		MultiLoginAgent typedAgent = (MultiLoginAgent) crawler.getAgent();
		
		MulitLoginBrowserProfile bp;
		
		if (StringUtils.isEmpty(typedAgent.getMultiLoginId())) {
			try {
				bp = new MulitLoginBrowserProfile(crawler, recorder);
				agentService.updateMultiloginProfile(typedAgent, bp.getProfileId());
			} catch (Exception ex) {
				logger.warn("could not create MultiLoginProfile for agent {}", typedAgent.getId(), ex);
				
				throw new IllegalStateException("could not create MultiLoginProfile", ex);
			}
		} else {
			bp = new MulitLoginBrowserProfile(typedAgent.getMultiLoginId());
		}		
		
		while (!done && retryCount < maxRetryCount) {
			retryCount += 1;
			if (retryCount > 1) {
				logger.info("retry starting profile");
			}
			
			try {
				bp.updateProxy(recorder);
				RemoteWebDriver driver = new RemoteWebDriver(new URL(bp.startProfile()), capabilities);
				done = true;
				
				
				mlContext.setProfile(bp);
				mlContext.setDriver(driver);
				
				// remember
				this.context = mlContext;	

			} catch (MalformedURLException e) {
				logger.error("catched exception starting profile", e);				
			} catch (Exception e) {
				logger.error("catched exception starting profile", e);
			}
			
			if (!done) {
				try {
					Thread.sleep(this.retryDelay*1000);
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
	}
	
	private Boolean runJob(IJob job) {
		logger.info("Starting job {}", job.getId());
		
		try {
			jobService.updateJobState(job, JobState.RUNNING, ZonedDateTime.now(), null); // setting starttime
			
			// init data recording
			JobData data = new JobData();
			this.context.getRecorder().startCapturing(job.getDescription(), job.getName());
			
			// running job
			RunJobHandler.handle(context.getDriver(), job);
			
			jobService.updateJobState(job, JobState.STORINGDATA, null, null);
			
			// capture screenshot
			if (context.getDriver() instanceof TakesScreenshot) {
				
				TakesScreenshot camera = (TakesScreenshot) context.getDriver();
				byte[] screenshot = camera.getScreenshotAs(OutputType.BYTES);
				
				data.setScreenshot(screenshot);	
			}
			
			// capture trafic data
			Har har = context.getRecorder().stopCapturing();
			data.setHar(har);
			
			// capture page data
			data.setPageSource(primemover.util.Page.getBodyeFromJavascript(context.getDriver()));
			
			// capture cookies, localstorage, etc.
			data.setCookies(context.getDriver().manage().getCookies());
			data.setLocalStorage(primemover.util.LocalStorage.getItems(context.getDriver()));
			data.setSessionStorage(primemover.util.SessionStorage.getItems(context.getDriver()));
			
			
			// Writing data
			for (IStorageService storage : storages) {
				storage.writeJobData(job, data);
			}		
			
			logger.info("Done running job {}", job.getId());
			jobService.updateJobState(job, JobState.SUCCESS, null, ZonedDateTime.now()); // setting endtime
			
			
			return true;
			
		} catch (Exception ex) {
			logger.error("Exception executing job {}", job.getId(), ex);
			
			jobService.updateJobState(job, JobState.FAILURE, null, ZonedDateTime.now()); // setting endtime
			
			return false;
		}
		
	}
	
	public RecordingProxy setupRecorder(Crawler crawler) {		
		// create a recording proxy. Check also for any additional proxy
		RecordingProxy recorder = recorderFactory.createRecordingProxy();
		
		IProxy crawlerProxy = crawler.getProxy();
		
		if (crawlerProxy != null) {
			
			// setting GeoSurf-Session idendifier
			if (crawler.getProxy() instanceof GeoSurfProxy) {
				GeoSurfProxy geoProxy = (GeoSurfProxy) crawler.getProxy();
				
				geoProxy.setSessionId("primemover-" + UUID.randomUUID().toString());
				logger.debug("GeoSurf-Session identifier for crawler {} is {}", crawler.getId(), geoProxy.getSessionId());
			}
			
			recorder.setup(crawlerProxy);
		} else {
			recorder.setup();
		}
		
		return recorder;
	}
	
	@Override
	public Boolean runJobList(Joblist jobs) {
		Boolean res = false;
		if (!isSetup.get()) {
			throw new IllegalStateException("please setup service first");
		}
		
		try {
			Boolean previousResult = true;
			IJob previousJob = null;
			for (IJob jobItem : jobs.getJobs()) {
				if (previousResult) {
					runJob(jobItem);
				} else {
					logger.warn("skipping job ({}) due to error in previous job ({})", jobItem.getId(), previousJob == null ? -1 : previousJob.getId() );
					jobService.updateJobState(jobItem, JobState.SKIPPED, ZonedDateTime.now(), null);
				}		
				previousJob = jobItem;
			}
			
			res = true;
		} catch (Exception ex) {
			logger.warn("Error running joblist {}", jobs.getId(), ex);
		}
		
		return res;
	}

	@Override
	public Boolean setup(Crawler crawler) {
		// Default capabilities for ssl handling
		DesiredCapabilities capabilities = new DesiredCapabilities();
		
		// siehe https://www.guru99.com/ssl-certificate-error-handling-selenium.html
		capabilities.setCapability(CapabilityType.ACCEPT_SSL_CERTS, true);
		capabilities.setCapability(CapabilityType.ACCEPT_INSECURE_CERTS, true);
				
		// start recorder
		RecordingProxy recorder = setupRecorder(crawler);
		recorder.applyProxy(capabilities);

		// create driver
		try {
			switch (crawler.getAgent().getType()) {
			case CHROME:
				startupChrome(crawler, capabilities, recorder);
				
				break;
			case FIREFOX:
				startupFirefox(crawler, capabilities, recorder);
				
				break;
			case MULTILOGIN:
				startupMultilogin(crawler, capabilities, recorder);
				break;
			
			default:
				logger.error("unsupported type {} for agent {}", crawler.getAgent().getType(), crawler.getAgent().getId());
			}
		} catch (Exception ex) {
			logger.error("error during setup for crawler {}", crawler.getId(), ex);
			return false;
		}
		
		isSetup.set(true);
		return true;
	}

	@Override
	public void tearDown() {
		isSetup.set(false);
		
		context.getDriver().quit();
		context.getRecorder().tearDown();
		context = null;
	}

}
