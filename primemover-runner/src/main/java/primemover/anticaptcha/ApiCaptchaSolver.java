package primemover.anticaptcha;

import org.apache.commons.lang3.time.StopWatch;
import org.openqa.selenium.JavascriptExecutor;
import org.openqa.selenium.MutableCapabilities;
import org.openqa.selenium.WebDriver;
import org.openqa.selenium.WebDriverException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import primemover.capture.RecordingProxy;
import primemover.model.Crawler;
import primemover.model.IJob;
import primemover.runner.context.IRunnerContext;

import javax.annotation.Nullable;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.Optional;
import java.util.concurrent.TimeUnit;

/**
 * Solves a challenge page through Anti-Captcha's HTTP API, with no browser
 * extension -- the solver for Multilogin X profiles, where the extension cannot
 * be installed (the plugin path only knows the Multilogin 6 profile API, which
 * an MLX box does not run).
 *
 * <p>
 * It acts only on a page {@link ChallengePageDetector} recognises, so a job on
 * an ordinary page costs one page-source read and nothing is paid for. Of the
 * challenges it recognises, it can solve one: Bing's "One last step" page, a
 * Cloudflare Turnstile widget. For that it reads the widget parameters from the
 * page ({@link BingTurnstileChallenge}), buys a token for exactly those
 * parameters, hands it to Bing's verify endpoint the way the page's own callback
 * would ({@code anticaptcha/bing-turnstile-verify.js}), reloads, and looks
 * again. The page after the reload decides: solved means the challenge is gone,
 * whatever the verify call said. A challenge still there is tried once more with
 * the new page's parameters (Bing re-renders the widget with a fresh
 * {@code impressionGuid}), then given up. After {@value #MAX_UNSUCCESSFUL_TOKENS}
 * tokens in a row that did not get through, the session stops buying and only
 * reports. Google's sorry page and the generic Cloudflare interstitial are
 * reported as found and not solved, never paid for.
 *
 * <p>
 * Anti-Captcha is told the page's address without its query string: the bot's
 * search terms are experiment data, and a token is bound to the site, not the
 * query.
 *
 * <p>
 * {@code solve} never throws: a challenge it could not get through is a result,
 * and {@code TaskOperator} turns a job that ends on one into a warning.
 */
public class ApiCaptchaSolver implements ICaptchaSolver {
	private static final Logger LOGGER = LoggerFactory.getLogger(ApiCaptchaSolver.class);

	private static final String VERIFY_SCRIPT_RESOURCE = "/anticaptcha/bing-turnstile-verify.js";
	private static final String VERIFY_SCRIPT = loadScript();

	/** One token, and one more if the reload shows a fresh challenge. */
	static final int MAX_ATTEMPTS = 2;

	/**
	 * Tokens in a row that did not get the session through, after which it stops
	 * buying. Every visit, wait and search job calls the solver before and after
	 * itself, so a session Bing will not let through would otherwise buy two
	 * tokens per call, and spend ~30 s per token, for the rest of its run.
	 */
	static final int MAX_UNSUCCESSFUL_TOKENS = 4;

	/** Per solver, and a solver lives as long as one browser session. */
	private int unsuccessfulTokens = 0;

	@Autowired
	private TurnstileTokenSource service;

	@Value("${anticaptcha.enabled:true}")
	private Boolean enabled;

	public ApiCaptchaSolver() {
		// wired by CaptchaSolverFactory
	}

	ApiCaptchaSolver(TurnstileTokenSource service, boolean enabled) {
		this.service = service;
		this.enabled = enabled;
	}

	private static String loadScript() {
		try (InputStream in = ApiCaptchaSolver.class.getResourceAsStream(VERIFY_SCRIPT_RESOURCE)) {
			if (in == null) {
				throw new IllegalStateException("classpath resource missing: " + VERIFY_SCRIPT_RESOURCE);
			}
			return new String(in.readAllBytes(), StandardCharsets.UTF_8);
		} catch (IOException e) {
			throw new IllegalStateException("cannot read " + VERIFY_SCRIPT_RESOURCE, e);
		}
	}

	/** Nothing to install in the browser: the solve happens over HTTP. */
	@Override
	public boolean setOptions(Crawler crawler, MutableCapabilities options, RecordingProxy recorder) {
		if (!enabled) {
			LOGGER.info("Anticaptcha disabled");
			return false;
		}

		LOGGER.info("Anticaptcha enabled (API solver: Bing Turnstile)");
		return true;
	}

	@Override
	public boolean setupDriver(IRunnerContext<?> context) {
		return true;
	}

