package primemover.util.consent;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.ValueSource;

import primemover.api.model.Configuration;
import primemover.api.model.Preference;

class ConsentPolicyTest {

	@AfterEach
	void unbind() {
		ConsentPolicy.unbind();
	}

	private static Configuration configWithCookiePref(String value) {
		Configuration c = new Configuration();
		c.setId(7);
		c.setPreferences(List.of(new Preference("location", "CH-ZH-ZURICH"), new Preference("cookie_pref", value)));
		return c;
	}

	@ParameterizedTest
	@CsvSource({ "accept,ACCEPT", "Accept,ACCEPT", " REJECT ,REJECT", "accept_all,ACCEPT", "reject_all,REJECT",
			"ignore,IGNORE", "none,IGNORE", "deny,REJECT", "allow,ACCEPT" })
	void parsesChoices(String raw, ConsentChoice expected) {
		assertThat(ConsentChoice.parse(raw)).isEqualTo(expected);
	}

	@ParameterizedTest
	@ValueSource(strings = { "", "  ", "maybe", "0.5" })
	void unknownChoiceIsNull(String raw) {
		assertThat(ConsentChoice.parse(raw)).isNull();
	}

	@Test
	void nullChoiceIsNull() {
		assertThat(ConsentChoice.parse(null)).isNull();
	}

	@Test
	@DisplayName("cookie_pref {\"accept_all\": true|false} maps to accept / reject")
	void cookiePrefJson() {
		assertThat(ConsentPolicy.fromCookiePref(configWithCookiePref("{\"accept_all\": true}")))
				.isEqualTo(ConsentChoice.ACCEPT);
		assertThat(ConsentPolicy.fromCookiePref(configWithCookiePref("{\"accept_all\": false}")))
				.isEqualTo(ConsentChoice.REJECT);
		assertThat(ConsentPolicy.fromCookiePref(configWithCookiePref("{\"accept_all\":\"reject\"}")))
				.isEqualTo(ConsentChoice.REJECT);
	}

	@Test
	void cookiePrefBareWord() {
		assertThat(ConsentPolicy.fromCookiePref(configWithCookiePref("reject"))).isEqualTo(ConsentChoice.REJECT);
	}

	@Test
	@DisplayName("a malformed or empty cookie_pref is ignored, not an error")
	void cookiePrefMalformed() {
		assertThat(ConsentPolicy.fromCookiePref(configWithCookiePref("{not json"))).isNull();
		assertThat(ConsentPolicy.fromCookiePref(configWithCookiePref("{\"other\": 1}"))).isNull();
		assertThat(ConsentPolicy.fromCookiePref(configWithCookiePref(""))).isNull();
		assertThat(ConsentPolicy.fromCookiePref(configWithCookiePref("sometimes"))).isNull();
		assertThat(ConsentPolicy.fromCookiePref(null)).isNull();
		assertThat(ConsentPolicy.fromCookiePref(new Configuration())).isNull();
	}

	@Test
	void inactiveCookiePrefIsIgnored() {
		Configuration c = configWithCookiePref("{\"accept_all\": false}");
		c.getPreferences().get(1).setActive(Boolean.FALSE);
		assertThat(ConsentPolicy.fromCookiePref(c)).isNull();
	}

	@Test
	@DisplayName("precedence: job behaviour > cookie_pref > runner default > built-in fallback")
	void precedence() {
		Configuration rejectPref = configWithCookiePref("{\"accept_all\": false}");

		assertThat(ConsentPolicy.resolve("ignore", rejectPref, ConsentChoice.ACCEPT, 1L))
				.isEqualTo(ConsentChoice.IGNORE);
		assertThat(ConsentPolicy.resolve(null, rejectPref, ConsentChoice.ACCEPT, 1L)).isEqualTo(ConsentChoice.REJECT);
		assertThat(ConsentPolicy.resolve(null, new Configuration(), ConsentChoice.REJECT, 1L))
				.isEqualTo(ConsentChoice.REJECT);
		assertThat(ConsentPolicy.resolve(null, null, null, 1L)).isEqualTo(ConsentPolicy.FALLBACK);
		// an unknown behaviour value falls through instead of silently becoming a choice
		assertThat(ConsentPolicy.resolve("banana", rejectPref, ConsentChoice.ACCEPT, 1L))
				.isEqualTo(ConsentChoice.REJECT);
	}

	@Test
	void runnerDefaultFallsBackOnGarbage() {
		assertThat(ConsentPolicy.runnerDefault("reject")).isEqualTo(ConsentChoice.REJECT);
		assertThat(ConsentPolicy.runnerDefault("garbage")).isEqualTo(ConsentPolicy.FALLBACK);
		assertThat(ConsentPolicy.runnerDefault(null)).isEqualTo(ConsentPolicy.FALLBACK);
	}

	@Test
	void threadBindingIsPerThread() throws Exception {
		ConsentPolicy.bind(ConsentChoice.REJECT);
		assertThat(ConsentPolicy.current()).isEqualTo(ConsentChoice.REJECT);

		ConsentChoice[] other = new ConsentChoice[1];
		Thread t = new Thread(() -> other[0] = ConsentPolicy.current());
		t.start();
		t.join();
		assertThat(other[0]).isEqualTo(ConsentPolicy.FALLBACK);

		ConsentPolicy.unbind();
		assertThat(ConsentPolicy.current()).isEqualTo(ConsentPolicy.FALLBACK);
	}

	@Test
	void scriptResourceIsOnTheClasspath() {
		assertThat(ConsentHandler.script()).contains("ytd-consent-bump-v2-lightbox").contains("onetrust")
				.contains("alle ablehnen");
	}
}
