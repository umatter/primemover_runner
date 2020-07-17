package primemover.service;

import java.util.Collections;
import java.util.concurrent.atomic.AtomicBoolean;

import org.openqa.selenium.WebDriver;
import org.openqa.selenium.chrome.ChromeDriver;
import org.openqa.selenium.chrome.ChromeOptions;
import org.openqa.selenium.firefox.FirefoxDriver;
import org.openqa.selenium.firefox.FirefoxOptions;
import org.openqa.selenium.remote.CapabilityType;
import org.openqa.selenium.remote.DesiredCapabilities;
import org.springframework.beans.factory.annotation.Autowired;

import net.lightbody.bmp.core.har.Har;
import primemover.capture.RecordingProxy;
import primemover.model.Crawler;
import primemover.model.IJob;
import primemover.model.Joblist;

public class MockTaskService implements ITaskService {
	
	@Autowired
	IStorageService storage;
	
	private AtomicBoolean isSetup = new AtomicBoolean(false);
	private RecordingProxy recorder;
	private WebDriver driver;
	private Crawler crawler;

	
	private Boolean runJob(IJob job) {
		// init data recording
		JobData data = new JobData();
		recorder.startCapturing(job.getReference(), job.getTitle());
		
		// running job
		RunJobHandler.handle(driver, job);
		
		// capture trafic data
		Har har = recorder.stopCapturing();
		data.setHar(har);
		
		// capture page data
		data.setPageSource(selenium.util.HTML.getBodyeFromJavascript(driver));
		
		// capture cookies, localstorage, etc.
		data.setCookies(driver.manage().getCookies());
		data.setLocalStorage(selenium.util.LocalStorage.getItems(driver));
		data.setSessionStorage(selenium.util.SessionStorage.getItems(driver));
		
		
		// Writing data
		storage.writeJobData(job, data);
		
		
		return true;
	}
	
	@Override
	public Boolean runJobList(Joblist jobs) {
		
		if (!isSetup.get()) {
			throw new IllegalStateException("please setup service first");
		}
		
		for (IJob jobItem : jobs.getJobList()) {
			runJob(jobItem);			
		}
		
		return true;
	}

	@Override
	public Boolean setup(Crawler crawler) {
		// remember the crawler
		this.crawler = crawler;
		
		// Default capabilities for ssl handling
		DesiredCapabilities capabilities = new DesiredCapabilities();
		
		// siehe https://www.guru99.com/ssl-certificate-error-handling-selenium.html
		capabilities.setCapability (CapabilityType.ACCEPT_SSL_CERTS, true);
		capabilities.setCapability(CapabilityType.ACCEPT_INSECURE_CERTS, true);
		
		// create a recording proxy
		recorder = new RecordingProxy();
		recorder.setup();
		recorder.applyProxy(capabilities);

		// create driver		
		switch (crawler.getAgent().getType()) {
		case CHROME:
			ChromeOptions chromeOptions = new ChromeOptions();
			chromeOptions.merge(capabilities);
			chromeOptions.addArguments("--start-maximized");
			
			// make navigator.webriver disappear
			// see https://stackoverflow.com/questions/53039551/selenium-webdriver-modifying-navigator-webdriver-flag-to-prevent-selenium-detec
			chromeOptions.addArguments("--disable-blink-features");
			chromeOptions.addArguments("--disable-blink-features=AutomationControlled");
			
			driver = new ChromeDriver(chromeOptions);
			
			break;
		case FIREFOX:
			FirefoxOptions firefoxOptions = new FirefoxOptions();
			firefoxOptions.merge(capabilities);
			driver = new FirefoxDriver(firefoxOptions);
			
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
		driver.quit();
		driver = null;
		recorder.tearDown();
		recorder = null;
		crawler = null;;
	}

}
