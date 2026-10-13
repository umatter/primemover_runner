package primemover.runner;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import primemover.anticaptcha.CaptchaSolverResult;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * What a job reports about the challenges its solver met: whether the page it
 * ended on was got past (which decides the challenge warning), and the
 * {@code captcha_solved} the API stores. Pure functions over the before/after
 * results, for the same reason as {@code challengeWarning}.
 */
class TaskOperatorCaptchaReportTest {

	private static final CaptchaSolverResult NONE = CaptchaSolverResult._NOCAPTCHADETECTED;
	private static final CaptchaSolverResult SOLVED = CaptchaSolverResult.of(true, true, true, true, 20, false,
			false, "bing challenge page (turnstile)");
	private static final CaptchaSolverResult UNSOLVED = CaptchaSolverResult.of(true, true, true, false, 40, false,
			false, "bing challenge page (turnstile)");

	@Test
	@DisplayName("a challenge solved before the job does not excuse one the job ended on")
	void solvedBeforeDoesNotCoverTheFinalPage() {
		CaptchaSolverResultHolder results = new CaptchaSolverResultHolder(SOLVED, UNSOLVED);

		assertThat(TaskOperator.solvedOnFinalPage(results)).isFalse();
		assertThat(TaskOperator.captchaSolvedReport(results)).isFalse();
	}

	@Test
	@DisplayName("a challenge solved after the job means the final page was got past")
	void solvedAfter() {
		CaptchaSolverResultHolder results = new CaptchaSolverResultHolder(NONE, SOLVED);

		assertThat(TaskOperator.solvedOnFinalPage(results)).isTrue();
		assertThat(TaskOperator.captchaSolvedReport(results)).isTrue();
	}

	@Test
	@DisplayName("a challenge solved before the job, and none after, is reported solved")
	void solvedBeforeOnly() {
		CaptchaSolverResultHolder results = new CaptchaSolverResultHolder(SOLVED, NONE);

		assertThat(TaskOperator.captchaSolvedReport(results)).isTrue();
		assertThat(TaskOperator.solvedOnFinalPage(results)).isFalse();
	}

	@Test
	@DisplayName("no challenge met, or no solver run: captcha_solved stays unknown")
	void noChallengeIsUnknown() {
		assertThat(TaskOperator.captchaSolvedReport(new CaptchaSolverResultHolder(NONE, NONE))).isNull();
		assertThat(TaskOperator.captchaSolvedReport(new CaptchaSolverResultHolder())).isNull();
		assertThat(TaskOperator.captchaSolvedReport(null)).isNull();
		assertThat(TaskOperator.solvedOnFinalPage(null)).isFalse();
	}
}
