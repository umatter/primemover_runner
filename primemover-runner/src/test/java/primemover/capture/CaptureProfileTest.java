package primemover.capture;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import primemover.api.model.Configuration;
import primemover.api.model.Preference;

import java.util.ArrayList;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * What a crawler's {@code capture_profile} preference turns on. The point of
 * the tests on the default is the promise to every other project on a shared
 * runner: a crawler that does not opt in stores exactly what it stored before.
 */
class CaptureProfileTest {

	private static Configuration config(Preference... prefs) {
		Configuration config = new Configuration();
		config.setPreferences(new ArrayList<>(List.of(prefs)));
		return config;
	}

	@Test
	@DisplayName("no configuration, no preferences, no capture_profile: the full capture of today")
	void defaultIsFull() {
		assertThat(CaptureProfile.of(null)).isEqualTo(CaptureProfile.FULL);
		assertThat(CaptureProfile.of(new Configuration())).isEqualTo(CaptureProfile.FULL);
		assertThat(CaptureProfile.of(config(new Preference("cookie_pref", "{\"accept_all\": true}"))))
				.isEqualTo(CaptureProfile.FULL);
	}

	@Test
	@DisplayName("the full profile records HAR, PNG, the separate page source, every session storage")
	void fullChangesNothing() {
		assertThat(CaptureProfile.FULL.recordsHar()).isTrue();
		assertThat(CaptureProfile.FULL.jpegScreenshot()).isFalse();
		assertThat(CaptureProfile.FULL.writesSeparatePageSource()).isTrue();
		assertThat(CaptureProfile.FULL.dedupesSessionStorage()).isFalse();
	}

	@Test
	@DisplayName("compact: no HAR, JPEG, page source once, session storage only when it changed")
	void compactCapabilities() {
		assertThat(CaptureProfile.COMPACT.recordsHar()).isFalse();
		assertThat(CaptureProfile.COMPACT.jpegScreenshot()).isTrue();
		assertThat(CaptureProfile.COMPACT.writesSeparatePageSource()).isFalse();
		assertThat(CaptureProfile.COMPACT.dedupesSessionStorage()).isTrue();
	}

	@Test
	@DisplayName("compact is read as a plain string, any case, or as a JSON string")
	void compactIsRead() {
		for (String value : new String[] { "compact", " COMPACT ", "\"compact\"" }) {
			assertThat(CaptureProfile.of(config(new Preference("capture_profile", value)))).as(value)
					.isEqualTo(CaptureProfile.COMPACT);
		}
		assertThat(CaptureProfile.of(config(new Preference(" Capture_Profile ", "compact"))))
				.isEqualTo(CaptureProfile.COMPACT);
	}

	@Test
	@DisplayName("an inactive preference, full, or an unknown value keep the full capture")
	void inactiveOrUnknownIsFull() {
		Preference inactive = new Preference("capture_profile", "compact");
		inactive.setActive(false);
		assertThat(CaptureProfile.of(config(inactive))).isEqualTo(CaptureProfile.FULL);
		assertThat(CaptureProfile.of(config(new Preference("capture_profile", "full"))))
				.isEqualTo(CaptureProfile.FULL);
		assertThat(CaptureProfile.of(config(new Preference("capture_profile", "tiny"))))
				.isEqualTo(CaptureProfile.FULL);
	}
}
