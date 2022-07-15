package primemover.anticaptcha;

import java.io.File;
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

import primemover.capture.RecordingProxy;
import primemover.model.Crawler;
import primemover.model.IJob;
import primemover.model.MultiLoginAgent;
import primemover.runner.context.ChromeContext;
import primemover.runner.context.ContextBase;
import primemover.runner.context.FirefoxContext;
import primemover.util.NavigationHelper;

public class PluginCaptchaSolver implements ICaptchaSolver {
	private static final Logger LOGGER = LoggerFactory.getLogger(CaptchaSolver.class);

	private String firefoxExtensionPath = "anticaptcha/anticaptcha-plugin_v0.60.xpi";
	private String chromeExtensionPath = "anticaptcha/anticaptcha-plugin_v0.62.crx";
	private String apiKey = "00000000000000000000000000000000";
	private String emptyPageUrl = "https://antcpt.com/blank.html";
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
			// check for Plugin
			MultiLoginAgent agent = (MultiLoginAgent) crawler.getAgent();

			// FIXME: this is only a hack!
			this.pluginAvailable = agent.getMultiLoginCreationJson().contains("anticaptcha-plugin");

			LOGGER.debug("Multilogin-Plugin available: {}", this.pluginAvailable);
			return pluginAvailable;
		}

		// Firefox
		if (options instanceof FirefoxOptions) {
			LOGGER.debug("Setting options for Firefox");
			FirefoxOptions ffOptions = (FirefoxOptions) options;

			FirefoxProfile profile = new FirefoxProfile();
			profile.addExtension(new File(firefoxExtensionPath));
			ffOptions.setProfile(profile);

			this.pluginAvailable = true;
			return true;
		}

		// Chrome
		if (options instanceof ChromeOptions) {
			LOGGER.debug("Setting options for Chrome");
			ChromeOptions chOptions = (ChromeOptions) options;
			chOptions.addExtensions(new File(chromeExtensionPath));

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

		if (!this.pluginAvailable) {
			return true; // Plugin not available
		}

		if (context instanceof FirefoxContext || context instanceof ChromeContext) {
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
		}

		// not implemented or not possible
		return false;
	}

	@Override
	public CaptchaSolverResult solve(ContextBase<?> context, IJob job, Boolean submit) {
		if (!this.pluginInitialised) {
			LOGGER.debug("Plugin not available for job {}", job.getId());
			return CaptchaSolverResult.ERROR; // Plugin not working
		}

		// Most important part: we wait upto 120 seconds until the AntiCaptcha plugin
		// indicator with antigate_solver class
		// gets the solved class, which means that the captcha was successfully solved
		LOGGER.debug("waiting for captcha being solved for {} seconds", maxSolvingSeconds);
		try {
			new WebDriverWait(context.getDriver(), maxSolvingSeconds)
					.until(driver -> driver.findElement(By.cssSelector(".antigate_solver.solved")));

		} catch (NoSuchElementException ex) {
			LOGGER.warn("Captcha not solved within {} seconds", maxSolvingSeconds);

			return CaptchaSolverResult.FAILURE;
		}

		return CaptchaSolverResult.SUCCESS;
	}
}
