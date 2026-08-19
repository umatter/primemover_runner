package primemover.model.proxy;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Arrays;
import java.util.List;
import java.util.Locale;

/**
 * Translates the legacy (GeoSurf-shaped) agent location string into the keyed
 * token form Oxylabs requires inside the proxy username.
 *
 * <p>
 * Oxylabs never accepts a bare location value. Every location is a keyed token:
 * {@code cc-US}, {@code cc-GB-city-london}, {@code st-us_california}. Our agents
 * carry values such as {@code US-CO-COLORADO_SPRINGS}, which are three unkeyed
 * segments. Splicing those into the username produces either a
 * {@code 400 Bad Request / Unsupported parameters were used.} or -- far worse --
 * a working proxy with an arbitrary worldwide exit IP while the experiment
 * record claims a specific geography. Which of the two happens is not
 * documented, so this class refuses to guess: anything it cannot map with
 * confidence raises {@link IllegalArgumentException} at conversion time rather
 * than silently degrading the geography of a run.
 *
 * <p>
 * The {@code cc-} form is emitted rather than {@code st-} because {@code st-} is
 * US-only, while {@code cc-} generalises to every country. Combining {@code cc}
 * and {@code st} in one username is not a documented form and is never emitted.
 *
 * <p>
 * Values already in Oxylabs token form are kept as-is (bar lower-casing their
 * keys), so specs may emit tokens directly. Detecting that form takes both the
 * key and its value into account, because three ISO country codes collide with
 * Oxylabs keys: {@code CN} (China) with the continent key, {@code ST} (Sao Tome
 * and Principe) with the US-state key, and {@code CC} (Cocos Islands) with the
 * country key. A prefix-only test would silently turn the legacy value
 * {@code CN-SHANGHAI} into the nonsense continent token {@code cn-SHANGHAI}, so
 * a key is only honoured when its value is valid for that key -- and when it is
 * not, the value is rejected rather than guessed at.
 *
 * @see <a href=
 *      "https://developers.oxylabs.io/products/proxies/residential-proxies/making-requests">Oxylabs
 *      making requests</a>
 */
public final class OxylabsLocation {

	private static final Logger LOGGER = LoggerFactory.getLogger(OxylabsLocation.class);

	/** The seven Oxylabs continent codes. */
	private static final List<String> CONTINENTS = Arrays.asList("af", "an", "as", "eu", "na", "oc", "sa");

	/** Every documented location key. */
	private static final List<String> KEYS = Arrays.asList("cc", "city", "st", "cn", "postalcode");

	/** Legacy values are COUNTRY[-REGION]-CITY, so at most three segments. */
	private static final int MAX_LEGACY_SEGMENTS = 3;

	private OxylabsLocation() {
	}

	/**
	 * Convert an agent location value into Oxylabs username tokens.
	 *
	 * <pre>
	 * null / blank             -&gt; ""                            (no targeting; the pool rotates worldwide)
	 * "cc-US-city-chicago"     -&gt; "cc-US-city-chicago"           (already tokens)
	 * "US"                     -&gt; "cc-US"
	 * "US-CO-COLORADO_SPRINGS" -&gt; "cc-US-city-colorado_springs"
	 * "US-BOSTON"              -&gt; "cc-US-city-boston"
	 * "CN-SHANGHAI"            -&gt; throws                          (CN is both a key and a country)
	 * </pre>
	 *
	 * @param raw the agent's location value; may be null or blank
	 * @return the token string to splice into the username, or an empty string
	 *         when no targeting is requested
	 * @throws IllegalArgumentException when the value cannot be mapped
	 *                                  unambiguously
	 */
	public static String toTokens(String raw) {
		if (raw == null || raw.trim().isEmpty()) {
			return "";
		}

		String[] segments = raw.trim().split("-", -1);

		for (String segment : segments) {
			if (segment.trim().isEmpty()) {
				throw new IllegalArgumentException("Cannot map location '" + raw
						+ "' to Oxylabs tokens: it contains an empty segment.");
			}
		}

		if (isTokenForm(segments)) {
			return lowerCaseKeys(segments);
		}

		// The value is not valid token form. If it nevertheless opens with something
		// spelled like an Oxylabs key, its intent is genuinely ambiguous: `CN-SHANGHAI`
		// could be a (malformed) continent token or the legacy China/Shanghai pair, and
		// three ISO country codes -- CN, ST, CC -- collide with keys this way. Guessing
		// either way risks targeting the wrong geography silently, so demand an explicit
		// value instead.
		if (KEYS.contains(segments[0].toLowerCase(Locale.ROOT))) {
			throw new IllegalArgumentException("Cannot map location '" + raw
					+ "' to Oxylabs tokens: '" + segments[0] + "' is ambiguous -- it is both an Oxylabs key and"
					+ " an ISO country code, and the value that follows is not valid for that key. Write it"
					+ " explicitly, either as tokens (for example 'cc-" + segments[0].toUpperCase(Locale.ROOT)
					+ "-city-<name>') or with a different country.");
		}

		return fromLegacy(segments, raw);
	}

