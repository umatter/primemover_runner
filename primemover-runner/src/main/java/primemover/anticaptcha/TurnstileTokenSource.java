package primemover.anticaptcha;

import primemover.anticaptcha.service.TaskProxy;

import javax.annotation.Nullable;
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
	 *
	 * @param proxy the proxy the worker solves through (the bot's own upstream, so
	 *              the token comes from the bot's exit address), or {@code null} to
	 *              let the worker solve from its own address
	 */
	Optional<String> solveTurnstile(String websiteUrl, String websiteKey, String action, String cData,
			@Nullable TaskProxy proxy);
}
