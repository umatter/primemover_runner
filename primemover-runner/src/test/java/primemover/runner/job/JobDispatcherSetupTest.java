package primemover.runner.job;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import org.springframework.beans.factory.config.AutowireCapableBeanFactory;
import org.springframework.context.ApplicationContext;
import org.springframework.test.util.ReflectionTestUtils;
import primemover.config.StartupMode;
import primemover.model.jobs.AssertProxyByOxylabsJob;
import primemover.model.jobs.ClearStorageJob;

import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * Which job types {@link JobDispatcher#setup()} registers in each startup mode. The assert jobs
 * are registered for DEVELOP and INTEGRATION only, behind an early return; a job a study uploads
 * must be registered above it, or a PRODUCTION runner fails every such job with "This type is
 * not yet supported". clear_storage shipped below the return and did exactly that on the VM
 * (experiment 199, 2026-10-11), while its handler tests passed.
 */
class JobDispatcherSetupTest {

	@SuppressWarnings("rawtypes")
	private static Map<Class, ?> handlersIn(StartupMode mode) {
		JobDispatcher dispatcher = new JobDispatcher();
		ReflectionTestUtils.setField(dispatcher, "mode", mode);
		ApplicationContext ctx = mock(ApplicationContext.class);
		when(ctx.getAutowireCapableBeanFactory()).thenReturn(mock(AutowireCapableBeanFactory.class));
		dispatcher.setApplicationContext(ctx);
		dispatcher.setup();
		@SuppressWarnings("unchecked")
		Map<Class, ?> dispatch = (Map<Class, ?>) ReflectionTestUtils.getField(dispatcher, "dispatch");
		return dispatch;
	}

	@ParameterizedTest
	@EnumSource(StartupMode.class)
	@DisplayName("clear_storage has a handler in every startup mode, PRODUCTION included")
	void clearStorageIsRegisteredInEveryMode(StartupMode mode) {
		assertThat(handlersIn(mode)).containsKey(ClearStorageJob.class);
	}

	@ParameterizedTest
	@EnumSource(value = StartupMode.class, names = {"DEVELOP", "INTEGRATION"})
	@DisplayName("the assert jobs stay registered for DEVELOP and INTEGRATION")
	void assertJobsInDevelopAndIntegration(StartupMode mode) {
		assertThat(handlersIn(mode)).containsKey(AssertProxyByOxylabsJob.class);
	}

	@ParameterizedTest
	@EnumSource(value = StartupMode.class, names = {"PRODUCTION"})
	@DisplayName("the assert jobs stay out of PRODUCTION")
	void noAssertJobsInProduction(StartupMode mode) {
		assertThat(handlersIn(mode)).doesNotContainKey(AssertProxyByOxylabsJob.class);
	}
}
