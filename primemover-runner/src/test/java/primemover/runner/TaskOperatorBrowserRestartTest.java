package primemover.runner;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.InOrder;
import org.openqa.selenium.JavascriptExecutor;
import org.openqa.selenium.TimeoutException;
import org.openqa.selenium.WebDriver;
import org.springframework.test.util.ReflectionTestUtils;
import primemover.anticaptcha.CaptchaSolverFactory;
import primemover.anticaptcha.ICaptchaSolver;
import primemover.capture.HarRecordingFilter;
import primemover.capture.RecordingProxy;
import primemover.model.Crawler;
import primemover.model.IJob;
import primemover.model.JobState;
import primemover.model.Joblist;
import primemover.model.MultiLoginAgent;
import primemover.model.MultiLoginVersion;
import primemover.model.jobs.VisitJob;
import primemover.runner.context.ChromeContext;
import primemover.runner.context.IJobAwareRunnerContext;
import primemover.runner.context.MultiloginContext;
import primemover.runner.job.JobDispatcher;
import primemover.service.IJobService;
import primemover.util.BrowserUnresponsiveException;

import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.RETURNS_DEEP_STUBS;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.timeout;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.mockito.Mockito.withSettings;

/**
 * A frozen browser used to end its session. On experiment 189 (2026-10-07,
 * 09:00-12:00) 38 of 40 measurement sessions lost jobs that way and 29% of their
 * jobs were skipped. A Multilogin X browser is now restarted -- at most twice per
 * session -- and the session goes on with its next job.
 */
class TaskOperatorBrowserRestartTest {

	private static final String URL = "https://www.rottentomatoes.com/";

	private TaskOperator operator;
	private IContextBuilder builder;
	private ICaptchaSolver captchaSolver;
	private RecordingProxy recorder;
	private Crawler crawler;
	private MultiloginContext frozen;
	private final List<MultiloginContext> started = new ArrayList<>();

	/** A driver that is also a JavascriptExecutor, as the real Remote one is. */
	private static WebDriver driver() {
		WebDriver driver = mock(WebDriver.class,
				withSettings().extraInterfaces(JavascriptExecutor.class).defaultAnswer(RETURNS_DEEP_STUBS));
		when(((JavascriptExecutor) driver).executeScript("return document.body.outerHTML;"))
				.thenReturn("<body>page</body>");
		return driver;
	}

	private MultiloginContext context(Crawler crawler) {
		MultiloginContext ctx = new MultiloginContext();
		ctx.setCrawler(crawler);
		ctx.setRecordingProxy(recorder);
		ctx.setDriver(driver());
		return ctx;
	}

	private static Crawler crawler(MultiLoginVersion version) {
		Crawler crawler = new Crawler();
		crawler.setId(4711L);
		crawler.setAgent(new MultiLoginAgent(17L, "bot", null, null, "profile-uuid", version, null));
		return crawler;
	}

	@BeforeEach
	void setUp() {
		operator = new TaskOperator();
		builder = mock(IContextBuilder.class);
		captchaSolver = mock(ICaptchaSolver.class);
		recorder = mock(RecordingProxy.class);
		crawler = crawler(MultiLoginVersion.MLX);
		frozen = context(crawler);

		CaptchaSolverFactory solvers = mock(CaptchaSolverFactory.class);
		when(solvers.getCaptchaSolver()).thenReturn(captchaSolver);

		// the profile starts again through the same builder the session start uses
		when(builder.buildContext(eq(crawler), any(), eq(recorder), eq(captchaSolver))).thenAnswer(invocation -> {
			MultiloginContext ctx = context(crawler);
			started.add(ctx);
			return ctx;
		});

		ReflectionTestUtils.setField(operator, "contextBuilders", Map.of("MlxContextBuilder", builder));
		ReflectionTestUtils.setField(operator, "captchaSolverFac", solvers);
		ReflectionTestUtils.setField(operator, "pageLoadTimeoutSeconds", 60);
		ReflectionTestUtils.setField(operator, "context", frozen);
	}

	@AfterEach
	void clearInterrupt() {
		Thread.interrupted();
	}

	private Object context() {
		return ReflectionTestUtils.getField(operator, "context");
	}

	// ---- the pure decisions ----------------------------------------------

