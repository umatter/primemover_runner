package primemover.anticaptcha;

import org.json.JSONException;
import org.json.JSONObject;

import javax.annotation.Nullable;
import java.util.Optional;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * The parameters Bing's challenge page renders its Cloudflare Turnstile widget
 * with, read from the page's inline {@code var CfConfig = {...}}.
 *
 * <p>
 * A token is only worth paying for if Bing will accept it, and Bing binds two
 * values into the widget besides the site key: {@code action}, which is the
 * config's {@code ruleId}, and {@code cData}, its {@code impressionGuid}. Bing's
 * success callback then sends the token to {@code verifyEndpoint + token +
 * "&clientAction=" + encodeURIComponent(ruleId)} and reloads. When a field is
 * missing, the page's own script falls back to {@code "NA"} and the all-zero
 * guid; so does this class, so the token is asked for with exactly what the
 * widget was rendered with. A missing site key or endpoint is not defaulted --
 * the page's fallback key is Cloudflare's always-pass test key, and asking for
 * that would be paying for nothing.
 */
public final class BingTurnstileChallenge {

	private static final Pattern CF_CONFIG = Pattern.compile("var\\s+CfConfig\\s*=\\s*(\\{[^{}]*\\})");

	private static final String DEFAULT_ACTION = "NA";
	private static final String DEFAULT_CDATA = "00000000-0000-0000-0000-000000000000";
	private static final String DEFAULT_PARTNER = "0";

	private final String siteKey;
	private final String verifyEndpoint;
	private final String action;
	private final String cData;
	private final String partner;

	private BingTurnstileChallenge(String siteKey, String verifyEndpoint, String action, String cData,
			String partner) {
		this.siteKey = siteKey;
		this.verifyEndpoint = verifyEndpoint;
		this.action = action;
		this.cData = cData;
		this.partner = partner;
	}

	/**
	 * The challenge on this page, or empty when the page carries no Bing
	 * Turnstile config with a site key and a verify endpoint.
	 */
	public static Optional<BingTurnstileChallenge> parse(@Nullable String pageSource) {
		if (pageSource == null || pageSource.isBlank()) {
			return Optional.empty();
		}

		Matcher matcher = CF_CONFIG.matcher(pageSource);
		if (!matcher.find()) {
			return Optional.empty();
		}

		JSONObject config;
		try {
			config = new JSONObject(matcher.group(1));
		} catch (JSONException e) {
			return Optional.empty();
		}

		String siteKey = config.optString("siteKey", "");
		String verifyEndpoint = config.optString("verifyEndpoint", "");
		if (siteKey.isBlank() || verifyEndpoint.isBlank()) {
			return Optional.empty();
		}

		String action = config.optString("ruleId", "");
		String cData = config.optString("impressionGuid", "");
		String partner = config.optString("partner", "");

		return Optional.of(new BingTurnstileChallenge(siteKey, verifyEndpoint,
				action.isBlank() ? DEFAULT_ACTION : action, cData.isBlank() ? DEFAULT_CDATA : cData,
				partner.isBlank() ? DEFAULT_PARTNER : partner));
	}

	public String getSiteKey() {
		return siteKey;
	}

	public String getVerifyEndpoint() {
		return verifyEndpoint;
	}

	/** Bing's {@code ruleId}: the widget's {@code action}, and the verify call's {@code clientAction}. */
	public String getAction() {
		return action;
	}

	/** Bing's {@code impressionGuid}: the widget's {@code cData}. */
	public String getCData() {
		return cData;
	}

	/** Bing's {@code partner}: what its callback reports the outcome under. */
	public String getPartner() {
		return partner;
	}

	@Override
	public String toString() {
		return "BingTurnstileChallenge[siteKey=" + siteKey + ", action=" + action + ", cData=" + cData + "]";
	}
}
