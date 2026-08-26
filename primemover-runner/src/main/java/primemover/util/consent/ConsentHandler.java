package primemover.util.consent;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.Locale;
import java.util.Map;

import org.openqa.selenium.JavascriptExecutor;
import org.openqa.selenium.WebDriver;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Detects cookie / GDPR consent dialogs on the current page and applies the
 * thread's {@link ConsentPolicy}: click "Accept all", click "Reject all", or
 * leave it alone. Runs after every navigation (visit, lambda-select click) and
 * before the generic {@link primemover.util.ModalDismisser} so that a consent
 * dialog is always answered according to the bot's arm, never by whichever
 * button happens to come first in the DOM.
 *
 * <p>Detection and clicking live in {@code consent/consent-handler.js} (on the
 * classpath) — it recognises the YouTube/Google consent bump, OneTrust,
 * Cookiebot, Didomi, Usercentrics, TrustArc, Quantcast, Sourcepoint and a
 * generic cookie/consent container, in EN/DE/FR/IT/ES.
 *
 * <p>Every outcome is logged at INFO with the CMP and the button label so the
 * runner log answers "what did this bot consent to" per job. A dialog that is
 * still visible after the click is logged at WARN: that is the case where an
 * arm would silently run behind a modal.
 */
public final class ConsentHandler {

	private static final Logger logger = LoggerFactory.getLogger(ConsentHandler.class);
	private static final String SCRIPT_RESOURCE = "/consent/consent-handler.js";
	private static final String SCRIPT = loadScript();

	/** How long to give the page to remove the dialog after a click before re-checking. */
	private static final long SETTLE_MILLIS = 800;

	private ConsentHandler() {
	}

	static String script() {
		return SCRIPT;
	}

	private static String loadScript() {
		try (InputStream in = ConsentHandler.class.getResourceAsStream(SCRIPT_RESOURCE)) {
			if (in == null) {
				throw new IllegalStateException("classpath resource missing: " + SCRIPT_RESOURCE);
			}
			return new String(in.readAllBytes(), StandardCharsets.UTF_8);
		} catch (IOException e) {
			throw new IllegalStateException("cannot read " + SCRIPT_RESOURCE, e);
		}
	}

	/** Apply the choice bound to the current thread (see {@link ConsentPolicy#current()}). */
	public static ConsentResult handle(WebDriver driver) {
		return handle(driver, ConsentPolicy.current());
	}

	/**
	 * Detect a consent dialog and apply {@code choice}. Never throws: a page
	 * that cannot be scripted is reported as "not present" with a warning.
	 */
	public static ConsentResult handle(WebDriver driver, ConsentChoice choice) {
		if (!(driver instanceof JavascriptExecutor)) {
			return ConsentResult.NOT_PRESENT;
		}
		JavascriptExecutor jse = (JavascriptExecutor) driver;
		String action = choice.name().toLowerCase(Locale.ROOT);

		try {
			Map<?, ?> first = run(jse, action, "act");
			if (first == null) {
				return ConsentResult.NOT_PRESENT;
			}
			String cmp = str(first.get("cmp"));

			if (choice == ConsentChoice.IGNORE) {
				logger.info("consent dialog ({}) present, left in place: policy is ignore", cmp);
				return ConsentResult.ignored(cmp);
			}

			boolean clicked = Boolean.TRUE.equals(first.get("clicked"));
			String label = str(first.get("label"));
			if (!clicked) {
				logger.warn("consent dialog ({}) present but no '{}' button could be clicked: {} (buttons: {})", cmp,
						action, str(first.get("reason")), label);
				return new ConsentResult(true, false, cmp, label, action, true);
			}

			sleep(SETTLE_MILLIS);
			Map<?, ?> probe = run(jse, action, "probe");
			if (probe != null) {
				// one retry: some CMPs re-render the dialog once before honouring the click
				Map<?, ?> second = run(jse, action, "act");
				sleep(SETTLE_MILLIS);
				probe = run(jse, action, "probe");
				if (probe != null) {
					logger.warn("consent dialog ({}) still present after clicking '{}' twice — the page may be "
							+ "running behind a modal (second attempt: {})", cmp, label,
							second != null ? second.get("reason") : "no dialog found on retry");
					return new ConsentResult(true, true, cmp, label, action, true);
				}
			}

			logger.info("consent dialog ({}) answered: {} -> clicked '{}'", cmp, action, label);
			return new ConsentResult(true, true, cmp, label, action, false);
		} catch (Exception e) {
			logger.warn("consent handling failed, page left as is: {}", e.toString());
			return ConsentResult.NOT_PRESENT;
		}
	}

	/** Detection only, no click. */
	public static boolean isPresent(WebDriver driver) {
		if (!(driver instanceof JavascriptExecutor)) {
			return false;
		}
		try {
			return run((JavascriptExecutor) driver, "accept", "probe") != null;
		} catch (Exception e) {
			return false;
		}
	}

	private static Map<?, ?> run(JavascriptExecutor jse, String action, String mode) {
		Object result = jse.executeScript(SCRIPT, action, mode);
		return result instanceof Map ? (Map<?, ?>) result : null;
	}

	private static String str(Object o) {
		return o == null ? null : String.valueOf(o);
	}

	private static void sleep(long millis) {
		try {
			Thread.sleep(millis);
		} catch (InterruptedException e) {
			Thread.currentThread().interrupt();
		}
	}
}
