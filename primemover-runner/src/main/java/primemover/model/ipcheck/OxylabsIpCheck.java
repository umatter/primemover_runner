package primemover.model.ipcheck;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * Response of {@code https://ip.oxylabs.io/location}, the exit-IP self-check
 * endpoint Oxylabs mandates (third-party IP checkers are a restricted target).
 *
 * <p>
 * The payload is not flat: it carries one block per geolocation database, and
 * <strong>the providers disagree</strong> -- a live sample had {@code dbip} and
 * {@code ip2location} reporting a city while {@code ipinfo} and {@code maxmind}
 * returned an empty string for the very same IP. Oxylabs assigns its own
 * {@code cc}/{@code st}/{@code city} targeting from MaxMind, which is one of the
 * providers that can come back blank, so asserting a city against a single
 * provider would fail runs whose targeting actually worked.
 *
 * <pre>
 * {"ip":"...","providers":{
 *    "dbip":       {"country":"CH","city":"Zurich",...},
 *    "ip2location":{"country":"CH","city":"Zurich",...},
 *    "ipinfo":     {"country":"CH","city":"",...},
 *    "maxmind":    {"country":"CH","city":"",...}}}
 * </pre>
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public class OxylabsIpCheck {

	private String ip;

	private Map<String, Provider> providers = new LinkedHashMap<>();

	@JsonIgnoreProperties(ignoreUnknown = true)
	public static class Provider {
		private String country;
		private String city;
		private String asn;
		private String org_name;
		private String zip_code;
		private String time_zone;
		private String time_zone_identifier;

		public String getCountry() {
			return country;
		}

		public void setCountry(String country) {
			this.country = country;
		}

		public String getCity() {
			return city;
		}

		public void setCity(String city) {
			this.city = city;
		}

		public String getAsn() {
			return asn;
		}

		public void setAsn(String asn) {
			this.asn = asn;
		}

		public String getOrg_name() {
			return org_name;
		}

		public void setOrg_name(String orgName) {
			this.org_name = orgName;
		}

		public String getZip_code() {
			return zip_code;
		}

		public void setZip_code(String zipCode) {
			this.zip_code = zipCode;
		}

		public String getTime_zone() {
			return time_zone;
		}

		public void setTime_zone(String timeZone) {
			this.time_zone = timeZone;
		}

		public String getTime_zone_identifier() {
			return time_zone_identifier;
		}

		public void setTime_zone_identifier(String timeZoneIdentifier) {
			this.time_zone_identifier = timeZoneIdentifier;
		}
	}

	public String getIp() {
		return ip;
	}

	public void setIp(String ip) {
		this.ip = ip;
	}

	public Map<String, Provider> getProviders() {
		return providers;
	}

	public void setProviders(Map<String, Provider> providers) {
		this.providers = providers == null ? new LinkedHashMap<>() : providers;
	}

	/**
	 * @return every non-blank country reported, upper-cased, in provider order
	 */
	public List<String> reportedCountries() {
		return collect(true);
	}

	/**
	 * @return every non-blank city reported, lower-cased with spaces normalised to
	 *         underscores so it can be compared with an Oxylabs {@code city-} token
	 */
	public List<String> reportedCities() {
		return collect(false);
	}

	/** @return the names of the providers that reported the given city */
	public List<String> providersReporting(String city) {
		List<String> names = new ArrayList<>();
		for (Map.Entry<String, Provider> entry : providers.entrySet()) {
			if (normaliseCity(entry.getValue().getCity()).equals(normaliseCity(city))) {
				names.add(entry.getKey());
			}
		}
		return names;
	}

	private List<String> collect(boolean country) {
		List<String> out = new ArrayList<>();
		for (Provider provider : providers.values()) {
			String raw = country ? provider.getCountry() : provider.getCity();
			if (raw != null && !raw.trim().isEmpty()) {
				String value = country ? raw.trim().toUpperCase(Locale.ROOT) : normaliseCity(raw);
				if (!out.contains(value)) {
					out.add(value);
				}
			}
		}
		return Collections.unmodifiableList(out);
	}

	/** Oxylabs spells cities lower-case with underscores; the check endpoint does not. */
	public static String normaliseCity(String city) {
		if (city == null) {
			return "";
		}
		return city.trim().toLowerCase(Locale.ROOT).replaceAll("\\s+", "_");
	}
}
