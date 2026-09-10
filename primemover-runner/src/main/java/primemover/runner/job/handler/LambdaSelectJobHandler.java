package primemover.runner.job.handler;

import com.fasterxml.jackson.annotation.JsonAutoDetect.Visibility;
import com.fasterxml.jackson.annotation.PropertyAccessor;
import com.fasterxml.jackson.core.json.JsonReadFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.json.JsonMapper;
import org.apache.commons.lang3.StringUtils;
import org.openqa.selenium.By;
import org.openqa.selenium.JavascriptExecutor;
import org.openqa.selenium.NoSuchElementException;
import org.openqa.selenium.TimeoutException;
import org.openqa.selenium.WebDriverException;
import org.openqa.selenium.WebDriver;
import org.openqa.selenium.WebElement;
import org.openqa.selenium.support.ui.ExpectedConditions;
import org.openqa.selenium.support.ui.WebDriverWait;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import primemover.util.consent.ConsentHandler;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.beans.factory.annotation.Autowired;
import primemover.lambda.LambdaCaller;
import primemover.lambda.LambdaCallerFactory;
import primemover.model.jobs.LambdaSelectJob;
import primemover.runner.DecisionVerdict;
import primemover.runner.JobDiagnostics;
import primemover.util.WatchPageAnchor;
import primemover.runner.context.IJobAwareRunnerContext;
import primemover.runner.job.IJobHandler;
import primemover.service.ICrawlerService;
import primemover.service.IStorageService;
import primemover.service.datawriter.DataWriterCachingDecorator;
import primemover.service.datawriter.IDataWriter;
import primemover.service.datawriter.lambda.LambdaInvocationData;
import primemover.service.datawriter.lambda.LambdaInvocationWriter;
import primemover.lambda.LambdaInvokeResult;

import javax.xml.xpath.XPath;
import javax.xml.xpath.XPathExpressionException;
import javax.xml.xpath.XPathFactory;
import java.time.Duration;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

public class LambdaSelectJobHandler implements IJobHandler {
	private static Logger logger = LoggerFactory.getLogger(LambdaSelectJobHandler.class);

	/**
	 * Longest single warning detail attached to a job. JobService truncates the
	 * whole status_message at 1800 characters, so no single warning may be allowed
	 * to crowd out the others.
	 */
	static final int MAX_WARNING_DETAIL_CHARS = 200;

	/**
	 * How many of the decision function's own free-text warnings are attached to
	 * the job. The rest stay in the runner log and in the recorded payload; the
	 * status_message only has to say that the decision was degraded and why.
	 */
	static final int MAX_LAMBDA_WARNINGS_REPORTED = 4;

	@Autowired(required = false)
	private List<IStorageService> storages = new ArrayList<IStorageService>();

	private XPathFactory factory = XPathFactory.newInstance();

	/**
	 * Reload once when the watch page has no recommendation container.
	 *
	 * A YouTube SPA navigation can leave the shell in place -- ytd-watch-flexy
	 * and an empty #related -- while the data fetch that fills it never lands.
	 * The page is then permanently useless: no recommendations, no present
	 * video, nothing to decide from. Nothing in the workflow re-anchors, because
	 * the two visitjobs run only at the start, so every remaining
	 * lambdasingleselectjob grinds against the same dead page.
	 *
	 * Observed on experiment 147: bot soak3_us_04 clicked a recommendation at
	 * 23:01 and spent its remaining 49 decisions and 147 skipped jobs on the
	 * resulting shell, finishing 86 of 284 jobs while the other ten bots
	 * finished 270-278.
	 *
	 * Set false to restore the previous behaviour (send the dead page to the
	 * decision function and let the job fail).
	 */
	@Value("${runner.RecoverUnrenderedWatchPage:true}")
	private boolean recoverUnrenderedWatchPage;

	@Autowired
	private LambdaCallerFactory callerFac;

	/**
	 * Only for the bot's watch history. Optional on purpose: a context without
	 * this bean (a unit test, a stripped profile) must still run the job and
	 * simply send no history, which the decision function reads as unknown.
	 */
	@Autowired(required = false)
	private ICrawlerService crawlerService;

	private LambdaCaller caller = null;

	private ObjectMapper mapper;

	public LambdaSelectJobHandler() {
		super();
		// Build mapper that allows NaN/Infinity values (Lambda may return these in numeric fields)
		mapper = JsonMapper.builder()
				.enable(JsonReadFeature.ALLOW_NON_NUMERIC_NUMBERS)
				.build();
		mapper.setVisibility(PropertyAccessor.FIELD, Visibility.ANY); // amazon invocation result needs higher
																		// visibility...
	}