	@Test
	@DisplayName("a frozen Multilogin X browser may be restarted twice per session, not a third time")
	void restartLimit() {
		assertThat(TaskOperator.browserRestartRefusal(false, false, true, 0, 2)).isNull();
		assertThat(TaskOperator.browserRestartRefusal(false, false, true, 1, 2)).isNull();
		assertThat(TaskOperator.browserRestartRefusal(false, false, true, 2, 2)).contains("limit");
		assertThat(TaskOperator.browserRestartRefusal(false, false, true, 0, 0)).as("0 disables restarts")
				.isNotNull();
	}

	@Test
	@DisplayName("other browsers keep today's behaviour: no restart")
	void onlyMultiloginX() {
		assertThat(TaskOperator.browserRestartRefusal(false, false, false, 0, 2)).contains("Multilogin X");
	}

	@Test
	@DisplayName("a cut-off or interrupted session is never restarted, whatever else holds")
	void abortWins() {
		assertThat(TaskOperator.browserRestartRefusal(true, false, true, 0, 2)).contains("cut off");
		assertThat(TaskOperator.browserRestartRefusal(false, true, true, 0, 2)).contains("cut off");
		assertThat(TaskOperator.browserRestartRefusal(true, false, false, 5, 2)).contains("cut off");
	}

	@Test
	@DisplayName("the restarted job's status says so, in place of 'browser unresponsive'")
	void restartedJobMessage() {
		BrowserUnresponsiveException frozenLoad = new BrowserUnresponsiveException(
				"Page load timed out for " + URL + " (job 7); browser unresponsive",
				new TimeoutException("Timed out receiving message from renderer: 58.5"));

		String detail = TaskOperator.restartedDetail(frozenLoad, 1);
		assertThat(detail).startsWith("TimeoutException: Page load timed out for " + URL + " (job 7)")
				.contains("; browser restarted (profile restart 1)").doesNotContain("unresponsive")
				.contains("58.5");

		// a handler that wraps what it caught does not lose the wording
		assertThat(TaskOperator.restartedDetail(new RuntimeException("visit failed", frozenLoad), 2))
				.contains("browser restarted (profile restart 2)").doesNotContain("unresponsive");
	}

	@Test
	@DisplayName("Multilogin's 'still running' answer is recognised anywhere in the cause chain")
	void profileStillRunning() {
		Exception refused = new RuntimeException("failed to start profile", new IllegalStateException(
				"400 Bad Request: {\"status\":{\"error_code\":\"PROFILE_ALREADY_RUNNING\"}}"));

		assertThat(TaskOperator.isProfileAlreadyRunning(refused)).isTrue();
		assertThat(TaskOperator.isProfileAlreadyRunning(new RuntimeException("429 Too Many Requests"))).isFalse();
		assertThat(TaskOperator.isProfileAlreadyRunning(null)).isFalse();
	}

	// ---- the restart -------------------------------------------------------

	@Test
	@DisplayName("the old browser is quit and its profile stopped before the profile starts again")
	void oldProfileStoppedBeforeNewStart() {
		WebDriver frozenDriver = frozen.getDriver();

		assertThat(operator.restartFrozenBrowser(7L)).isEqualTo(1);

		InOrder order = inOrder(frozenDriver, builder);
		order.verify(frozenDriver).quit();
		order.verify(builder).destroyContext(frozen);
		order.verify(builder).buildContext(eq(crawler), any(), eq(recorder), eq(captchaSolver));

		// the session now runs on the new browser
		assertThat(started).hasSize(1);
		assertThat(context()).isSameAs(started.get(0));
	}

	@Test
	@DisplayName("the new browser gets what session start applies, through the session's own recorder")
	void restartAppliesTheSessionSetup() {
		operator.restartFrozenBrowser(7L);
		MultiloginContext fresh = started.get(0);

		verify(recorder).applyProxy(any(org.openqa.selenium.remote.DesiredCapabilities.class));
		assertThat(fresh.getCaptchaSolver()).isSameAs(captchaSolver);
		verify(captchaSolver).setupDriver(fresh);
		verify(fresh.getDriver().manage().timeouts()).pageLoadTimeout(Duration.ofSeconds(60));

		// one recorder for the whole session: nothing to leak, nothing torn down
		assertThat(fresh.getRecordingProxy()).isSameAs(recorder);
		verify(recorder, never()).tearDown();
		verify(recorder, never()).tearDown(anyBoolean());
	}

