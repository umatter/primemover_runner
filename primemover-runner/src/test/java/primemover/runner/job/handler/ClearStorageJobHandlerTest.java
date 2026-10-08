package primemover.runner.job.handler;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.openqa.selenium.JavascriptExecutor;
import org.openqa.selenium.WebDriver;
import org.openqa.selenium.chromium.HasCdp;
import primemover.model.jobs.ClearStorageJob;
import primemover.model.jobs.ClearStorageScope;
import primemover.runner.context.IJobAwareRunnerContext;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class ClearStorageJobHandlerTest {

	/** A driver that speaks both JS and CDP, recording what it is asked to do. */
	private interface CdpDriver extends WebDriver, JavascriptExecutor, HasCdp {
	}

	private final List<String> scripts = new ArrayList<>();
	private final List<String> cdpCalls = new ArrayList<>();

	private IJobAwareRunnerContext<?> context(ClearStorageScope scope, List<String> origins, boolean cdp) {
		WebDriver driver;
		if (cdp) {
			CdpDriver d = mock(CdpDriver.class);
			when(d.executeScript(anyString(), any(Object[].class))).thenAnswer(i -> {
				scripts.add(i.getArgument(0));
				return 2L;
			});
			when(d.executeCdpCommand(anyString(), any())).thenAnswer(i -> {
				Map<String, Object> args = i.getArgument(1);
				cdpCalls.add(i.getArgument(0) + " " + args);
				return Map.of();
			});
			driver = d;
		} else {
			driver = mock(WebDriver.class);
		}
		ClearStorageJob job = new ClearStorageJob(1L, "Clear", "", scope, origins);
		IJobAwareRunnerContext<?> ctx = mock(IJobAwareRunnerContext.class);
		when(ctx.getDriver()).thenReturn(driver);
		when(ctx.getJob()).thenAnswer(i -> job);
		return ctx;
	}

	@Test
	@DisplayName("scope all clears cookies and cache browser-wide, with no page loaded")
	void allClearsCookiesAndCache() {
		// The job runs at the head of a session, on about:blank. A JavaScript clear there
		// would address nothing, so the whole scope goes through CDP, whose cookie and cache
		// commands take no origin and need no page.
		new ClearStorageJobHandler().handle(context(ClearStorageScope.ALL, List.of(), true));

		assertThat(cdpCalls).anySatisfy(c -> assertThat(c).startsWith("Network.clearBrowserCookies"));
		assertThat(cdpCalls).anySatisfy(c -> assertThat(c).startsWith("Network.clearBrowserCache"));
		assertThat(scripts).as("no JavaScript, which would hit about:blank").isEmpty();
	}

	@Test
	@DisplayName("scope all clears every storage bucket of each named origin")
	void allClearsSiteDataPerOrigin() {
		new ClearStorageJobHandler().handle(context(ClearStorageScope.ALL,
				List.of("https://www.bing.com", "https://search.yahoo.com"), true));

		List<String> siteData = cdpCalls.stream().filter(c -> c.startsWith("Storage.clearDataForOrigin")).toList();
		assertThat(siteData).hasSize(2);
		assertThat(siteData.get(0)).contains("https://www.bing.com").contains("storageTypes=all");
		assertThat(siteData.get(1)).contains("https://search.yahoo.com");
	}

	@Test
	@DisplayName("scope none does nothing at all, so a control bot is untouched")
	void noneIsARealNoOp() {
		// The control arm runs this job so both arms have the same job count and timing. It
		// must execute nothing -- a control that cleared "just the cache" is a treatment.
		new ClearStorageJobHandler().handle(context(ClearStorageScope.NONE, List.of(), true));
		assertThat(cdpCalls).isEmpty();
		assertThat(scripts).isEmpty();
	}

	@Test
	@DisplayName("the narrow scopes still use JavaScript on the current origin")
	void narrowScopesUseJavascript() {
		new ClearStorageJobHandler().handle(context(ClearStorageScope.LOCAL, List.of(), true));
		assertThat(scripts).hasSize(1);
		assertThat(scripts.get(0)).contains("window.localStorage").contains(".clear()");
		assertThat(cdpCalls).isEmpty();
	}

	@Test
	@DisplayName("a driver that cannot speak CDP fails the job instead of leaving a bot untreated")
	void noCdpFailsLoudly() {
		// Silently skipping would record a bot as treated while it kept every cookie -- the
		// untreated-but-recorded-as-treated failure the whole privacy factor guards against.
		assertThatThrownBy(() -> new ClearStorageJobHandler()
				.handle(context(ClearStorageScope.ALL, List.of(), false)))
				.isInstanceOf(IllegalStateException.class)
				.hasMessageContaining("CDP");
	}

	@Test
	@DisplayName("a job with no scope fails loudly")
	void missingScopeFails() {
		assertThatThrownBy(() -> new ClearStorageJobHandler().handle(context(null, List.of(), true)))
				.isInstanceOf(IllegalStateException.class)
				.hasMessageContaining("no scope");
	}
}