	/**
	 * Rejects an unusable xpath from the decision function before it reaches JAXP.
	 *
	 * <p>An empty xpath is a normal, expected answer: the decision function returns one when it
	 * could not select a recommendation at all -- most often because the page it was handed was
	 * not a watch page (a consent wall, a bot check, or a Chromium network-error page). Compiling
	 * "" raises "XPathExpressionException: Empty expression!", which reads like a malformed
	 * selector rather than a page that never loaded, and sends whoever debugs it hunting in the
	 * wrong place. The decision function's own explanation is in the payload logged just before
	 * each call to this method.
	 *
	 * <p>Compiling a non-blank value still serves as a syntax check; the compiled expression is
	 * deliberately discarded, because the element itself is located with
	 * {@code By.xpath(parsedResult.getXpath())}.
	 */
	private void validateXpath(XPath xpath, String value, Long jobId) throws XPathExpressionException {
		if (StringUtils.isBlank(value)) {
			throw new RuntimeException("Lambda selected no recommendation (empty xpath) for job " + jobId
					+ ". The analysed page most likely contained no recommendations;"
					+ " see the Lambda payload warnings logged above and the captured page source.");
		}

		xpath.compile(value);
	}

	/**
	 * Turn the decision function's degradation report into the short, single-line
	 * warnings that become the job's {@code status_message} detail.
	 *
	 * <p>
	 * Returns an empty list for a clean decision <em>and</em> for a payload that
	 * carries no degradation report at all (see {@link LambdaSelectJobResult}), so
	 * running against the decision function that is deployed today keeps reporting
	 * {@code success} exactly as before. That second case is not a detail: the
	 * deployed function emits a non-empty {@code warnings[]} for roughly 5% of
	 * decisions (19 of 358 measured on this host), several of them benign, so
	 * attaching those unconditionally would flip those jobs from success to warning
	 * the moment this jar is deployed -- with no other component having changed.
	 * Their text is still logged at WARN by {@link #reportDegradation}; it simply
	 * does not move the job's status. Once the decision function is redeployed, the
	 * explicit {@code degraded} / {@code treatment_lost} flags govern.
	 *
	 * <p>
	 * Kept pure and package-private so the mapping from payload to warning text can
	 * be tested without a browser.
	 */
	static List<String> describeDegradation(LambdaSelectJobResult parsed) {
		List<String> out = new ArrayList<>();

		if (parsed == null || !parsed.hasDegradationReport()) {
			return out;
		}

		if (parsed.isTreatmentLost()) {
			// The single most important line in this file: the arm LOST its treatment.
			//
			// Gated on the decision function's own treatment_lost flag, which is
			// "degraded AND no requested treatment applied" computed once at the source.
			// It is emphatically NOT gated on selection_mode: a control crawler
			// (pi=psi=kappa=tau=0) requests no LLM rating filter at all, so it draws
			// uniformly and the decision function correctly reports
			// selection_mode=random with degraded=false and treatment_lost=false
			// (bot-decision-yt tests/test_degradation_reporting.py). 295 of the 358
			// lambda payloads in this host's rotated runner logs are that case, so
			// keying the warning on the mode alone reports ~82% of all select jobs as
			// degraded and buries the handful that really are.
			//
			// For a payload predating the flag, isTreatmentLost() recomputes the same
			// two-term rule locally -- see LambdaSelectJobResult.
			out.add("lambda treatment_lost: a requested treatment did not apply, so this job produced"
					+ " control-arm data" + modeSuffix(parsed) + suffix(parsed.describeLlmProblem(), "llm "));
		}

		for (String failed : parsed.failedFilters()) {
			out.add("lambda filter " + shorten(failed, MAX_WARNING_DETAIL_CHARS));
		}

		if (out.isEmpty() && parsed.isDegraded()) {
			// degraded, but neither the treatment flag nor a named filter says why
			out.add("lambda reported a degraded decision" + suffix(parsed.describeLlmProblem(), "llm "));
		}

		if (out.isEmpty()) {
			// A report is present and it says nothing went wrong. The decision
			// function's own warnings[] are then informational (a control crawler that
			// skipped a filter still warns about it), so they stay in the log and in
			// the recorded payload rather than turning a healthy job into a warning.
			return out;
		}

		List<String> lambdaWarnings = parsed.getWarnings();
		int reported = 0;
		for (String warning : lambdaWarnings) {
			if (reported >= MAX_LAMBDA_WARNINGS_REPORTED) {
				out.add("lambda: (+" + (lambdaWarnings.size() - reported)
						+ " more warnings, see the recorded lambda payload)");
				break;
			}

			String text = shorten(warning, MAX_WARNING_DETAIL_CHARS);
			if (text != null) {
				out.add("lambda: " + text);
				reported++;
			}
		}

		return out;
	}

