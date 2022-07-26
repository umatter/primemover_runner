package primemover.anticaptcha;

import java.util.Arrays;
import java.util.NoSuchElementException;

import org.json.JSONObject;
import org.openqa.selenium.By;
import org.openqa.selenium.JavascriptExecutor;
import org.openqa.selenium.MutableCapabilities;
import org.openqa.selenium.WebDriver;
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
import primemover.model.IJob;
import primemover.model.MultiLoginAgent;
import primemover.multilogin.MulitLoginBrowserProfile;
import primemover.multilogin.MultiLoginBrowserProfileFactory;
import primemover.runner.context.ContextBase;
import primemover.util.NavigationHelper;

public class PluginCaptchaSolver implements ICaptchaSolver {
	private static final Logger LOGGER = LoggerFactory.getLogger(CaptchaSolver.class);

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
		// Multilogin -> nothing to do
		if (crawler.getAgent() instanceof MultiLoginAgent) {
			LOGGER.debug("Checking for Multilogin-Plugin");
			MultiLoginAgent agent = (MultiLoginAgent) crawler.getAgent();

			// FIXME check for already installed plugin, fixed extension settings and used
			// browser (currently fixed to mimic) with newer Multilogin-API

			// Update to current plugin
			MulitLoginBrowserProfile mlProfile = mlProfileFactory.getItem(crawler, recorder);
			boolean pluginUpdated = mlProfile.updateExtensions(Arrays.asList(chromeExtensionPath));

			LOGGER.debug("Multilogin-Plugin available: {}", this.pluginAvailable);
			return pluginUpdated;
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
	public boolean setupDriver(ContextBase<?> context) {
		LOGGER.debug("initialising plugin in open browser for crawler {}", context.getCrawler().getId());
		if (!this.pluginAvailable) {
			return true; // Plugin not available
		}

		try {
			// load empty page
			NavigationHelper navHelper = new NavigationHelper(context.getDriver(), 10000);
			context.getDriver().get(emptyPageUrl);
			navHelper.waitForPageLoad();

			// wait for Plugin initial load
			try {
				Thread.sleep(1500);
			} catch (InterruptedException e) {
			}

			// set options
			JSONObject data = new JSONObject();
			data.put("antiCaptchaApiKey", apiKey);

			sendApiRequest(context.getDriver(), SET_OPTIONS_MESSAGE, data);

			this.pluginInitialised = true;

			return true;
		} catch (Exception e) {
			LOGGER.warn("failed to setup the anticaptcha plugin", e);
		}

		// not implemented or failed
		return false;
	}

	@Override
	public CaptchaSolverResult solve(ContextBase<?> context, IJob job, Boolean submit) {
		if (!this.pluginInitialised) {
			LOGGER.debug("Plugin not available for job {}", job.getId());
			return CaptchaSolverResult.ERROR; // Plugin not working
		}

		// check to see if the plugin is working, allow 5 seconds for setup
		try {
			new WebDriverWait(context.getDriver(), 5)
					.until(driver -> driver.findElement(By.cssSelector(".antigate_solver")));
		} catch (Exception e) {
			// ignore -> plugin inactive
			LOGGER.debug("no captcha found");
			return CaptchaSolverResult.NOTFOUND;
		}

		// Most important part: we wait upto 120 seconds until the AntiCaptcha plugin
		// indicator with antigate_solver class
		// gets the solved class, which means that the captcha was successfully solved
		LOGGER.debug("waiting for captcha being solved for {} seconds", maxSolvingSeconds);

		try

		{
			new WebDriverWait(context.getDriver(), maxSolvingSeconds)
					.until(driver -> driver.findElement(By.cssSelector(".antigate_solver.solved")));

		} catch (NoSuchElementException ex) {
			LOGGER.warn("Captcha not solved within {} seconds", maxSolvingSeconds);

			return CaptchaSolverResult.FAILURE;
		}

		return CaptchaSolverResult.SUCCESS;
	}
}
