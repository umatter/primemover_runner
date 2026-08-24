package primemover.config;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.context.annotation.Scope;

import java.lang.reflect.Method;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * The recorder beans must be prototype-scoped: one recorder per session.
 *
 * As singletons, every concurrent session shared one BrowserUpRecorderProxy and
 * raced on its single {@code proxy} field. Measured on 2026-08-24 (experiment
 * 107, eight simultaneous launches): three of eight sessions failed setup in the
 * same millisecond with "Proxy server is already started" and an NPE on a
 * mid-swap read. The sharing also leaked a proxy server on every concurrent
 * overwrite, and allowed one session's teardown to close another session's live
 * recorder. The bug was latent for five years because
 * runner.MaxParallelExecution was 2.
 *
 * A full Spring context test would need the whole application environment
 * (Multilogin credentials, the outlets provider's network refresh), so this
 * pins the scope structurally instead: cheap, hermetic, and it fails the moment
 * someone removes the annotation.
 */
class RecorderScopeTest {

	@Test
	@DisplayName("both RecordingProxy bean variants are prototype-scoped")
	void recorderBeansArePrototypeScoped() throws Exception {
		for (String factory : new String[] { "getBrowserUpRecorderProxy", "getNoRecordingProxy" }) {
			Method m = Config.class.getDeclaredMethod(factory);
			Scope scope = m.getAnnotation(Scope.class);
			assertThat(scope)
					.as("%s must carry @Scope(\"prototype\") -- see class javadoc for the incident", factory)
					.isNotNull();
			assertThat(scope.value())
					.as("%s scope", factory)
					.isEqualTo("prototype");
		}
	}
}
