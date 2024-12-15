package primemover.anticaptcha;

import java.time.Duration;
import java.util.Arrays;
import java.util.NoSuchElementException;
import java.util.concurrent.TimeUnit;

import org.apache.commons.lang3.time.StopWatch;
import org.json.JSONObject;
import org.openqa.selenium.By;
import org.openqa.selenium.JavascriptExecutor;
import org.openqa.selenium.MutableCapabilities;
import org.openqa.selenium.WebDriver;
import org.openqa.selenium.WebElement;
import org.openqa.selenium.chrome.ChromeOptions;
import org.openqa.selenium.firefox.FirefoxOptions;
import org.openqa.selenium.firefox.FirefoxProfile;
import org.openqa.selenium.support.ui.WebDriverWait;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;

import primemover.capture.RecordingProxy;
import primemover.model.Crawler;
import primemover.model.HtmlSelectorType;
import primemover.model.IJob;
import primemover.model.MultiLoginAgent;
import primemover.multilogin.MulitLoginBrowserProfile;
import primemover.multilogin.MultiLoginBrowserProfileFactory;
import primemover.runner.context.IRunnerContext;
import primemover.util.NavigationHelper;
import primemover.util.SelectingHelper;

public class PluginCaptchaSolver implements ICaptchaSolver {
	private static final Logger LOGGER = LoggerFactory.getLogger(PluginCaptchaSolver.class);

	@Autowired
	MultiLoginBrowserProfileFactory mlProfileFactory;

	@Value("${anticaptcha.extension.firefox}")
	private String firefoxExtensionPath;
	@Value("${anticaptcha.extension.chrome}")
	private String chromeExtensionPath;
	@Value("${anticaptcha.api.key}")
	private String apiKey;
	@Value("${anticaptcha.api.blankPage}")
	private String emptyPageUrl;
	private int maxSolvingSeconds = 120;

	private boolean pluginAvailable = false;
	private boolean pluginInitialised = false;

	private static final String MESSAGE_RECEIVER = "antiCaptchaPlugin";
	private static final String SET_OPTIONS_MESSAGE = "setOptions";

	@Override
	public boolean setOptions(Crawler crawler, MutableCapabilities options, RecordingProxy recorder) {

		// Multilogin
		if (crawler.getAgent() instanceof MultiLoginAgent) {
			LOGGER.debug("Checking for Multilogin-Plugin");

			// FIXME check for already installed plugin, fixed extension settings and used
			// browser (currently fixed to mimic) with newer Multilogin-API

			// Update to current plugin
			MulitLoginBrowserProfile mlProfile = mlProfileFactory.getItem(crawler, recorder);
			String multiloginExtension = chromeExtensionPath.substring(chromeExtensionPath.lastIndexOf("/") + 1);
			pluginAvailable = mlProfile.updateExtensions(Arrays.asList(multiloginExtension));

			LOGGER.debug("Multilogin-Plugin available: {}", this.pluginAvailable);
			return pluginAvailable;
		}

		// Firefox
		if (options instanceof FirefoxOptions) {
			LOGGER.debug("Setting options for Firefox");
			FirefoxOptions ffOptions = (FirefoxOptions) options;

			FirefoxProfile profile = new FirefoxProfile();
			profile.addExtension(new java.io.File(firefoxExtensionPath));
			ffOptions.setProfile(profile);

			this.pluginAvailable = true;
			return true;
		}

		// Chrome
		if (options instanceof ChromeOptions) {
			LOGGER.debug("Setting options for Chrome");
			ChromeOptions chOptions = (ChromeOptions) options;
			chOptions.addExtensions(new java.io.File(chromeExtensionPath));

			this.pluginAvailable = true;
			return true;
		}

		// not implemented or not possible
		LOGGER.warn("Captcha solving not possible");
		return false;
	}

