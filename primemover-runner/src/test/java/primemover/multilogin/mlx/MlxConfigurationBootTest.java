package primemover.multilogin.mlx;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.config.ConfigurableListableBeanFactory;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * The runner must boot without a Multilogin account.
 *
 * {@link MlxConfiguration#authTokenProvider()} signs in to Multilogin's cloud
 * inside the bean factory method. While those beans were eager and
 * unconditional, a blank {@code multilogin.apiPassword} -- a fresh checkout, a
 * developer machine, an expired account -- failed Spring's context refresh and
 * the JVM died at startup, regardless of which browser any crawler on that box
 * actually used. Two things fix it and both are asserted here: the configuration
 * is conditional on {@code multilogin.enabled}, and its beans are {@code @Lazy}
 * so the sign-in happens on first use rather than during refresh.
 *
 * Scoped to {@link MlxConfiguration} with an {@link ApplicationContextRunner}
 * rather than booting the whole application: a full context also wants the
 * outlets provider's network refresh against the backend API (an init method
 * with no catch), which no CI runner can serve. Same reasoning as
 * {@code RecorderScopeTest}. The credentials below are blank on purpose -- that
 * is the condition under which master fails.
 */
class MlxConfigurationBootTest {

	// No AgentService bean is registered, and none is needed: every bean here is
	// lazy, so nothing resolves a dependency during refresh. That is precisely
	// the property under test.
	private final ApplicationContextRunner runner = new ApplicationContextRunner()
			.withUserConfiguration(MlxConfiguration.class)
			.withPropertyValues(
					"multilogin.apiUser=",
					"multilogin.apiPassword=",
					"multilogin.defaultFolderId=");

	@Test
	@DisplayName("multilogin.enabled=false: context starts and no MLX bean exists")
	void contextStartsWithMultiloginDisabled() {
		runner.withPropertyValues("multilogin.enabled=false").run(context -> {
			assertThat(context)
					.as("a runner with no Multilogin account must still boot")
					.hasNotFailed();
			assertThat(context).doesNotHaveBean(MlxConfiguration.class);
			assertThat(context).doesNotHaveBean(AuthTokenProvider.class);
			assertThat(context).doesNotHaveBean(ProfileService.class);
			assertThat(context).doesNotHaveBean(ContextBuilder.class);
		});
	}

	@Test
	@DisplayName("default (enabled): the MLX beans exist but nothing signs in at refresh")
	void mlxBeansExistButAreNotCreatedAtStartup() {
		runner.run(context -> {
			assertThat(context)
					.as("the default must keep the MLX beans available")
					.hasNotFailed();
			assertThat(context).hasSingleBean(MlxConfiguration.class);

			ConfigurableListableBeanFactory beanFactory =
					context.getSourceApplicationContext().getBeanFactory();

			assertThat(beanFactory.getBeanNamesForType(AuthTokenProvider.class))
					.as("the token provider must still be DEFINED when Multilogin is enabled")
					.containsExactly("MlxAuthTokenProvider");

			assertThat(beanFactory.getBeanDefinition("MlxAuthTokenProvider").isLazyInit())
					.as("MlxConfiguration is @Lazy, so its beans must be lazy-init")
					.isTrue();

			// The behavioural half of the claim: the bean was never instantiated
			// during refresh, so authenticate() -- which is called inside the
			// factory method -- was never reached.
			for (String lazyBean : new String[] { "MlxAuthTokenProvider", "MlxMultiloginApi", "MlxLocalApi",
					"MlxContextBuilder" }) {
				assertThat(beanFactory.containsSingleton(lazyBean))
						.as("%s must not be instantiated at context refresh (it signs in / schedules)", lazyBean)
						.isFalse();
			}
		});
	}
}
