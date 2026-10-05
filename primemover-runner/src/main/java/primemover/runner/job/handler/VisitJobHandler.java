package primemover.runner.job.handler;

import org.openqa.selenium.JavascriptExecutor;
import org.openqa.selenium.TimeoutException;
import primemover.util.PageLoadGuard;
import org.openqa.selenium.WebDriver;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import primemover.util.consent.ConsentHandler;
import primemover.anticaptcha.CaptchaJobHelper;
import primemover.anticaptcha.CaptchaJobHelper.JobPosition;
import primemover.model.jobs.VisitJob;
import primemover.runner.context.IJobAwareRunnerContext;
import primemover.runner.job.IJobHandler;
import primemover.util.NavigationHelper;

public class VisitJobHandler implements IJobHandler {
	private static Logger logger = LoggerFactory.getLogger(VisitJobHandler.class);

	/**
	 * Forces YouTube to use H.264 instead of VP9/AV1 by overriding MediaSource.isTypeSupported.
	 * VP9/AV1 require software decoding on headless VMs (no hardware GPU), which saturates the CPU
	 * and makes the renderer unresponsive to WebDriver commands.
	 * H.264 is much lighter on CPU for software decoding.
	 * This override persists across SPA navigations (YouTube click-throughs) within the same page context.
	 */
	private static final String H264_CODEC_OVERRIDE_SCRIPT =
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

	@Override
	public void handle(IJobAwareRunnerContext<?> context) {
		boolean success = false;
		WebDriver driver = context.getDriver();
		VisitJob typedJob = (VisitJob) context.getJob();

		logger.debug("running job");

		// captcha before
		CaptchaJobHelper.solveCaptcha(context, JobPosition.BEFORE);

		logger.debug("navigate to {} for job {}", typedJob.getUrl(), typedJob.getId());
		NavigationHelper navHelper = new NavigationHelper(driver, 2 * 60 * 1000l); // 2 Minuten
		// Bounded by runner.PageLoadTimeoutSeconds; on a timeout the load is stopped and
		// the job fails here instead of spending minutes in every wait below.
		PageLoadGuard.navigate(driver, typedJob.getUrl(), typedJob.getId());

		// Wait for DOM-reload
		try {
			navHelper.waitForPageLoad(typedJob.getUrl());
		} catch (Exception e) {
			logger.warn("simple page load wait (DOM) failed for job {}.", typedJob.getId(), e);
			// second chance below
		}

		// wait for pending javascript triggered loads
		try {
			primemover.util.Waiter.waitUntilJSReady(context.getDriver(), 5, 12); // check every 5 seconds for 1 Minutes
			success = true;
		} catch (Exception e) {
			logger.warn("page ready load wait failed for job {}.", typedJob.getId(), e);
		}

		// Answer a cookie / GDPR consent dialog according to this bot's policy
		// (job behaviour > crawler cookie_pref > consent.choice). Before this the
		// only consent handling was inside the scroll jobs, so a workflow of
		// visit -> wait -> lambda-select ran entirely behind the YouTube consent
		// bump on any EU/CH exit IP.
		ConsentHandler.handle(driver);

		// Inject H.264 codec override to prevent VP9/AV1 software decoding
		// which saturates CPU on headless VMs and makes the renderer unresponsive.
		// Injected after page load so it's in place before YouTube's player initializes
		// for subsequent video loads (Lambda Select clicks via SPA navigation).
		try {
			((JavascriptExecutor) driver).executeScript(H264_CODEC_OVERRIDE_SCRIPT);
			logger.debug("H.264 codec override injected for job {}", typedJob.getId());
		} catch (Exception e) {
			logger.warn("Failed to inject H.264 codec override for job {}: {}", typedJob.getId(), e.getMessage());
		}

		// captcha after
		CaptchaJobHelper.solveCaptcha(context, JobPosition.AFTER);

		if (!success) {
			logger.warn("Timeout visiting page {} for job {}", typedJob.getUrl(), typedJob.getId());
			throw new TimeoutException("Timeout visiting page " + typedJob.getUrl() + " for job " + typedJob.getId());
		}

	}
}
