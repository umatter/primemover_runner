package primemover.util.consent;

import java.util.List;
import java.util.Locale;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

import primemover.api.model.Configuration;
import primemover.api.model.Preference;
import primemover.model.Crawler;
import primemover.model.IJob;
import primemover.model.jobs.BaseJob;

/**
 * Decides which {@link ConsentChoice} applies to the job currently running on
 * this thread, and makes it available to code that has no job context of its
 * own ({@link ConsentHandler}, {@link primemover.util.ModalDismisser}).
 *
 * <p>Precedence, most specific first:
 * <ol>
 * <li>the job's {@code consent} behaviour ({@code accept | reject | ignore});</li>
 * <li>the crawler's {@code cookie_pref} configuration preference, the JSON
 * {@code {"accept_all": true|false}} that the experiment designer already emits
 * for every bot ({@code true} → accept, {@code false} → reject);</li>
 * <li>the runner-wide default, property {@code consent.choice}.</li>
 * </ol>
 *
 * <p>The choice is bound per executor thread by the job dispatcher for the
 * duration of one job; one job runs per thread at a time, so a
 * {@link ThreadLocal} is sufficient and needs no synchronisation.
 */
public final class ConsentPolicy {

	private static final Logger logger = LoggerFactory.getLogger(ConsentPolicy.class);
	private static final ObjectMapper MAPPER = new ObjectMapper();
	private static final ThreadLocal<ConsentChoice> CURRENT = new ThreadLocal<>();

	/** Name of the crawler preference the runner consumes. */
	public static final String COOKIE_PREF = "cookie_pref";
	/** Name of the per-job behaviour. */
	public static final String BEHAVIOUR = "consent";
	/** Applied when nothing more specific is configured. */
	public static final ConsentChoice FALLBACK = ConsentChoice.ACCEPT;

	private ConsentPolicy() {
	}

	/** Resolve the effective choice for a job on a crawler. Never returns null. */
	public static ConsentChoice resolve(IJob job, Crawler crawler, ConsentChoice runnerDefault) {
		String behaviour = job instanceof BaseJob ? ((BaseJob) job).getConsent() : null;
		Configuration raw = crawler != null ? crawler.getRawConfiguration() : null;
		return resolve(behaviour, raw, runnerDefault, job != null ? job.getId() : null);
	}

	/** Same as {@link #resolve(IJob, Crawler, ConsentChoice)} on the raw values, for tests and callers without models. */
	public static ConsentChoice resolve(String jobBehaviour, Configuration rawConfiguration,
			ConsentChoice runnerDefault, Long jobId) {
		if (jobBehaviour != null && !jobBehaviour.isBlank()) {
			ConsentChoice fromJob = ConsentChoice.parse(jobBehaviour);
			if (fromJob != null) {
				return fromJob;
			}
			logger.warn("job {}: unknown consent behaviour '{}' ignored (use accept|reject|ignore)", jobId,
					jobBehaviour);
		}

		ConsentChoice fromPreference = fromCookiePref(rawConfiguration);
		if (fromPreference != null) {
			return fromPreference;
		}

		return runnerDefault != null ? runnerDefault : FALLBACK;
	}

	/**
	 * Read {@code cookie_pref} from the crawler configuration. Accepts the
	 * designer's JSON form ({@code {"accept_all": true}}) and, leniently, a bare
	 * word ({@code accept}, {@code reject}, ...).
	 *
	 * @return the choice, or null if the preference is absent, inactive or unparseable
	 */
	static ConsentChoice fromCookiePref(Configuration rawConfiguration) {
		if (rawConfiguration == null || rawConfiguration.getPreferences() == null) {
			return null;
		}
		List<Preference> prefs = rawConfiguration.getPreferences();
		for (Preference p : prefs) {
			if (p == null || p.getName() == null || !COOKIE_PREF.equalsIgnoreCase(p.getName().trim())) {
				continue;
			}
			if (Boolean.FALSE.equals(p.getActive())) {
				continue;
			}
			String value = p.getValue();
			if (value == null || value.isBlank()) {
				return null;
			}
			String trimmed = value.trim();
			if (trimmed.startsWith("{")) {
				try {
					JsonNode node = MAPPER.readTree(trimmed);
					JsonNode acceptAll = node.get("accept_all");
					if (acceptAll == null || acceptAll.isNull()) {
						logger.warn("configuration {}: cookie_pref {} has no accept_all key, ignored",
								rawConfiguration.getId(), trimmed);
						return null;
					}
					if (acceptAll.isBoolean()) {
						return acceptAll.booleanValue() ? ConsentChoice.ACCEPT : ConsentChoice.REJECT;
					}
					ConsentChoice parsed = ConsentChoice.parse(acceptAll.asText());
					if (parsed == null) {
						logger.warn("configuration {}: cookie_pref accept_all={} not understood, ignored",
								rawConfiguration.getId(), acceptAll);
					}
					return parsed;
				} catch (Exception e) {
					logger.warn("configuration {}: cookie_pref is not valid JSON ({}), ignored: {}",
							rawConfiguration.getId(), e.getMessage(), trimmed);
					return null;
				}
			}
			ConsentChoice parsed = ConsentChoice.parse(trimmed);
			if (parsed == null) {
				logger.warn("configuration {}: cookie_pref '{}' not understood, ignored", rawConfiguration.getId(),
						trimmed);
			}
			return parsed;
		}
		return null;
	}

	/** Parse the {@code consent.choice} property; unknown values fall back to {@link #FALLBACK} with a warning. */
	public static ConsentChoice runnerDefault(String property) {
		ConsentChoice parsed = ConsentChoice.parse(property);
		if (parsed == null) {
			if (property != null && !property.isBlank()) {
				logger.warn("consent.choice='{}' not understood, using {}", property,
						FALLBACK.name().toLowerCase(Locale.ROOT));
			}
			return FALLBACK;
		}
		return parsed;
	}

	/** Bind the choice for the job about to run on this thread. Pair with {@link #unbind()} in a finally block. */
	public static void bind(ConsentChoice choice) {
		CURRENT.set(choice);
	}

	public static void unbind() {
		CURRENT.remove();
	}

	/** The choice bound to this thread, or {@link #FALLBACK} outside a dispatched job. */
	public static ConsentChoice current() {
		ConsentChoice c = CURRENT.get();
		return c != null ? c : FALLBACK;
	}
}
