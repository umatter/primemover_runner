package primemover.anticaptcha;

import javax.annotation.Nullable;
import java.util.Optional;
import java.util.regex.Pattern;

/**
 * Recognises the handful of <em>challenge pages</em> a bot can end a job on --
 * the block interstitials that replace the page that was asked for, so the
 * session saw a "prove you are not a robot" screen instead of the content the
 * experiment wanted.
 *
 * <p>
 * This exists because the runner's other captcha signal cannot carry that
 * meaning. {@code TaskOperator}'s {@code captcha_found} is
 * {@code pageSource.toLowerCase().contains("captcha")}, and a captcha
 * <b>widget</b> is present on a great many perfectly ordinary pages: an
 * invisible reCAPTCHA v3 badge ({@code <div class="grecaptcha-badge">}), an
 * invisible widget div ({@code g-recaptcha g-recaptcha--invisible}), a
 * {@code recaptcha/api2/anchor} iframe, a {@code "recaptchaSiteKey":"6Lc…"} in
 * page javascript -- or, memorably, a YouTube video titled "The Real CAPTCHA vs.
 * The Fake One". Twelve non-Google jobs where {@code captcha_found} fired were
 * sampled and every one was a false positive; the flag is set on 354 of 26,737
 * jobs checked over 60 days. So {@code captcha_found} stays exactly as it is
 * (the API stores it, and it is backward compatible), and this class answers the
 * different, narrower question.
 *
 * <p>
 * Every signature therefore requires a <b>page-level</b> signal -- the URL the
 * block redirected to, or text that only the block page carries -- and never a
 * widget marker. Three signatures are implemented, all seen in our own data:
 *
 * <ul>
 * <li><b>Google sorry page.</b> A {@code /sorry/} URL on any Google domain, or
 * the sentence "detected unusual traffic from your computer network". This is
 * what all 22 bots of experiment 185 hit on 2026-09-20.</li>
 * <li><b>Cloudflare challenge.</b> The {@code Just a moment...} title
 * <em>together with</em> a challenge marker in the source -- {@code cf-chl} or
 * its underscore spelling {@code cf_chl} (Cloudflare uses both, e.g.
 * {@code cf-chl-bypass} and {@code __cf_chl_f_tk}), or
 * {@code challenge-platform}. The title alone is not enough: plenty of ordinary
 * loading screens use it.</li>
 * <li><b>Bing challenge.</b> Bing's own "One last step" page, which keeps the
 * {@code /search} URL and renders a Cloudflare Turnstile widget in place of the
 * results. Two signals, both required: the sentence "please solve the challenge
 * below to continue" and Bing's {@code bing.com/challenge/verify} endpoint in the
 * page config. Experiment 199 hit it twice on 2026-10-12 (jobs 715588, 715686);
 * of the 126 Bing result pages stored that month, those two carry either signal
 * and no other does. {@code challenges.cloudflare.com} is useless as a signal:
 * every ordinary Bing results page names it in an inline allow-list.</li>
 * </ul>
 *
 * <p>
 * hCaptcha and standalone reCAPTCHA challenge pages are deliberately
 * <b>not</b> matched: we have no instance of either in our data, and a
 * speculative rule here buys a false-positive risk in exchange for nothing.
 * Add one when a real page turns up to write the test against.
 *
 * <p>
 * Pure and static: it takes the URL and the page source the caller already has
 * in hand and returns a short human label, so it can be unit-tested without a
 * browser.
 */
public final class ChallengePageDetector {

	/**
	 * {@code https://www.google.<tld>/sorry/…} on any Google country domain. The
	 * path prefix is what matters -- an ordinary search for the word "sorry" is
	 * {@code /search?q=…} and does not match.
	 */
	private static final Pattern GOOGLE_SORRY_URL = Pattern
			.compile("^https?://(?:[a-z0-9-]+\\.)*google\\.[a-z]{2,}(?:\\.[a-z]{2,})*/sorry/", Pattern.CASE_INSENSITIVE);

	/**
	 * The sentence the sorry page shows under "About this page". Present in every
	 * localisation we run (the bots browse in English), and present in no ordinary
	 * page.
	 */
	private static final String GOOGLE_SORRY_TEXT = "detected unusual traffic from your computer network";

	private static final String GOOGLE_SORRY_LABEL = "google sorry page (unusual traffic)";

	/** {@code <title>Just a moment...</title>}, with either kind of ellipsis. */
	private static final Pattern CLOUDFLARE_TITLE = Pattern
			.compile("<title[^>]*>\\s*just a moment\\s*(?:\\.\\.\\.|\\u2026)?\\s*</title>", Pattern.CASE_INSENSITIVE);

	private static final String CLOUDFLARE_LABEL = "cloudflare challenge page";

	/** The visible sentence under Bing's "One last step" header. */
	private static final String BING_CHALLENGE_TEXT = "please solve the challenge below to continue";

	/** Where Bing's page sends the Turnstile token; only its challenge page carries it. */
	private static final String BING_VERIFY_ENDPOINT = "bing.com/challenge/verify";

	/** The label of Bing's Turnstile challenge, the one challenge the runner can solve. */
	public static final String BING_TURNSTILE_LABEL = "bing challenge page (turnstile)";

	private ChallengePageDetector() {
		// static utility
	}

	/**
	 * The known challenge page this job ended on, or empty.
	 *
	 * @param currentUrl  the browser's current URL, may be {@code null} when it
	 *                    could not be read
	 * @param pageSource  the page source, may be {@code null} when it could not be
	 *                    read
	 * @return a short human label suitable for a {@code status_message}, e.g.
	 *         {@code "google sorry page (unusual traffic)"}, or
	 *         {@link Optional#empty()} when the page is not a challenge page we
	 *         recognise. Never a guess.
	 */
	public static Optional<String> detect(@Nullable String currentUrl, @Nullable String pageSource) {
		String url = currentUrl == null ? "" : currentUrl.strip();
		String source = pageSource == null ? "" : pageSource;

		if (url.isEmpty() && source.isBlank()) {
			return Optional.empty();
		}

		if (!url.isEmpty() && GOOGLE_SORRY_URL.matcher(url).find()) {
			return Optional.of(GOOGLE_SORRY_LABEL);
		}

		if (source.isBlank()) {
			return Optional.empty();
		}

		String lower = source.toLowerCase();

		if (lower.contains(GOOGLE_SORRY_TEXT)) {
			return Optional.of(GOOGLE_SORRY_LABEL);
		}

		// The title says "loading"; the marker says "loading a Cloudflare
		// challenge". Both are required.
		if (CLOUDFLARE_TITLE.matcher(lower).find() && (lower.contains("cf-chl") || lower.contains("cf_chl")
				|| lower.contains("challenge-platform"))) {
			return Optional.of(CLOUDFLARE_LABEL);
		}

		// The sentence says "block page"; the endpoint says "Bing's". Both are
		// required, so a page config shipped on ordinary result pages cannot
		// turn every search into a challenge.
		if (lower.contains(BING_CHALLENGE_TEXT) && lower.contains(BING_VERIFY_ENDPOINT)) {
			return Optional.of(BING_TURNSTILE_LABEL);
		}

		return Optional.empty();
	}
}
