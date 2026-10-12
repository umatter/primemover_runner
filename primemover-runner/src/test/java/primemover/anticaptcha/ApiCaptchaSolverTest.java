package primemover.anticaptcha;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.openqa.selenium.JavascriptExecutor;
import org.openqa.selenium.ScriptTimeoutException;
import org.openqa.selenium.WebDriver;
import primemover.anticaptcha.service.HostResolver;
import primemover.anticaptcha.service.TaskProxy;
import primemover.model.IJob;
import primemover.model.proxy.HttpProxy;
import primemover.model.proxy.IProxy;
import primemover.runner.context.IRunnerContext;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.mockito.Mockito.withSettings;

/**
 * The solve flow against a scripted browser: what the page looks like before and
 * after each step, what Anti-Captcha answers, and what the page's verify call
 * says. Nothing here may throw out of {@code solve} -- a challenge that could
 * not be solved is a result, not an exception.
 */
class ApiCaptchaSolverTest {

	private static final String BING_URL = "https://www.bing.com/search?q=food+stamps&form=QBLH";
	private static final String VERIFY_ENDPOINT = "https://www.bing.com/challenge/verify?partner=7&token=";
	/** What Anti-Captcha is told: the page, without the bot's search query. */
	private static final String WEBSITE_URL = "https://www.bing.com/search";

	private static String challengePage;
	private static String ordinaryPage;

	private FakeTokenSource service;
	private WebDriver driver;
	private WebDriver.Navigation navigation;
	private IRunnerContext<?> context;
	private IJob job;

	/** Anti-Captcha, scripted: answers in order, then the fallback; every call recorded. */
	private static final class FakeTokenSource implements TurnstileTokenSource {
		final List<List<String>> calls = new ArrayList<>();
		final Deque<Optional<String>> answers = new ArrayDeque<>();
		final List<TaskProxy> proxies = new ArrayList<>();
		Optional<String> fallback = Optional.empty();

		@Override
		public Optional<String> solveTurnstile(String websiteUrl, String websiteKey, String action, String cData,
				TaskProxy proxy) {
			calls.add(List.of(websiteUrl, websiteKey, action, cData));
			proxies.add(proxy);
			return answers.isEmpty() ? fallback : answers.poll();
		}
	}

	private static String fixture(String name) throws IOException {
		try (InputStream in = ApiCaptchaSolverTest.class.getResourceAsStream("/challenge/" + name)) {
			assertThat(in).as("fixture " + name).isNotNull();
			return new String(in.readAllBytes(), StandardCharsets.UTF_8);
		}
	}

	@BeforeEach
	void setUp() throws IOException {
		if (challengePage == null) {
			challengePage = fixture("bing-turnstile-exp199-job715588.html");
			ordinaryPage = fixture("bing-serp-ordinary-exp199-job715553.html");
		}

		service = new FakeTokenSource();
		driver = mock(WebDriver.class, withSettings().extraInterfaces(JavascriptExecutor.class));
		navigation = mock(WebDriver.Navigation.class);
		when(driver.navigate()).thenReturn(navigation);
		when(driver.getCurrentUrl()).thenReturn(BING_URL);
		context = mock(IRunnerContext.class);
		when(context.getDriver()).thenReturn(driver);
		job = mock(IJob.class);
		when(job.getId()).thenReturn(715588L);
	}

	private Object verifyScriptCall(String token, String action) {
		return ((JavascriptExecutor) verify(driver)).executeAsyncScript(anyString(), eq(VERIFY_ENDPOINT), eq(token),
				eq(action), eq("7"));
	}

	@Test
	@DisplayName("no challenge on the page: nothing is paid for and the page is left alone")
	void noChallengeCostsNothing() {
		when(driver.getPageSource()).thenReturn(ordinaryPage);

		CaptchaSolverResult result = new ApiCaptchaSolver(service, true).solve(context, job, true);

		assertThat(result.isCaptchaDetected()).isFalse();
		assertThat(result.isCaptchaSolved()).isFalse();
		assertThat(service.calls).isEmpty();
		verify(navigation, never()).refresh();
	}