	/**
	 * Log the degradation report and attach it to the running job.
	 *
	 * <p>
	 * The attached warnings make {@link primemover.runner.TaskOperator} finish the
	 * job as {@code warning} instead of {@code success} -- the whole point of
	 * parsing the report at all. A degraded decision is <b>not</b> a job failure:
	 * the click still happened and the recorded data is still worth keeping, it
	 * simply must not be read as clean treatment data.
	 *
	 * <p>
	 * It also records the parsed verdict on the job
	 * ({@link JobDiagnostics#recordDecisionVerdict}), which is what carries
	 * {@code degraded} / {@code treatment_lost} / {@code selection_mode} / the
	 * filter report into the job PUT and therefore onto the job row. That is a
	 * strictly additive step: it changes no status and no message, and it happens
	 * only for a payload that carried a degradation report, so a legacy payload
	 * still records nothing at all rather than a manufactured "clean".
	 *
	 * <p>
	 * Package-private so a test can drive it without a browser or a Spring context.
	 *
	 * @param alreadyReported warnings already attached during this job, so a
	 *                        re-invocation on a click retry does not attach the same
	 *                        text twice
	 */
	void reportDegradation(LambdaSelectJobResult parsed, Long jobId, Set<String> alreadyReported) {
		if (parsed == null) {
			return;
		}

		List<String> lambdaWarnings = parsed.getWarnings();

		if (!parsed.hasDegradationReport()) {
			// A payload from a decision function that predates the report. Absence
			// reads as "unknown", never as degraded: say what came back, leave the
			// job's status alone.
			if (!lambdaWarnings.isEmpty()) {
				logger.warn("Lambda returned {} warning(s) for job {} in a payload carrying no degradation report"
						+ " (the deployed decision function predates it), so the job status is left unchanged: {}",
						lambdaWarnings.size(), jobId, lambdaWarnings);
			}
			return;
		}

		// Persist the verdict on the job row. Note this happens for a CLEAN report
		// too -- degraded=false on a control crawler is a measurement, and recording
		// it is what lets the API tell "this decision was fine" apart from "nobody
		// ever looked". It is deliberately not gated on the branches below.
		JobDiagnostics.recordDecisionVerdict(DecisionVerdict.of(parsed));

		if (parsed.isTreatmentLost()) {
			logger.error(
					"Lambda decision LOST THE TREATMENT for job {}: a requested treatment did not apply, so this"
							+ " crawler produced control-arm data for this job whatever its assignment says."
							+ " selection_mode={} llm={} filters={}",
					jobId, parsed.getSelectionMode(), parsed.describeLlmProblem(), parsed.filtersSnapshot());
		} else if (parsed.isDegraded()) {
			logger.warn("Lambda reported a degraded decision for job {}: selection_mode={} filters={} llm={}", jobId,
					parsed.getSelectionMode(), parsed.filtersSnapshot(), parsed.describeLlmProblem());
		} else if (parsed.isRandomSelection()) {
			// Random, and nothing degraded: this crawler requested no rating filter,
			// i.e. it IS a control. Expected, and by far the common case -- not a
			// warning. Note this branch is reached only after the two above, so the
			// mode is never read on its own.
			logger.debug("Lambda picked the video at random for job {} with nothing degraded (control crawler)."
					+ " filters={}", jobId, parsed.filtersSnapshot());
		}

		List<String> failedFilters = parsed.failedFilters();
		if (!failedFilters.isEmpty()) {
			logger.warn("Lambda decision degraded for job {}: {} filter(s) failed: {} (applied: {})", jobId,
					failedFilters.size(), failedFilters, parsed.appliedFilters());
		}

		if (!lambdaWarnings.isEmpty()) {
			logger.warn("Lambda returned {} warning(s) for job {}: {}", lambdaWarnings.size(), jobId, lambdaWarnings);
		}

		for (String warning : describeDegradation(parsed)) {
			if (alreadyReported.add(warning)) {
				JobDiagnostics.addWarning(warning);
			}
		}
	}