	@Test
	@DisplayName("at most two restarts per session; the third freeze ends it as before")
	void atMostTwoRestarts() {
		assertThat(operator.restartFrozenBrowser(7L)).isEqualTo(1);
		assertThat(operator.restartFrozenBrowser(8L)).isEqualTo(2);
		Object second = context();

		assertThat(operator.restartFrozenBrowser(9L)).isZero();

		verify(builder, times(2)).buildContext(any(), any(), any(), any());
		assertThat(context()).isSameAs(second);
	}

	@Test
	@DisplayName("a Multilogin 6 profile is not restarted")
	void multilogin6IsNotRestarted() {
		ReflectionTestUtils.setField(operator, "context", context(crawler(MultiLoginVersion.ML6)));

		assertThat(operator.restartFrozenBrowser(7L)).isZero();

		verifyNoInteractions(builder);
	}

	@Test
	@DisplayName("a plain Chrome browser is not restarted")
	void chromeIsNotRestarted() {
		ChromeContext chrome = new ChromeContext();
		chrome.setCrawler(crawler);
		chrome.setDriver(driver());
		ReflectionTestUtils.setField(operator, "context", chrome);

		assertThat(operator.restartFrozenBrowser(7L)).isZero();

		verifyNoInteractions(builder);
		verify(chrome.getDriver(), never()).quit();
	}

	@Test
	@DisplayName("a cut-off session is not restarted")
	void abortedSessionIsNotRestarted() {
		ReflectionTestUtils.setField(operator, "aborted", true);

		assertThat(operator.restartFrozenBrowser(7L)).isZero();

		verifyNoInteractions(builder);
		verify(frozen.getDriver(), never()).quit();
	}

	@Test
	@DisplayName("an interrupted worker is not restarted")
	void interruptedWorkerIsNotRestarted() {
		Thread.currentThread().interrupt();

		assertThat(operator.restartFrozenBrowser(7L)).isZero();

		verifyNoInteractions(builder);
	}

	@Test
	@DisplayName("an abort arriving mid-restart wins: the new browser is released, not installed")
	void abortDuringRestartReleasesTheNewBrowser() {
		when(builder.buildContext(any(), any(), any(), any())).thenAnswer(invocation -> {
			// the processing timeout fires while the profile is starting
			operator.tearDown(true);
			MultiloginContext ctx = context(crawler);
			started.add(ctx);
			return ctx;
		});

		assertThat(operator.restartFrozenBrowser(7L)).isZero();

		MultiloginContext fresh = started.get(0);
		verify(fresh.getDriver()).quit();
		verify(builder).destroyContext(fresh);
		assertThat(context()).as("claimed by the abort, not replaced").isNull();
	}

	@Test
	@DisplayName("a start that runs over its bound is given up on, and the browser it brings up later is released")
	void lateStartIsReleased() {
		ReflectionTestUtils.setField(operator, "browserRestartStartSeconds", 1);
		AtomicBoolean late = new AtomicBoolean();
		when(builder.buildContext(any(), any(), any(), any())).thenAnswer(invocation -> {
			Thread.sleep(1500);
			late.set(true);
			MultiloginContext ctx = context(crawler);
			started.add(ctx);
			return ctx;
		});

		long begun = System.nanoTime();
		assertThat(operator.restartFrozenBrowser(7L)).isZero();
		assertThat(Duration.ofNanos(System.nanoTime() - begun)).isLessThan(Duration.ofMillis(1400));
		assertThat(late).isFalse();

		// the session ends; its teardown waits for the late browser to be released
		// before stopping the profile itself, so a later joblist's browser is safe
		operator.tearDown(false);

		MultiloginContext fresh = started.get(0);
		verify(fresh.getDriver(), timeout(2000)).quit();
		InOrder order = inOrder(builder);
		order.verify(builder).destroyContext(frozen); // the restart's stop
		order.verify(builder).destroyContext(fresh); // the late release
		order.verify(builder).destroyContext(frozen); // the session's teardown
	}

	@Test
	@DisplayName("a stop that fails does not prevent the start")
	void failedStopStillStarts() {
		doThrow(new IllegalStateException("profile not running")).when(builder).destroyContext(frozen);

		assertThat(operator.restartFrozenBrowser(7L)).isEqualTo(1);
	}