	/**
	 * Send a message to the plugin. Works only on a page containing HTML-Code, not
	 * in a blank page or about page
	 * 
	 */
	private void sendApiRequest(WebDriver driver, String messageTyp, JSONObject data) {

		JSONObject messageObj = new JSONObject();
		messageObj.put("receiver", MESSAGE_RECEIVER);
		messageObj.put("type", messageTyp);
		messageObj.put("options", data);
		String messageStr = messageObj.toString();

		LOGGER.debug("sending message of type {} to plugin data:\n{}", messageTyp, messageStr);

		JavascriptExecutor jse = (JavascriptExecutor) driver;

		StringBuilder script = new StringBuilder();
		script.append("return window.postMessage(");
		script.append(messageStr);
		script.append(");");

		jse.executeScript(script.toString());
	}

	@Override
	public boolean setupDriver(IRunnerContext<?> context) {
		LOGGER.debug("initialising plugin in open browser for crawler {}", context.getCrawler().getId());
		if (!this.pluginAvailable) {
			return true; // Plugin not available
		}

		try {
			// load empty page
			NavigationHelper navHelper = new NavigationHelper(context.getDriver(), 1000);
			context.getDriver().get(emptyPageUrl);
			navHelper.waitForPageLoad();

			// wait for Plugin initial load
			try {
				Thread.sleep(1000);
			} catch (InterruptedException e) {
			}

			// set options
			JSONObject data = new JSONObject();
			data.put("antiCaptchaApiKey", apiKey);

			sendApiRequest(context.getDriver(), SET_OPTIONS_MESSAGE, data);

			// wait for Plugin to accept key
			try {
				Thread.sleep(3000);
			} catch (InterruptedException e) {
			}

			this.pluginInitialised = true;

			return true;
		} catch (Exception e) {
			LOGGER.warn("failed to setup the anticaptcha plugin", e);
		}

		// not implemented or failed
		return false;
	}

	@Override
	public CaptchaSolverResult solve(IRunnerContext<?> context, IJob job, Boolean submit) {
		if (!this.pluginInitialised) {
			LOGGER.debug("Plugin not available for job {}", job.getId());
			return CaptchaSolverResult._PLUGINFAILURE; // Plugin not working
		}

		// check to see if the plugin is working on a captcha, allow 3 seconds for setup
		try {
			new WebDriverWait(context.getDriver(), Duration.ofSeconds(3))
					.until(driver -> driver.findElement(By.cssSelector(".antigate_solver")));
		} catch (Exception e) {
			// ignore -> plugin inactive
			LOGGER.debug("no captcha found");
			return CaptchaSolverResult._NOCAPTCHADETECTED;
		}

		// Most important part: we wait upto 120 seconds until the AntiCaptcha plugin
		// indicator with antigate_solver class
		// gets the solved class, which means that the captcha was successfully solved
		LOGGER.debug("waiting for captcha being solved for {} seconds", maxSolvingSeconds);

		StopWatch timing = new StopWatch();
		timing.start();
		try {
			new WebDriverWait(context.getDriver(), Duration.ofSeconds(maxSolvingSeconds))
					.until(driver -> driver.findElement(By.cssSelector(".antigate_solver.solved")));
		} catch (NoSuchElementException ex) {
			LOGGER.warn("Captcha not solved within {} seconds", maxSolvingSeconds);

			return CaptchaSolverResult.of(true, true, false, maxSolvingSeconds, true, true, null);
		}
		timing.stop();

		// special treatment of Amazon -> needs Button click
		try {
			WebElement button = SelectingHelper.getElementByWebdriver(context.getDriver(), HtmlSelectorType.TEXT,
					"Continue shopping");
			if (button != null) {
				// click and wait for page refresh
				LOGGER.debug("Clicking Amazon continue");
				button.click();
				Thread.sleep(5000);
			}
		} catch (NoSuchElementException ex) {
			// no Amazon button
		} catch (InterruptedException e) {
			// nothing to do
		}

		return CaptchaSolverResult.of(true, true, false, timing.getTime(TimeUnit.SECONDS), false, false, null);

	}
}
