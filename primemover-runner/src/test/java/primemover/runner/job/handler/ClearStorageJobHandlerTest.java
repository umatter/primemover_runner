package primemover.runner.job.handler;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.openqa.selenium.JavascriptExecutor;
import org.openqa.selenium.WebDriver;
import primemover.model.jobs.ClearStorageJob;
import primemover.model.jobs.ClearStorageScope;
import primemover.runner.context.IJobAwareRunnerContext;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class ClearStorageJobHandlerTest {

	private interface ScriptingDriver extends WebDriver, JavascriptExecutor {
	}

	private final List<String> scripts = new ArrayList<>();
	private WebDriver.Options options;

	private IJobAwareRunnerContext<?> context(ClearStorageScope scope) {
		ScriptingDriver driver = mock(ScriptingDriver.class);
		options = mock(WebDriver.Options.class);
		when(driver.manage()).thenReturn(options);
		when(options.getCookies()).thenReturn(Set.of());
		when(driver.getCurrentUrl()).thenReturn("https://www.bing.com/");
		when(driver.executeScript(anyString(), any(Object[].class))).thenAnswer(i -> {
			scripts.add(i.getArgument(0));
			return 2L;
		});
		ClearStorageJob job = new ClearStorageJob(1L, "Clear", "", scope);
		IJobAwareRunnerContext<?> ctx = mock(IJobAwareRunnerContext.class);
		when(ctx.getDriver()).thenReturn(driver);
		when(ctx.getJob()).thenAnswer(i -> job);
		return ctx;
	}

	@Test
	@DisplayName("scope all clears the origin's cookies and both web stores")
	void allClearsCookiesAndWebStorage() {
		// Standard WebDriver throughout: Multilogin's driver does not implement CDP, so a
		// browser-wide clear is not available and this acts on the current origin.
		new ClearStorageJobHandler().handle(context(ClearStorageScope.ALL));

		verify(options).deleteAllCookies();
		assertThat(scripts).hasSize(2);
		assertThat(scripts.get(0)).contains("window.localStorage").contains(".clear()");
		assertThat(scripts.get(1)).contains("window.sessionStorage").contains(".clear()");
	}

	@Test
	@DisplayName("scope none touches neither cookies nor storage, so a control bot is untouched")
	void noneIsARealNoOp() {
		// The control arm runs this job, and the same preceding visits, so the arms differ in
		// what is cleared and never in where the bot went. It must therefore clear nothing --
		// a control that dropped "just the cookies" would itself be a treatment.
		new ClearStorageJobHandler().handle(context(ClearStorageScope.NONE));

		verify(options, never()).deleteAllCookies();
		assertThat(scripts).isEmpty();
	}

	@Test
	@DisplayName("the narrow scopes touch one store and leave cookies alone")
	void narrowScopesLeaveCookies() {
		new ClearStorageJobHandler().handle(context(ClearStorageScope.LOCAL));
		verify(options, never()).deleteAllCookies();
		assertThat(scripts).hasSize(1);
		assertThat(scripts.get(0)).contains("window.localStorage");

		scripts.clear();
		new ClearStorageJobHandler().handle(context(ClearStorageScope.SESSION));
		assertThat(scripts).hasSize(1);
		assertThat(scripts.get(0)).contains("window.sessionStorage");
	}

	@Test
	@DisplayName("a job with no scope fails loudly")
	void missingScopeFails() {
		assertThatThrownBy(() -> new ClearStorageJobHandler().handle(context(null)))
				.isInstanceOf(IllegalStateException.class)
				.hasMessageContaining("no scope");
	}
}