	@Test
	@DisplayName("a start that fails ends the session as before")
	void failedStartEndsTheSession() {
		when(builder.buildContext(any(), any(), any(), any())).thenThrow(new RuntimeException("failed to start profile"));

		assertThat(operator.restartFrozenBrowser(7L)).isZero();
		assertThat(context()).isSameAs(frozen);
	}

	// ---- the session's end after a restart ----------------------------------

	/**
	 * The launcher stops the frozen profile once, during the restart, and refuses
	 * every later stop of it, as it answered a stop with a 500 on 2026-10-02 for a
	 * profile whose browser was gone.
	 */
	private void secondStopOfTheFrozenProfileIsRefused() {
		AtomicInteger stops = new AtomicInteger();
		doAnswer(invocation -> {
			if (stops.incrementAndGet() > 1) {
				throw new IllegalStateException("500 Internal Server Error: profile not running");
			}
			return null;
		}).when(builder).destroyContext(frozen);
	}

	@Test
	@DisplayName("after a failed restart, a refused stop at the session's end neither throws nor leaks the recorder")
	void refusedStopAfterFailedRestart() {
		secondStopOfTheFrozenProfileIsRefused();
		when(builder.buildContext(any(), any(), any(), any())).thenThrow(new RuntimeException("failed to start profile"));

		assertThat(operator.restartFrozenBrowser(7L)).isZero();
		assertThat(context()).isSameAs(frozen);

		// JobListRunner reports the queue only after this returns: a throw here
		// turned DONE_JOBSWARNING into DONE_FAILURE and lost the job counts
		assertThatCode(() -> operator.tearDown(false)).doesNotThrowAnyException();

		verify(builder, times(2)).destroyContext(frozen);
		verify(recorder).tearDown();
		assertThat(context()).isNull();
	}

	@Test
	@DisplayName("an abort mid-restart whose stop is refused still tears the recorder down")
	void refusedStopOnAbortMidRestart() {
		secondStopOfTheFrozenProfileIsRefused();
		AtomicReference<Throwable> abortFailure = new AtomicReference<>();
		when(builder.buildContext(any(), any(), any(), any())).thenAnswer(invocation -> {
			// the processing timeout fires while the profile is starting
			try {
				operator.tearDown(true);
			} catch (Throwable t) {
				abortFailure.set(t);
			}
			MultiloginContext ctx = context(crawler);
			started.add(ctx);
			return ctx;
		});

		assertThat(operator.restartFrozenBrowser(7L)).isZero();

		assertThat(abortFailure.get()).isNull();
		verify(recorder).tearDown();
		// and the browser that came up anyway is released
		verify(builder).destroyContext(started.get(0));
	}

	@Test
	@DisplayName("a stop the launcher refuses at a plain session end does not skip the driver quit or the recorder")
	void refusedStopAtSessionEnd() {
		doThrow(new IllegalStateException("500 Internal Server Error")).when(builder).destroyContext(frozen);

		assertThatCode(() -> operator.tearDown(false)).doesNotThrowAnyException();

		verify(frozen.getDriver(), times(2)).quit();
		verify(recorder).tearDown();
	}

	// ---- in the job loop ---------------------------------------------------

