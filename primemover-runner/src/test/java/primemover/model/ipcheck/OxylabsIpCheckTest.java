package primemover.model.ipcheck;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class OxylabsIpCheckTest {

	private final ObjectMapper mapper = new ObjectMapper();

	/** Shape of a real ip.oxylabs.io/location response, providers disagreeing on city. */
	private static final String SAMPLE = "{\"ip\":\"85.1.2.3\",\"providers\":{"
			+ "\"dbip\":{\"country\":\"CH\",\"asn\":\"AS559\",\"org_name\":\"SWITCH\",\"city\":\"Zurich\",\"zip_code\":\"\"},"
			+ "\"ip2location\":{\"country\":\"CH\",\"asn\":\"\",\"org_name\":\"\",\"city\":\"Zurich\",\"zip_code\":\"8099\"},"
			+ "\"ipinfo\":{\"country\":\"CH\",\"asn\":\"AS559\",\"org_name\":\"SWITCH\",\"city\":\"\"},"
			+ "\"maxmind\":{\"country\":\"CH\",\"asn\":\"AS559\",\"org_name\":\"SWITCH\",\"city\":\"\","
			+ "\"time_zone_identifier\":\"Europe/Zurich\"}}}";

	@Test
	@DisplayName("the nested provider payload parses")
	void parsesNestedProviders() throws Exception {
		OxylabsIpCheck check = mapper.readValue(SAMPLE, OxylabsIpCheck.class);

		assertThat(check.getIp()).isEqualTo("85.1.2.3");
		assertThat(check.getProviders()).containsOnlyKeys("dbip", "ip2location", "ipinfo", "maxmind");
		assertThat(check.getProviders().get("maxmind").getTime_zone_identifier()).isEqualTo("Europe/Zurich");
	}

	@Test
	@DisplayName("unknown fields do not break parsing")
	void toleratesUnknownFields() throws Exception {
		String json = "{\"ip\":\"1.2.3.4\",\"future_field\":9,\"providers\":{\"dbip\":{\"country\":\"US\",\"x\":1}}}";

		assertThat(mapper.readValue(json, OxylabsIpCheck.class).reportedCountries()).containsExactly("US");
	}

	@Test
	@DisplayName("countries are deduplicated and upper-cased")
	void collectsCountries() throws Exception {
		assertThat(mapper.readValue(SAMPLE, OxylabsIpCheck.class).reportedCountries()).containsExactly("CH");
	}

	@Test
	@DisplayName("blank cities are ignored, and the rest normalise to Oxylabs token spelling")
	void collectsOnlyReportedCities() throws Exception {
		// ipinfo and maxmind returned "" for this IP - they must not count as a city.
		assertThat(mapper.readValue(SAMPLE, OxylabsIpCheck.class).reportedCities()).containsExactly("zurich");
	}

	@Test
	@DisplayName("a response where no provider knows the city yields an empty list, not a blank entry")
	void handlesNoCityAtAll() throws Exception {
		String json = "{\"ip\":\"1.2.3.4\",\"providers\":{\"maxmind\":{\"country\":\"US\",\"city\":\"\"}}}";

		OxylabsIpCheck check = mapper.readValue(json, OxylabsIpCheck.class);

		assertThat(check.reportedCities()).isEmpty();
		assertThat(check.reportedCountries()).containsExactly("US");
	}

	@Test
	@DisplayName("multi-word cities compare against the Oxylabs city- token spelling")
	void normalisesMultiWordCities() {
		assertThat(OxylabsIpCheck.normaliseCity("Colorado Springs")).isEqualTo("colorado_springs");
		assertThat(OxylabsIpCheck.normaliseCity("  Rio  De  Janeiro ")).isEqualTo("rio_de_janeiro");
		assertThat(OxylabsIpCheck.normaliseCity(null)).isEmpty();
	}

	@Test
	@DisplayName("the providers backing a city can be named, for logging a disagreement")
	void namesProvidersReportingACity() throws Exception {
		assertThat(mapper.readValue(SAMPLE, OxylabsIpCheck.class).providersReporting("Zurich"))
				.containsExactly("dbip", "ip2location");
	}
}