	@Test
	@DisplayName("a Bing challenge is solved: token for Bing's widget, Bing's verify call, reload, results page")
	void solvesTheBingChallenge() {
		when(driver.getPageSource()).thenReturn(challengePage, ordinaryPage);
		service.fallback = Optional.of("0.token");
		when(((JavascriptExecutor) driver).executeAsyncScript(anyString(), any(), any(), any(), any()))
				.thenReturn(Map.of("status", 200L, "success", true));

		CaptchaSolverResult result = new ApiCaptchaSolver(service, true).solve(context, job, true);

		assertThat(result.isCaptchaDetected()).isTrue();
		assertThat(result.isCaptchaSolved()).isTrue();
		assertThat(result.hasError()).isFalse();
		assertThat(result.getCaptchaType()).isEqualTo(ChallengePageDetector.BING_TURNSTILE_LABEL);
		assertThat(service.calls).containsExactly(
				List.of(WEBSITE_URL, "0x4AAAAAABgNScUsbCFCjwIZ", "CC7527", "AEE96B5FD39F4A399BEB7847BE9FD66C"));
		assertThat(service.proxies).containsExactly((TaskProxy) null);
		verifyScriptCall("0.token", "CC7527");
		verify(navigation).refresh();
	}

	private ApiCaptchaSolver botProxySolver(Optional<IProxy> upstream) {
		return new ApiCaptchaSolver(service, true, TurnstileProxyMode.BOT, ctx -> upstream, HostResolver.SYSTEM);
	}

	@Test
	@DisplayName("proxy mode bot: the token is solved through the session's own upstream proxy")
	void botModeSolvesThroughTheUpstream() {
		when(driver.getPageSource()).thenReturn(challengePage, ordinaryPage);
		service.fallback = Optional.of("0.token");
		when(((JavascriptExecutor) driver).executeAsyncScript(anyString(), any(), any(), any(), any()))
				.thenReturn(Map.of("status", 200L, "success", true));

		CaptchaSolverResult result = botProxySolver(
				Optional.of(new HttpProxy("185.91.116.40", 8080, "user-sess-1", "pw"))).solve(context, job, true);

		assertThat(result.isCaptchaSolved()).isTrue();
		assertThat(service.proxies).hasSize(1);
		TaskProxy proxy = service.proxies.get(0);
		assertThat(proxy.getAddress()).isEqualTo("185.91.116.40");
		assertThat(proxy.getPort()).isEqualTo(8080);
		assertThat(proxy.getLogin()).isEqualTo("user-sess-1");
	}

	@Test
	@DisplayName("proxy mode bot without an upstream proxy: nothing is bought, never a silent proxyless solve")
	void botModeWithoutUpstreamBuysNothing() {
		when(driver.getPageSource()).thenReturn(challengePage);

		CaptchaSolverResult result = botProxySolver(Optional.empty()).solve(context, job, true);

		assertThat(result.isCaptchaDetected()).isTrue();
		assertThat(result.isCaptchaSolved()).isFalse();
		assertThat(result.hasError()).isTrue();
		assertThat(service.calls).isEmpty();
	}

	@Test
	@DisplayName("proxy mode bot with a local upstream (one Anti-Captcha cannot reach): nothing is bought")
	void botModeWithLocalUpstreamBuysNothing() {
		when(driver.getPageSource()).thenReturn(challengePage);

		CaptchaSolverResult result = botProxySolver(Optional.of(new HttpProxy("127.0.0.1", 8888, "u", "p")))
				.solve(context, job, true);

		assertThat(result.isCaptchaSolved()).isFalse();
		assertThat(result.hasError()).isTrue();
		assertThat(service.calls).isEmpty();
	}

	@Test
	@DisplayName("no token from Anti-Captcha: not solved, and the page is not touched")
	void noTokenIsNotSolved() {
		when(driver.getPageSource()).thenReturn(challengePage);

		CaptchaSolverResult result = new ApiCaptchaSolver(service, true).solve(context, job, true);

		assertThat(result.isCaptchaDetected()).isTrue();
		assertThat(result.isCaptchaSolved()).isFalse();
		assertThat(result.hasError()).isTrue();
		verify((JavascriptExecutor) driver, never()).executeAsyncScript(anyString(), any(), any(), any(), any());
		verify(navigation, never()).refresh();
	}