	/**
	 * A value is in token form when it is a run of key-value pairs and every value
	 * is valid for its key. Checking the value matters: {@code CN-SHANGHAI} looks
	 * like a continent token, but {@code SHANGHAI} is not a continent code, so it
	 * is really a legacy China/Shanghai pair.
	 */
	private static boolean isTokenForm(String[] segments) {
		if (segments.length < 2 || segments.length % 2 != 0) {
			return false;
		}
		for (int i = 0; i < segments.length; i += 2) {
			if (!isValidPair(segments[i], segments[i + 1])) {
				return false;
			}
		}
		return true;
	}

	private static boolean isValidPair(String key, String value) {
		String k = key.toLowerCase(Locale.ROOT);
		if (!KEYS.contains(k)) {
			return false;
		}
		switch (k) {
		case "cc":
			return value.matches("[A-Za-z]{2}");
		case "cn":
			return CONTINENTS.contains(value.toLowerCase(Locale.ROOT));
		case "st":
			return value.toLowerCase(Locale.ROOT).startsWith("us_");
		case "postalcode":
			return value.matches("\\d{5}");
		case "city":
			return value.matches("[A-Za-z0-9_]+");
		default:
			return false;
		}
	}

	/** Keys are documented only in lower case; values keep their case. */
	private static String lowerCaseKeys(String[] segments) {
		StringBuilder out = new StringBuilder();
		for (int i = 0; i < segments.length; i++) {
			if (i > 0) {
				out.append("-");
			}
			out.append(i % 2 == 0 ? segments[i].toLowerCase(Locale.ROOT) : segments[i]);
		}
		return out.toString();
	}

	/** Map the legacy COUNTRY[-REGION]-CITY form. */
	private static String fromLegacy(String[] segments, String raw) {
		String country = segments[0];

		if (!country.matches("[A-Za-z]{2}")) {
			throw new IllegalArgumentException("Cannot map location '" + raw
					+ "' to Oxylabs tokens: expected a 2-letter ISO 3166-1 alpha-2 country code as the first"
					+ " segment, got '" + country + "'. Use an explicit Oxylabs token string instead"
					+ " (for example 'cc-US-city-chicago').");
		}

		String countryToken = "cc-" + country.toUpperCase(Locale.ROOT);

		if (segments.length == 1) {
			return countryToken;
		}

		if (segments.length > MAX_LEGACY_SEGMENTS) {
			throw new IllegalArgumentException("Cannot map location '" + raw + "' to Oxylabs tokens: expected"
					+ " COUNTRY-REGION-CITY but found " + segments.length + " segments. A hyphenated city name"
					+ " cannot be told apart from an extra field, so map it explicitly instead (for example"
					+ " 'cc-" + country.toUpperCase(Locale.ROOT) + "-city-new_york').");
		}

		String last = segments[segments.length - 1];

		// A two-segment value whose tail is itself two letters is ambiguous: "US-CA"
		// is far more likely to mean the state California than a city called "ca".
		// Emitting city-ca would be exactly the silent geography corruption this
		// class exists to prevent, so refuse it.
		if (segments.length == 2 && last.matches("[A-Za-z]{2}")) {
			throw new IllegalArgumentException("Cannot map location '" + raw
					+ "' to Oxylabs tokens: '" + last + "' is ambiguous between a region code and a city name."
					+ " Use an explicit Oxylabs token string instead (for example 'cc-"
					+ country.toUpperCase(Locale.ROOT) + "-city-<name>' or 'st-us_<state>').");
		}

		// Oxylabs spells multi-word cities with a single underscore per space.
		String city = last.trim().replaceAll("\\s+", "_");

		if (!city.matches("[A-Za-z0-9_]+")) {
			throw new IllegalArgumentException("Cannot map location '" + raw
					+ "' to Oxylabs tokens: '" + last + "' is not a usable city name. Oxylabs expects the English"
					+ " name with spaces replaced by '_' (for example 'city-rio_de_janeiro').");
		}

		city = city.toLowerCase(Locale.ROOT);

		if (segments.length == MAX_LEGACY_SEGMENTS) {
			// The region segment is dropped: Oxylabs has no equivalent for the 2-letter
			// code (its `st` token wants the full name, e.g. us_colorado) and cc+st is
			// not a documented combination. This is lossy for city names that recur
			// across regions -- US-CO-SPRINGFIELD and US-IL-SPRINGFIELD both collapse to
			// cc-US-city-springfield -- so it is logged loudly rather than silently.
			LOGGER.warn("location '{}': region '{}' has no Oxylabs equivalent and is dropped; targeting on"
					+ " {}-city-{}. If this city exists in several regions, set an explicit token string"
					+ " (st-us_<state>-city-<name>) to disambiguate.", raw, segments[1], countryToken, city);
		}

		return countryToken + "-city-" + city;
	}
}
