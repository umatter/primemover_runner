package primemover.util.consent;

import java.util.Locale;

/**
 * What a bot does when a site puts up a cookie / GDPR consent dialog.
 *
 * <p>For a personalisation study this is a treatment variable, not a nuisance:
 * "reject all" switches off the signed-out watch-history personalisation the
 * study may be measuring. It is therefore never hard-coded; see
 * {@link ConsentPolicy} for where the value comes from.
 */
public enum ConsentChoice {
	/** Click "Accept all" (or the closest equivalent). */
	ACCEPT,
	/** Click "Reject all" / "Only necessary". */
	REJECT,
	/** Leave the dialog alone. The generic modal dismisser also leaves it alone. */
	IGNORE;

	/**
	 * Lenient parser for values from behaviours, preferences and properties:
	 * case-insensitive, surrounding whitespace ignored, and a few natural
	 * synonyms ({@code accept_all}, {@code allow}, {@code deny}, {@code none}, ...).
	 *
	 * @return the choice, or {@code null} when the value is null, blank or unknown
	 */
	public static ConsentChoice parse(String value) {
		if (value == null) {
			return null;
		}
		String v = value.trim().toLowerCase(Locale.ROOT);
		switch (v) {
		case "accept":
		case "accept_all":
		case "accept-all":
		case "acceptall":
		case "allow":
		case "allow_all":
		case "agree":
		case "true":
			return ACCEPT;
		case "reject":
		case "reject_all":
		case "reject-all":
		case "rejectall":
		case "deny":
		case "deny_all":
		case "decline":
		case "necessary":
		case "false":
			return REJECT;
		case "ignore":
		case "none":
		case "skip":
		case "off":
			return IGNORE;
		default:
			return null;
		}
	}
}