	@Test
	@DisplayName("a challenge that survives the reload is tried again with the new page's parameters, then given up")
	void persistingChallengeIsRetriedThenGivenUp() {
		String secondChallenge = challengePage.replace("AEE96B5FD39F4A399BEB7847BE9FD66C",
				"BBBBBBBBBBBBBBBBBBBBBBBBBBBBBBBB");
		when(driver.getPageSource()).thenReturn(challengePage, secondChallenge, secondChallenge);
		service.fallback = Optional.of("0.token");
		when(((JavascriptExecutor) driver).executeAsyncScript(anyString(), any(), any(), any(), any()))
				.thenReturn(Map.of("status", 200L, "success", false));

		CaptchaSolverResult result = new ApiCaptchaSolver(service, true).solve(context, job, true);

		assertThat(result.isCaptchaDetected()).isTrue();
		assertThat(result.isCaptchaSolved()).isFalse();
		assertThat(service.calls).containsExactly(
				List.of(WEBSITE_URL, "0x4AAAAAABgNScUsbCFCjwIZ", "CC7527", "AEE96B5FD39F4A399BEB7847BE9FD66C"),
				List.of(WEBSITE_URL, "0x4AAAAAABgNScUsbCFCjwIZ", "CC7527", "BBBBBBBBBBBBBBBBBBBBBBBBBBBBBBBB"));
		verify(navigation, times(2)).refresh();
	}

	@Test
	@DisplayName("a session Bing will not let through stops buying tokens after four in a row fail")
	void unsuccessfulTokensAreCappedPerSession() {
		when(driver.getPageSource()).thenReturn(challengePage);
		service.fallback = Optional.of("0.token");
		when(((JavascriptExecutor) driver).executeAsyncScript(anyString(), any(), any(), any(), any()))
				.thenReturn(Map.of("status", 200L, "success", false));
		ApiCaptchaSolver solver = new ApiCaptchaSolver(service, true);

		solver.solve(context, job, true);
		solver.solve(context, job, true);
		CaptchaSolverResult third = solver.solve(context, job, true);

		assertThat(service.calls).hasSize(4);
		assertThat(third.isCaptchaDetected()).isTrue();
		assertThat(third.isCaptchaSolved()).isFalse();
	}

	@Test
	@DisplayName("a failing verify script is an unsolved attempt, not an exception")
	void scriptErrorIsNotAnException() {
		when(driver.getPageSource()).thenReturn(challengePage);
		service.fallback = Optional.of("0.token");
		when(((JavascriptExecutor) driver).executeAsyncScript(anyString(), any(), any(), any(), any()))
				.thenThrow(new ScriptTimeoutException("script timeout"));

		CaptchaSolverResult result = new ApiCaptchaSolver(service, true).solve(context, job, true);

		assertThat(result.isCaptchaDetected()).isTrue();
		assertThat(result.isCaptchaSolved()).isFalse();
	}

	@Test
	@DisplayName("a challenge the runner cannot solve (Google's sorry page) is reported, never paid for")
	void unsupportedChallengeIsNotPaidFor() {
		when(driver.getCurrentUrl()).thenReturn("https://www.google.com/sorry/index?continue=x");
		when(driver.getPageSource()).thenReturn("<html><body>sorry</body></html>");

		CaptchaSolverResult result = new ApiCaptchaSolver(service, true).solve(context, job, true);

		assertThat(result.isCaptchaDetected()).isTrue();
		assertThat(result.isCaptchaSolved()).isFalse();
		assertThat(result.getCaptchaType()).isEqualTo("google sorry page (unusual traffic)");
		assertThat(service.calls).isEmpty();
	}

	@Test
	@DisplayName("disabled: the browser is not even looked at")
	void disabledTouchesNothing() {
		CaptchaSolverResult result = new ApiCaptchaSolver(service, false).solve(context, job, true);

		assertThat(result.isPluginEnabled()).isFalse();
		assertThat(service.calls).isEmpty();
		verifyNoInteractions(driver);
	}
}
