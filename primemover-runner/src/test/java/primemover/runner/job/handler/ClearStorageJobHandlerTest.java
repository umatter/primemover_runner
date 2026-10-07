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

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class ClearStorageJobHandlerTest {

	/** A driver that records the scripts it is asked to run and reports two entries cleared. */
	private interface ScriptingDriver extends WebDriver, JavascriptExecutor {
	}

	private final List<String> scripts = new ArrayList<>();

	private IJobAwareRunnerContext<?> contextFor(ClearStorageScope scope) {
		ScriptingDriver driver = mock(ScriptingDriver.class);
		when(driver.executeScript(anyString(), any(Object[].class))).thenAnswer(inv -> {
			scripts.add(inv.getArgument(0));
			return 2L;
		});
		ClearStorageJob job = new ClearStorageJob(1L, "Clear", "", scope);
		IJobAwareRunnerContext<?> ctx = mock(IJobAwareRunnerContext.class);
		when(ctx.getDriver()).thenReturn(driver);
		when(ctx.getJob()).thenAnswer(inv -> job);
		return ctx;
	}

	@Test
	@DisplayName("scope all clears both stores")
	void allClearsBoth() {
		new ClearStorageJobHandler().handle(contextFor(ClearStorageScope.ALL));
		assertThat(scripts).hasSize(2);
		assertThat(scripts.get(0)).contains("window.localStorage");
		assertThat(scripts.get(1)).contains("window.sessionStorage");
		assertThat(scripts).allSatisfy(s -> assertThat(s).contains(".clear()"));
	}

	@Test
	@DisplayName("scope local leaves sessionStorage alone, and vice versa")
	void narrowScopesTouchOneStore() {
		new ClearStorageJobHandler().handle(contextFor(ClearStorageScope.LOCAL));
		assertThat(scripts).hasSize(1);
		assertThat(scripts.get(0)).contains("window.localStorage");

		scripts.clear();
		new ClearStorageJobHandler().handle(contextFor(ClearStorageScope.SESSION));
		assertThat(scripts).hasSize(1);
		assertThat(scripts.get(0)).contains("window.sessionStorage");
	}

	@Test
	@DisplayName("scope none runs no script at all, so a control bot's storage is untouched")
	void noneIsARealNoOp() {
		// The control arm runs this job so that both arms have the same job count and timing.
		// It must therefore execute nothing -- if it cleared "just sessionStorage", the
		// control would itself be a treatment.
		new ClearStorageJobHandler().handle(contextFor(ClearStorageScope.NONE));
		assertThat(scripts).isEmpty();
	}

	@Test
	@DisplayName("a job with no scope fails loudly")
	void missingScopeFails() {
		assertThatThrownBy(() -> new ClearStorageJobHandler().handle(contextFor(null)))
				.isInstanceOf(IllegalStateException.class)
				.hasMessageContaining("no scope");
	}
}
