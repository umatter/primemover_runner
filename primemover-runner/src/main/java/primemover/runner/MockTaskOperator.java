package primemover.runner;

import java.net.MalformedURLException;
import java.net.URL;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicBoolean;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.openqa.selenium.OutputType;
import org.openqa.selenium.TakesScreenshot;
import org.openqa.selenium.chrome.ChromeDriver;
import org.openqa.selenium.chrome.ChromeOptions;
import org.openqa.selenium.firefox.FirefoxDriver;
import org.openqa.selenium.firefox.FirefoxOptions;
import org.openqa.selenium.remote.CapabilityType;
import org.openqa.selenium.remote.DesiredCapabilities;
import org.openqa.selenium.remote.RemoteWebDriver;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;

import net.lightbody.bmp.core.har.Har;
import primemover.capture.RecordingProxy;
import primemover.model.Crawler;
import primemover.model.IJob;
import primemover.model.IProxy;
import primemover.model.Joblist;
import primemover.model.MultiLoginAgent;
import primemover.service.IStorageService;
import primemover.service.JobData;
import primemover.util.MulitLoginBrowserProfile;

public class MockTaskOperator implements ITaskOperator {
	private static Logger logger = LogManager.getLogger(MockTaskOperator.class);
	
	@Value("${multilogin.StartupRetryCount}")
	private Integer maxRetryCount;
	
	private OperatorContext context;
	
	@Autowired(required=false)
	List<IStorageService> storages = new ArrayList<IStorageService>();
	
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
		MulitLoginBrowserProfile bp = new MulitLoginBrowserProfile(typedAgent.getMultiLoginId());
		
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
				logger.warn("catched exception starting profile", e);				
			} catch (Exception e) {
				logger.warn("catched exception starting profile", e);
			}
		}
		
		if (!done) {
			crawler = null;
			logger.error("Could not start MulitLogin-Profile");
			throw new IllegalStateException("Could not start MulitLogin-Profile");
		}
	}
	
	private Boolean runJob(IJob job) {
		// init data recording
		JobData data = new JobData();
		this.context.getRecorder().startCapturing(job.getDescription(), job.getName());
		
		// running job
		RunJobHandler.handle(context.getDriver(), job);
		
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
		
		return true;
	}
	
	public RecordingProxy setupRecorder(Crawler crawler) {
		// create a recording proxy. Check also for any additional proxy
		RecordingProxy recorder = new RecordingProxy();
		
		IProxy crawlerProxy = crawler.getProxy();
		if (crawlerProxy != null) {
			recorder.setup(crawlerProxy);
		} else {
			recorder.setup();
		}
		
		return recorder;
	}
	
	@Override
	public Boolean runJobList(Joblist jobs) {
		if (!isSetup.get()) {
			throw new IllegalStateException("please setup service first");
		}
		
		for (IJob jobItem : jobs.getJobs()) {
			runJob(jobItem);			
		}
		
		return true;
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
			throw new UnsupportedOperationException("This type is not yet supported");
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
