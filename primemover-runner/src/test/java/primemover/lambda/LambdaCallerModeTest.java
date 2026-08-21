package primemover.lambda;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.test.util.ReflectionTestUtils;

import java.lang.reflect.Field;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * How {@code lambda.mode} is resolved, and what happens when it is not what anyone
 * expected.
 *
 * <p>Both behaviours pinned here exist because the failure modes are not symmetric.
 * Decisions answered by the local server are visibly wrong when the server is down --
 * the call fails. Decisions answered by AWS look like success whatever the image
 * behind them contains, and the image currently deployed there predates both the
 * camelCase-selector and the aria-hidden click-target fixes. So the cost of
 * accidentally choosing AWS is silently wrong research data, while the cost of
 * accidentally choosing local is an obvious outage.
 *
 * <p>The property is set explicitly in {@code config/application.properties}; these
 * tests cover what happens when it is not.
 */
class LambdaCallerModeTest {

	private static String valueAnnotationOf(String fieldName) throws NoSuchFieldException {
		Field field = LambdaCaller.class.getDeclaredField(fieldName);
		Value annotation = field.getAnnotation(Value.class);
		assertThat(annotation)
				.as("%s should be injected via @Value", fieldName)
				.isNotNull();
		return annotation.value();
	}

	@Test
	@DisplayName("an absent lambda.mode resolves to local, not aws")
	void defaultsToLocal() throws Exception {
		// Asserting on the annotation rather than booting a context: the point is the
		// literal default baked into the placeholder, which is what a deployment gets
		// when the properties line goes missing.
		assertThat(valueAnnotationOf("lambdaMode")).isEqualTo("${lambda.mode:local}");
	}

	@ParameterizedTest
	@ValueSource(strings = {"locl", "remote", "", " ", "true", "AWS_LAMBDA"})
	@DisplayName("an unrecognised lambda.mode is an error, never a quiet fallback to aws")
	void unknownModeThrows(String mode) {
		LambdaCaller caller = new LambdaCaller();
		ReflectionTestUtils.setField(caller, "lambdaMode", mode);

		assertThatThrownBy(() -> caller.invoke("bot-decision-yt", "{}"))
				.isInstanceOf(LambdaException.class)
				.hasMessageContaining("unknown lambda.mode")
				.hasMessageContaining(mode);
	}

	@ParameterizedTest
	@ValueSource(strings = {"local", "LOCAL", "Local", "aws", "AWS"})
	@DisplayName("both modes are recognised case-insensitively")
	void knownModesAreRecognised(String mode) {
		LambdaCaller caller = new LambdaCaller();
		ReflectionTestUtils.setField(caller, "lambdaMode", mode);
		// Point the local client at a closed port so the call cannot succeed or hang;
		// what matters is only WHICH failure comes back.
		ReflectionTestUtils.setField(caller, "localLambdaUrl", "http://127.0.0.1:1/invoke");

		// These modes reach a real client and will fail -- no local server, no AWS
		// credentials. The assertion is that they fail for that reason rather than
		// being rejected as unrecognised configuration.
		try {
			caller.invoke("bot-decision-yt", "{}");
		} catch (Exception e) {
			assertThat(String.valueOf(e.getMessage()))
					.as("%s should be accepted as a mode", mode)
					.doesNotContain("unknown lambda.mode");
		}
	}
}