	/**
	 * {@code " (selection_mode=random)"}, or nothing when the payload did not say.
	 * Descriptive detail only -- the decision to warn was already taken by the
	 * caller.
	 */
	private static String modeSuffix(LambdaSelectJobResult parsed) {
		String mode = shorten(parsed.getSelectionMode(), 40);
		return mode == null ? "" : " (selection_mode=" + mode + ")";
	}

	private static String suffix(String detail, String label) {
		String text = shorten(detail, MAX_WARNING_DETAIL_CHARS);
		return text == null ? "" : " (" + label + text + ")";
	}

	/**
	 * Collapse to a single truncated line. The status_message is one field in one
	 * database column: a multi-line detail, or a whole stack trace, does not belong
	 * in it.
	 */
	private static String shorten(String value, int limit) {
		if (value == null) {
			return null;
		}

		String text = value.replaceAll("\\s+", " ").strip();
		if (text.isEmpty()) {
			return null;
		}

		return text.length() > limit ? text.substring(0, limit - 3) + "..." : text;
	}

	private void init() {
		logger.debug("initializing job");

		if (caller != null) {
			return;
		}

		if (callerFac == null) {
			throw new IllegalStateException("no LambdaCallerFactory available");
		}

		caller = callerFac.getInstance();

	}

	/**
	 * Whether a click failure is one Layer A retries and Layer B can recover.
	 *
	 * Exactly two cases, both ending in the same navigate-by-id fallback:
	 * {@link NoSuchElementException} (the xpath matched nothing) and
	 * {@link TimeoutException} (the element was found but never became clickable,
	 * e.g. obscured by YouTube's endscreen video-wall). Every other
	 * WebDriverException keeps its previous behaviour of propagating, so this does
	 * not silently swallow an unrelated driver fault.
	 */
	static boolean isRecoverableClickFailure(Throwable t) {
		return t instanceof NoSuchElementException || t instanceof TimeoutException;
	}

