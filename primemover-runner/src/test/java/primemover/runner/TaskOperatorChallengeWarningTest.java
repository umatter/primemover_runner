package primemover.runner;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * The rule that turns a detected challenge page into a job warning. Extracted as
 * a pure function for the same reason as {@code buildChromeOptions}: asserting it
 * through {@code runJobInContext} would need a browser, a display and a Spring
 * context.
 */
class TaskOperatorChallengeWarningTest {

	@Test
	@DisplayName("an unsolved challenge page produces the warning, verbatim")
	void unsolvedChallengeWarns() {
		assertThat(TaskOperator.challengeWarning("https://www.google.com/sorry/index", null, false))
				.contains("challenge page: google sorry page (unusual traffic), not solved");
	}

	@Test
	@DisplayName("a solved captcha suppresses the warning")
	void solvedChallengeIsSilent() {
		assertThat(TaskOperator.challengeWarning("https://www.google.com/sorry/index", null, true)).isEmpty();
	}

	@Test
	@DisplayName("an ordinary page never warns, solved or not")
	void ordinaryPageIsSilent() {
		String source = "<html><body><div class=\"grecaptcha-badge\"></div></body></html>";

		assertThat(TaskOperator.challengeWarning("https://news.example.com/", source, false)).isEmpty();
		assertThat(TaskOperator.challengeWarning("https://news.example.com/", source, true)).isEmpty();
	}
}