	@Test
	@DisplayName("a frozen page load fails its job as 'browser restarted', and the next job runs on the new browser")
	void sessionSurvivesAFrozenBrowser() {
		IJobService jobService = mock(IJobService.class);
		when(jobService.updateJobState(any(), any(), any(), any(), any(), any(), any(), any())).thenReturn(true);
		JobDispatcher dispatcher = mock(JobDispatcher.class);
		HarRecordingFilter harFilter = mock(HarRecordingFilter.class);
		when(harFilter.shouldRecordHar(any())).thenReturn(true);
		ReflectionTestUtils.setField(operator, "jobService", jobService);
		ReflectionTestUtils.setField(operator, "runJobHandler", dispatcher);
		ReflectionTestUtils.setField(operator, "harRecordingFilter", harFilter);
		ReflectionTestUtils.setField(operator, "runCaptchaChecks", false);
		ReflectionTestUtils.setField(operator, "skipIpRecording", true);
		((AtomicBoolean) ReflectionTestUtils.getField(operator, "isSetup")).set(true);

		VisitJob frozenVisit = new VisitJob(101L, "visit", null, null, URL);
		VisitJob nextVisit = new VisitJob(102L, "visit", null, null, "https://www.bing.com/");
		Joblist joblist = new Joblist();
		joblist.setId(55L);
		joblist.setJobs(List.<IJob>of(frozenVisit, nextVisit));

		List<WebDriver> drivers = new ArrayList<>();
		doAnswer(invocation -> {
			IJobAwareRunnerContext<?> ctx = invocation.getArgument(0);
			drivers.add(ctx.getDriver());
			if (ctx.getJob() == frozenVisit) {
				throw new BrowserUnresponsiveException(
						"Page load timed out for " + URL + " (job 101); browser unresponsive",
						new TimeoutException("Timed out receiving message from renderer: 58.5"));
			}
			return null;
		}).when(dispatcher).handle(any());

		JobListOutcome outcome = operator.runJobList(joblist);

		assertThat(outcome.getFailed()).isEqualTo(1);
		assertThat(outcome.getSucceeded()).isEqualTo(1);
		assertThat(outcome.getSkipped()).isZero();

		verify(jobService).updateJobState(eq(frozenVisit), eq(JobState.FAILURE),
				argThat(detail -> detail != null && detail.contains("browser restarted (profile restart 1)")
						&& !detail.contains("unresponsive")),
				any(), any(), any(), any(), any());
		verify(jobService).updateJobState(eq(nextVisit), eq(JobState.SUCCESS), any(), any(), any(), any(), any(),
				any());
		verify(jobService, never()).updateJobState(any(), eq(JobState.SKIPPED), anyString(), any(), any(), any(),
				any(), any());

		// the next job ran on the new browser, and the frozen job's HAR was closed
		// before that browser started through the same recorder
		assertThat(drivers).containsExactly(frozen.getDriver(), started.get(0).getDriver());
		InOrder order = inOrder(recorder, builder);
		order.verify(recorder).startCapturing(anyString());
		order.verify(recorder).stopCapturing();
		order.verify(builder).buildContext(any(), any(), any(), any());
	}

	@Test
	@DisplayName("without a restart left, a frozen browser still ends the session as before")
	void exhaustedRestartsEndTheSession() {
		ReflectionTestUtils.setField(operator, "maxBrowserRestarts", 0);
		IJobService jobService = mock(IJobService.class);
		when(jobService.updateJobState(any(), any(), any(), any(), any(), any(), any(), any())).thenReturn(true);
		JobDispatcher dispatcher = mock(JobDispatcher.class);
		ReflectionTestUtils.setField(operator, "jobService", jobService);
		ReflectionTestUtils.setField(operator, "runJobHandler", dispatcher);
		ReflectionTestUtils.setField(operator, "harRecordingFilter", mock(HarRecordingFilter.class));
		ReflectionTestUtils.setField(operator, "runCaptchaChecks", false);
		ReflectionTestUtils.setField(operator, "skipIpRecording", true);
		((AtomicBoolean) ReflectionTestUtils.getField(operator, "isSetup")).set(true);

		VisitJob frozenVisit = new VisitJob(101L, "visit", null, null, URL);
		VisitJob nextVisit = new VisitJob(102L, "visit", null, null, "https://www.bing.com/");
		Joblist joblist = new Joblist();
		joblist.setId(55L);
		joblist.setJobs(List.<IJob>of(frozenVisit, nextVisit));
		doThrow(new BrowserUnresponsiveException("Page load timed out for " + URL + " (job 101); browser unresponsive",
				new TimeoutException("renderer"))).when(dispatcher).handle(any());

		JobListOutcome outcome = operator.runJobList(joblist);

		assertThat(outcome.getFailed()).isEqualTo(1);
		assertThat(outcome.getSkipped()).isEqualTo(1);
		verify(jobService).updateJobState(eq(frozenVisit), eq(JobState.FAILURE),
				argThat(detail -> detail != null && detail.contains("browser unresponsive")), any(), any(), any(), any(),
				any());
		verify(jobService).updateJobState(eq(nextVisit), eq(JobState.SKIPPED),
				eq("browser unresponsive; session ended"), any(), any(), any(), any(), any());
		verifyNoInteractions(builder);
	}
}
