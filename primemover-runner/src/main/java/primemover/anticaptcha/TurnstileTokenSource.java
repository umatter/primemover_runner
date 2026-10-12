package primemover.anticaptcha;

import java.util.Optional;

/**
 * Where a Cloudflare Turnstile token comes from. Implemented by
 * {@code AntiCaptchaService}; an interface so the solver can be tested without
 * the network (and without mocking a concrete class, which Mockito's inline mock
 * maker cannot do on this project's JDK 25 / byte-buddy).
 */
@FunctionalInterface
public interface TurnstileTokenSource {

	/**
	 * A token for this widget, or empty when none could be had. Never throws.
	 */
	Optional<String> solveTurnstileProxyless(String websiteUrl, String websiteKey, String action, String cData);
}
