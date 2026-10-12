package primemover.capture;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import primemover.api.model.Configuration;
import primemover.api.model.Preference;

import javax.annotation.Nullable;
import java.util.Locale;

/**
 * How much of each job a crawler stores, from its {@code capture_profile}
 * configuration preference.
 *
 * <p>
 * {@link #FULL} is what every crawler stored before this existed and what every
 * crawler without the preference still stores, so no project on a shared runner
 * changes unless its spec opts in. {@link #COMPACT} keeps every interaction --
 * page source, screenshot, cookies, local and session storage -- and stores it
 * smaller:
 * <ul>
 * <li>no HAR (the recorder stays in the path; only the capture is skipped);</li>
 * <li>the screenshot as JPEG instead of PNG, which inside a ZIP does not shrink at
 * all;</li>
 * <li>the page source once, in the job-data ZIP, not again as a separate
 * {@code page-finalsource} object;</li>
 * <li>session storage only when it changed: a job whose session storage is
 * byte-identical to the last one stored in full gets a pointer to that job
 * instead. On Bing it is a ~1.8 MB blob that repeated in 81% of the bytes of 34
 * consecutive jobs measured on 2026-10-12.</li>
 * </ul>
 *
 * <p>
 * The value is read as a plain string or a JSON string, in any case; an
 * inactive preference, {@code full}, or a value this runner does not know keeps
 * {@link #FULL} -- the error that stores more, never the one that stores less.
 */
public enum CaptureProfile {
	FULL, COMPACT;

	private static final Logger LOGGER = LoggerFactory.getLogger(CaptureProfile.class);

	public static final String PREFERENCE = "capture_profile";

	public static CaptureProfile of(@Nullable Configuration rawConfiguration) {
		if (rawConfiguration == null || rawConfiguration.getPreferences() == null) {
			return FULL;
		}

		for (Preference preference : rawConfiguration.getPreferences()) {
			if (preference == null || preference.getName() == null
					|| !PREFERENCE.equalsIgnoreCase(preference.getName().strip())
					|| Boolean.FALSE.equals(preference.getActive())) {
				continue;
			}

			String value = preference.getValue() == null ? "" : preference.getValue().strip();
			if (value.length() >= 2 && value.startsWith("\"") && value.endsWith("\"")) {
				value = value.substring(1, value.length() - 1).strip();
			}
			switch (value.toLowerCase(Locale.ROOT)) {
			case "compact":
				return COMPACT;
			case "full":
				return FULL;
			default:
				LOGGER.warn("configuration {}: {} '{}' not understood, storing the full capture",
						rawConfiguration.getId(), PREFERENCE, preference.getValue());
				return FULL;
			}
		}
		return FULL;
	}

	/** Whether the job's network traffic is captured as HAR (where the job type allows it). */
	public boolean recordsHar() {
		return this == FULL;
	}

	/** Whether the screenshot is stored as JPEG rather than PNG. */
	public boolean jpegScreenshot() {
		return this == COMPACT;
	}

	/** Whether the page source is also written as its own {@code page-finalsource} object. */
	public boolean writesSeparatePageSource() {
		return this == FULL;
	}

	/** Whether an unchanged session storage is stored as a pointer to the job that holds it. */
	public boolean dedupesSessionStorage() {
		return this == COMPACT;
	}
}
