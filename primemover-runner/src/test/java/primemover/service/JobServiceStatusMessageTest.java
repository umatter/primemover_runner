package primemover.service;

import org.junit.jupiter.api.Test;
import primemover.model.JobState;

import java.nio.charset.StandardCharsets;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * The status_message the runner puts on the wire.
 *
 * <p>
 * Until 2026-08-27 it was always one of ~7 fixed enum strings, so every failed
 * job in the database read exactly "error during execution" while the real cause
 * (verified live: {@code WebDriverException: tab crashed} on job 3828) was
 * logged locally and thrown away.
 */
class JobServiceStatusMessageTest {

	@Test
	void noDetailKeepsTheBareStateText() {
		assertEquals("error during execution", JobService.buildStatusMessage(JobState.FAILURE.getText(), null));
		assertEquals("error during execution", JobService.buildStatusMessage(JobState.FAILURE.getText(), ""));
		assertEquals("error during execution", JobService.buildStatusMessage(JobState.FAILURE.getText(), "   "));
	}

	@Test
	void detailIsAppendedAfterADash() {
		assertEquals("error during execution - WebDriverException: tab crashed",
				JobService.buildStatusMessage(JobState.FAILURE.getText(), "WebDriverException: tab crashed"));
	}

	@Test
	void detailIsCollapsedOntoASingleLine() {
		String message = JobService.buildStatusMessage(JobState.FAILURE.getText(),
				"WebDriverException: tab crashed\n\tat org.openqa.selenium.Foo\r\n\tat Bar");

		assertFalse(message.contains("\n"), message);
		assertFalse(message.contains("\r"), message);
		assertFalse(message.contains("\t"), message);
		assertEquals("error during execution - WebDriverException: tab crashed at org.openqa.selenium.Foo at Bar",
				message);
	}

	@Test
	void longDetailIsTruncatedWithAMarker() {
		String message = JobService.buildStatusMessage(JobState.FAILURE.getText(), "x".repeat(5000));

		assertEquals(JobService.MAX_STATUS_MESSAGE_LENGTH, message.length());
		assertTrue(message.startsWith("error during execution - x"), message.substring(0, 40));
		assertTrue(message.endsWith("..."), message.substring(message.length() - 10));
	}

	@Test
	void aDetailThatFitsExactlyIsNotTruncated() {
		String prefix = JobState.FAILURE.getText() + " - ";
		String detail = "y".repeat(JobService.MAX_STATUS_MESSAGE_LENGTH - prefix.length());

		String message = JobService.buildStatusMessage(JobState.FAILURE.getText(), detail);

		assertEquals(JobService.MAX_STATUS_MESSAGE_LENGTH, message.length());
		assertFalse(message.endsWith("..."), "exact fit must not be truncated");
	}

	/** A grinning face: U+1F600, two UTF-16 chars. */
	private static final String EMOJI = "\uD83D\uDE00";

	@Test
	void truncationNeverSplitsANonBmpCharacter() {
		// Place the emoji so that its two halves straddle the cut exactly: the high
		// surrogate is the last character kept, the low surrogate the first dropped.
		// A video title arriving from YouTube in a failed job's message is
		// attacker-supplied text, so this boundary is a question of when, not whether.
		String prefix = JobState.FAILURE.getText() + " - ";
		int cut = JobService.MAX_STATUS_MESSAGE_LENGTH - 3;
		String detail = "a".repeat(cut - 1 - prefix.length()) + EMOJI + "b".repeat(300);

		String message = JobService.buildStatusMessage(JobState.FAILURE.getText(), detail);

		assertFalse(hasLoneSurrogate(message),
				"truncation split the emoji into a lone surrogate, which reaches the API as '?'");
		// what the wire actually does to a lone surrogate: UTF-8 has no encoding for
		// it, so it is replaced. Round-tripping is the honest test of the symptom.
		assertEquals(message, new String(message.getBytes(StandardCharsets.UTF_8), StandardCharsets.UTF_8));
		assertTrue(message.endsWith("..."), "the truncation marker is still appended");
		// one char shorter than the cap, because the cut moved back off the pair
		assertEquals(JobService.MAX_STATUS_MESSAGE_LENGTH - 1, message.length());
	}

	@Test
	void anEmojiThatFitsIsKeptWhole() {
		String prefix = JobState.FAILURE.getText() + " - ";
		int cut = JobService.MAX_STATUS_MESSAGE_LENGTH - 3;
		// emoji ends two characters before the cut, so both halves survive
		String detail = "a".repeat(cut - 4 - prefix.length()) + EMOJI + "b".repeat(300);

		String message = JobService.buildStatusMessage(JobState.FAILURE.getText(), detail);

		assertTrue(message.contains(EMOJI), "a character that fits must not be dropped");
		assertFalse(hasLoneSurrogate(message));
		assertEquals(JobService.MAX_STATUS_MESSAGE_LENGTH, message.length());
	}

	private static boolean hasLoneSurrogate(String value) {
		for (int i = 0; i < value.length(); i++) {
			char c = value.charAt(i);
			if (Character.isHighSurrogate(c)) {
				if (i + 1 >= value.length() || !Character.isLowSurrogate(value.charAt(i + 1))) {
					return true;
				}
				i++;
			} else if (Character.isLowSurrogate(c)) {
				return true;
			}
		}
		return false;
	}
}