	/**
	 * Solve the challenge on the current page, if there is one we can solve.
	 * {@code submit} is ignored: handing the token over is the solve.
	 */
	@Override
	public CaptchaSolverResult solve(IRunnerContext<?> context, IJob job, Boolean submit) {
		if (!enabled) {
			return CaptchaSolverResult.DISABLED;
		}

		WebDriver driver = context.getDriver();
		String url = currentUrl(driver);
		String source = pageSource(driver);

		Optional<String> detected = ChallengePageDetector.detect(url, source);
		if (detected.isEmpty()) {
			return CaptchaSolverResult._NOCAPTCHADETECTED;
		}

		String label = detected.get();
		if (!ChallengePageDetector.BING_TURNSTILE_LABEL.equals(label)) {
			LOGGER.warn("job {}: {}, which the runner cannot solve", job.getId(), label);
			return CaptchaSolverResult.of(true, true, true, false, 0, false, false, label);
		}

		StopWatch timing = StopWatch.createStarted();
		boolean machineryFailed = false;

		for (int attempt = 1; attempt <= MAX_ATTEMPTS; attempt++) {
			if (unsuccessfulTokens >= MAX_UNSUCCESSFUL_TOKENS) {
				LOGGER.warn("job {}: {}, but {} tokens in a row have failed this session; not buying more",
						job.getId(), label, unsuccessfulTokens);
				break;
			}

			Optional<BingTurnstileChallenge> challenge = BingTurnstileChallenge.parse(source);
			if (challenge.isEmpty()) {
				LOGGER.warn("job {}: {} without a readable widget config, not solving", job.getId(), label);
				machineryFailed = true;
				break;
			}

			LOGGER.info("job {}: {} (attempt {}/{}), asking Anti-Captcha for {}", job.getId(), label, attempt,
					MAX_ATTEMPTS, challenge.get());
			Optional<String> token = service.solveTurnstileProxyless(withoutQuery(url), challenge.get().getSiteKey(),
					challenge.get().getAction(), challenge.get().getCData());
			if (token.isEmpty()) {
				LOGGER.warn("job {}: no Turnstile token from Anti-Captcha", job.getId());
				machineryFailed = true;
				break;
			}

			handTokenToPage(driver, challenge.get(), token.get(), job);
			reload(driver, job);

			url = currentUrl(driver);
			source = pageSource(driver);
			if (ChallengePageDetector.detect(url, source).isEmpty()) {
				unsuccessfulTokens = 0;
				timing.stop();
				LOGGER.info("job {}: {} solved on attempt {} in {} s", job.getId(), label, attempt,
						timing.getTime(TimeUnit.SECONDS));
				return CaptchaSolverResult.of(true, true, true, true, timing.getTime(TimeUnit.SECONDS), false,
						false, label);
			}

			unsuccessfulTokens++;
			LOGGER.warn("job {}: still on {} after attempt {}", job.getId(), label, attempt);
		}

		timing.stop();
		return CaptchaSolverResult.of(true, true, true, false, timing.getTime(TimeUnit.SECONDS), false,
				machineryFailed, label);
	}

	/** Bing's verify call, made from inside the page; the outcome is logged, the reload decides. */
	private void handTokenToPage(WebDriver driver, BingTurnstileChallenge challenge, String token, IJob job) {
		if (!(driver instanceof JavascriptExecutor)) {
			LOGGER.warn("job {}: driver cannot run scripts, token not handed to the page", job.getId());
			return;
		}

		try {
			Object outcome = ((JavascriptExecutor) driver).executeAsyncScript(VERIFY_SCRIPT,
					challenge.getVerifyEndpoint(), token, challenge.getAction(), challenge.getPartner());
			LOGGER.info("job {}: Bing verify answered {}", job.getId(), outcome);
		} catch (WebDriverException e) {
			LOGGER.warn("job {}: Bing verify call failed: {}", job.getId(), e.getMessage());
		}
	}

	private static void reload(WebDriver driver, IJob job) {
		try {
			driver.navigate().refresh();
		} catch (WebDriverException e) {
			// a slow reload still leaves a page to look at
			LOGGER.warn("job {}: reload after the verify call failed: {}", job.getId(), e.getMessage());
		}
	}

	/** {@code scheme://host/path}: the page, without the query and fragment. */
	static String withoutQuery(@Nullable String url) {
		if (url == null) {
			return "";
		}
		int end = url.length();
		int query = url.indexOf('?');
		int fragment = url.indexOf('#');
		if (query >= 0) {
			end = query;
		}
		if (fragment >= 0 && fragment < end) {
			end = fragment;
		}
		return url.substring(0, end);
	}

	private static @Nullable String currentUrl(WebDriver driver) {
		try {
			return driver.getCurrentUrl();
		} catch (WebDriverException e) {
			return null;
		}
	}

	private static @Nullable String pageSource(WebDriver driver) {
		try {
			return driver.getPageSource();
		} catch (WebDriverException e) {
			return null;
		}
	}
}