	@Override
	public void handle(IJobAwareRunnerContext<?> context) {
		logger.debug("running job");

		init();

		if (caller == null) {
			throw new IllegalStateException("no LambdaCaller created");
		}
		// grab some data and type it
		WebDriver driver = context.getDriver();
		LambdaSelectJob typedJob = (LambdaSelectJob) context.getJob();

		// prepare recorder
		LambdaInvocationData data = new LambdaInvocationData();
		data.setJob(context.getJob());

		Exception ex = null;

		try {

			// --- Layer C: DOM Stabilization ---
			// Wait for recommendations, and re-anchor once if the page came back
			// as an empty SPA shell. Without this a single bad landing costs the
			// rest of the session, not one job: nothing else in the workflow
			// navigates again.
			WatchPageAnchor.Outcome anchored = WatchPageAnchor.ensureRecommendations(
					driver, typedJob.getId(), recoverUnrenderedWatchPage);
			if (anchored == WatchPageAnchor.Outcome.RECOVERED) {
				// Recovered is not normal: this page is not what the previous
				// click produced, so the session is marked degraded rather than
				// passing as ordinary.
				JobDiagnostics.addWarning(
						"watch page had no recommendation container; recovered by reloading once");
			} else if (anchored == WatchPageAnchor.Outcome.REANCHORED) {
				// The session continues from an earlier page, so the chain of
				// recommendations has a hole in it: the video that would have
				// followed the previous click was never shown. Worth recording
				// distinctly from a plain reload recovery -- an analysis may want
				// to drop these sessions, and cannot if they look identical.
				JobDiagnostics.addWarning(
						"watch page was unusable and a reload did not fix it; session re-anchored to the "
								+ "last page with recommendations, so one step of the chain is missing");
			} else if (anchored == WatchPageAnchor.Outcome.ABSENT) {
				JobDiagnostics.addWarning(
						"watch page had no recommendation container, and it could not be restored");
			}

			// prepare input JSON
			LambdaSelectJobParam param = new LambdaSelectJobParam(typedJob, context.getCrawler(),
					driver.getPageSource(), context.getCrawler().getRawConfiguration());

			// The bot's own history, so the decision function can drop videos it
			// has already watched. Null when we could not read it -- never an
			// empty list, which would tell the decision function this bot has
			// watched nothing and let it report a filter it never applied.
			if (crawlerService != null) {
				param.setAlreadyWatched(crawlerService.getWatchedVideoIds(context.getCrawler().getId()));
			}

			data.setInputJson(mapper.writeValueAsString(param));

			LambdaInvokeResult result = this.caller.invoke(typedJob.getLinkValue(), data.getInputJson());
			data.setOutputJson(mapper.writeValueAsString(result));

			if (!StringUtils.isEmpty(result.getFunctionError())) {
				// Enhanced error logging with more details
				logger.error("Lambda function '{}' failed with error type: {}",
							typedJob.getLinkValue(), result.getFunctionError());
				logger.error("Lambda status code: {}", result.getStatusCode());

				// Try to parse error details from payload
				String errorPayload = result.getPayload();
				logger.error("Lambda error response payload: {}", errorPayload);

				// Try to extract structured error information
				try {
					// Attempt to parse as JSON to get structured error info
					Object errorObj = mapper.readValue(errorPayload, Object.class);
					logger.error("Parsed Lambda error details: {}", mapper.writeValueAsString(errorObj));
				} catch (Exception parseEx) {
					logger.debug("Could not parse Lambda error payload as JSON: {}", parseEx.getMessage());
				}

				throw new RuntimeException("Lambda Error (functionError): " + result.getFunctionError() +
										 " - Status: " + result.getStatusCode() +
										 " - Details: " + errorPayload);
			}

			if (!(result.getStatusCode() >= 200 && result.getStatusCode() < 300)) {
				logger.error("AWS responded with status code {}", result.getStatusCode());
				throw new RuntimeException("Lambda Error (status code): " + result.getStatusCode());
			}

			// Log the full Lambda response payload BEFORE attempting deserialization
			String lambdaResponsePayload = result.getPayload();
			logger.info("Full Lambda response payload: {}", lambdaResponsePayload);

			LambdaSelectJobResult parsedResult = mapper.readValue(lambdaResponsePayload,
					LambdaSelectJobResult.class);

			// Warnings already attached to this job, so the re-invocation below cannot
			// attach the same text a second time.
			Set<String> reportedDegradation = new LinkedHashSet<>();
			// Read the degradation report BEFORE validating the xpath: when the decision
			// function returned no selection at all, why it failed is exactly what the
			// resulting job failure needs to say.
			reportDegradation(parsedResult, typedJob.getId(), reportedDegradation);

			XPath xpath = factory.newXPath();
			boolean navigatedDirectly = false;

			// An empty xpath with a selection is recoverable, and used to be fatal.
			// When the recommendations sidebar has not rendered, the decision
			// function can still choose a video -- it reads the candidates from the
			// page's ytInitialData -- but there is no element on the page to build
			// an xpath against, so it returns the selection with an empty xpath.
			// validateXpath then threw before Layer B's navigate-by-id fallback
			// could run, failing a job whose answer we already had. Observed on
			// experiment 146: 20 recommendations recovered, one selected, job failed.
			if (StringUtils.isBlank(parsedResult.getXpath()) && parsedResult.getSelected() != null
					&& StringUtils.isNotBlank(parsedResult.getSelected().getId())) {
				String videoId = parsedResult.getSelected().getId();
				String videoUrl = "https://www.youtube.com/watch?v=" + videoId;
				logger.warn("No xpath for job {} (the page had no rendered recommendation elements);"
						+ " navigating directly to the selected video {}", typedJob.getId(), videoUrl);
				JobDiagnostics.addWarning("no clickable recommendation on the page; navigated directly to "
						+ videoId + " using the decision function's selection");
				driver.get(videoUrl);
				Thread.sleep(3000);
				navigatedDirectly = true;
			}

			if (!navigatedDirectly) {
				validateXpath(xpath, parsedResult.getXpath(), typedJob.getId());

				// --- Layer A: Retry with re-invocation ---
				// Try up to 3 times to find and click the element, re-invoking lambda on each retry
				int maxAttempts = 3;
				boolean clicked = false;
				// Tracks the last click failure across attempts. Widened from
				// NoSuchElementException to WebDriverException so it also carries a
				// TimeoutException from elementToBeClickable -- see the catch below.
				WebDriverException lastClickFailure = null;

				for (int attempt = 1; attempt <= maxAttempts; attempt++) {
					try {
						WebElement element = driver.findElement(By.xpath(parsedResult.getXpath()));

						// Scroll element into view with offset to avoid sticky headers
						JavascriptExecutor js = (JavascriptExecutor) driver;
						js.executeScript("arguments[0].scrollIntoView({block: 'center', behavior: 'instant'});", element);

						// Small pause to allow scroll to complete and any overlays to settle
						Thread.sleep(300);

						// Wait for element to be clickable
						WebDriverWait wait = new WebDriverWait(driver, Duration.ofSeconds(5));
						wait.until(ExpectedConditions.elementToBeClickable(element));

						// Use JavaScript click to bypass overlapping elements like sticky headers
						js.executeScript("arguments[0].click();", element);

						clicked = true;
						if (attempt > 1) {
							logger.info("Element found and clicked on attempt {}/{}", attempt, maxAttempts);
						}
						break;
					} catch (WebDriverException e) {
						// Two distinct page states, one recovery. NoSuchElement: the
						// xpath matched nothing. Timeout: the element was found but
						// never became clickable -- e.g. a recommendation obscured by
						// YouTube's endscreen video-wall overlay. Only the not-found
						// case was handled before; the timeout escaped the loop and
						// skipped Layer B entirely, failing a job whose selected video
						// id was already in hand (round 8, soak8_ch_03: 29 failures,
						// all found-but-not-clickable). Any other WebDriverException
						// keeps its old behaviour -- it propagates -- so this widens
						// recovery to exactly the two click failures we mean to retry.
						if (!isRecoverableClickFailure(e)) {
							throw e;
						}
						lastClickFailure = e;
						if (attempt < maxAttempts) {
							logger.warn("Element not found or not clickable on attempt {}/{}, "
									+ "re-capturing page and re-invoking lambda", attempt, maxAttempts);
							Thread.sleep(2000); // wait for DOM to settle

							// Re-capture page source and re-invoke lambda
							param = new LambdaSelectJobParam(typedJob, context.getCrawler(),
									driver.getPageSource(), context.getCrawler().getRawConfiguration());
							data.setInputJson(mapper.writeValueAsString(param));
							result = this.caller.invoke(typedJob.getLinkValue(), data.getInputJson());
							data.setOutputJson(mapper.writeValueAsString(result));

							// Re-parse result
							lambdaResponsePayload = result.getPayload();
							logger.info("Retry Lambda response payload: {}", lambdaResponsePayload);
							parsedResult = mapper.readValue(lambdaResponsePayload, LambdaSelectJobResult.class);

							// The retry is a second, independent decision: it can be degraded
							// even when the first one was not (and vice versa).
							reportDegradation(parsedResult, typedJob.getId(), reportedDegradation);

							// Validate new XPath. The re-invocation can just as easily come back
							// with no selection as the first call did, so it needs the same guard.
							validateXpath(xpath, parsedResult.getXpath(), typedJob.getId());
						}
					}
				}

				// --- Layer B: Direct URL navigation fallback ---
				if (!clicked) {
					if (parsedResult.getSelected() != null && parsedResult.getSelected().getId() != null) {
						String videoId = parsedResult.getSelected().getId();
						String videoUrl = "https://www.youtube.com/watch?v=" + videoId;
						logger.warn("Element not found after {} attempts, falling back to direct navigation: {}",
								maxAttempts, videoUrl);
						driver.get(videoUrl);
						// Wait for page load
						Thread.sleep(3000);
					} else {
						logger.error("All {} click attempts failed and no selected video ID available for fallback",
								maxAttempts);

						// lastClickFailure is null whenever the loop ended for a reason other
						// than a caught click failure. Rethrowing it unguarded then raised a bare
						// NullPointerException from this line, which says nothing about the
						// recommendation that could not be clicked and sends whoever reads the
						// job failure looking for a bug here instead of at the page.
						if (lastClickFailure != null) {
							throw lastClickFailure;
						}

						throw new RuntimeException("Lambda select job " + typedJob.getId()
								+ " neither clicked a recommendation nor received a video id to fall back to."
								+ " xpath was: " + parsedResult.getXpath());
					}
				}

				// The click (SPA navigation) or the fallback driver.get() can bring the
				// consent bump back, e.g. after a full reload; answer it per policy so
				// the watch time that follows is not spent behind a modal.
			}

			Thread.sleep(1500);
			ConsentHandler.handle(driver);

		} catch (Exception e) {
			logger.warn("error executing lambda", e);
			ex = e;
		}

		// write result
		LambdaInvocationWriter writer = new LambdaInvocationWriter(data);
		IDataWriter cachedWriter = new DataWriterCachingDecorator(writer);
		for (IStorageService storage : storages) {
			storage.writeData(typedJob, cachedWriter);
		}

		if (ex != null) {
			throw new RuntimeException("error executing lambda job", ex);
		}
	}

}
