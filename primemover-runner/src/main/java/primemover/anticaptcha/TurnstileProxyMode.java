package primemover.anticaptcha;

import java.util.Locale;

/**
 * Where an Anti-Captcha worker solves a Turnstile widget from
 * ({@code anticaptcha.api.proxy}).
 *
 * <p>
 * The token reaches the site from the bot's browser either way. What differs is
 * the address the widget was solved from: {@link #NONE} is the worker's own,
 * {@link #BOT} is the bot's exit address, through the session's own upstream
 * proxy -- whose credentials then go to Anti-Captcha. Whether a site accepts a
 * token minted at a different address than the one presenting it is the site's
 * business, which is why both exist.
 */
public enum TurnstileProxyMode {
	/** {@code TurnstileTaskProxyless}: solved from the worker's own address. */
	NONE,
	/** {@code TurnstileTask}: solved through the bot's upstream proxy. */
	BOT;

	/**
	 * @throws IllegalStateException for anything but {@code none} or {@code bot},
	 *                               so a typo stops the runner instead of quietly
	 *                               picking one
	 */
	public static TurnstileProxyMode parse(String configured) {
		String value = configured == null ? "" : configured.strip().toLowerCase(Locale.ROOT);
		switch (value) {
		case "none":
			return NONE;
		case "bot":
			return BOT;
		default:
			throw new IllegalStateException("anticaptcha.api.proxy must be 'none' or 'bot', not '" + configured + "'");
		}
	}
}
