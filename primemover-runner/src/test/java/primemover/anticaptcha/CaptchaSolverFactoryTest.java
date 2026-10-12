package primemover.anticaptcha;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Which solver a session gets. The API solver is the default because it is the
 * only one that works on Multilogin X; the plugin stays selectable for plain
 * Chrome/Firefox, and a value that names neither must stop the runner rather
 * than quietly pick one.
 */
class CaptchaSolverFactoryTest {

	private static CaptchaSolverFactory factory(String solver) {
		CaptchaSolverFactory factory = new CaptchaSolverFactory();
		ReflectionTestUtils.setField(factory, "solver", solver);
		return factory;
	}

	@Test
	@DisplayName("api selects the API solver")
	void apiIsTheApiSolver() {
		assertThat(factory("api").newSolver()).isInstanceOf(ApiCaptchaSolver.class);
		assertThat(factory(" API ").newSolver()).isInstanceOf(ApiCaptchaSolver.class);
	}

	@Test
	@DisplayName("plugin selects the browser-extension solver")
	void pluginIsThePluginSolver() {
		assertThat(factory("plugin").newSolver()).isInstanceOf(PluginCaptchaSolver.class);
	}

	@Test
	@DisplayName("an unknown proxy mode stops the runner at startup; none and bot are accepted")
	void unknownProxyModeIsRefused() {
		CaptchaSolverFactory factory = factory("api");
		ReflectionTestUtils.setField(factory, "proxyMode", "residential");
		assertThatThrownBy(factory::validate).isInstanceOf(IllegalStateException.class)
				.hasMessageContaining("residential");

		ReflectionTestUtils.setField(factory, "proxyMode", "bot");
		factory.validate();
		ReflectionTestUtils.setField(factory, "proxyMode", " None ");
		factory.validate();
	}

	@Test
	@DisplayName("an unknown solver name stops the runner at startup")
	void unknownSolverIsRefused() {
		assertThatThrownBy(() -> factory("extension").validate()).isInstanceOf(IllegalStateException.class)
				.hasMessageContaining("extension");
	}
}
