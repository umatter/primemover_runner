package primemover.model.proxy;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.ValueSource;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.api.Assertions.assertThatCode;

class OxylabsLocationTest {

	@ParameterizedTest
	@CsvSource({ //
			"US-CO-COLORADO_SPRINGS, cc-US-city-colorado_springs", //
			"US-MA-BOSTON,            cc-US-city-boston", //
			"US-IL-CHICAGO,           cc-US-city-chicago", //
			"DE-BE-BERLIN,            cc-DE-city-berlin", //
			"US-BOSTON,               cc-US-city-boston", //
			"US,                      cc-US", //
			"de,                      cc-DE" //
	})
	@DisplayName("legacy agent locations map to keyed cc/city tokens")
	void mapsLegacyLocations(String raw, String expected) {
		assertThat(OxylabsLocation.toTokens(raw)).isEqualTo(expected);
	}

	@ParameterizedTest
	@ValueSource(strings = { //
			"cc-US-city-chicago", //
			"cc-GB-city-london", //
			"st-us_california-city-los_angeles", //
			"cn-EU", //
			"cc-US-postalcode-90210" //
	})
	@DisplayName("values already in Oxylabs token form pass through untouched")
	void isIdempotent(String tokens) {
		assertThat(OxylabsLocation.toTokens(tokens)).isEqualTo(tokens);
		assertThat(OxylabsLocation.toTokens(OxylabsLocation.toTokens(tokens))).isEqualTo(tokens);
	}

	@ParameterizedTest
	@ValueSource(strings = { "", "   " })
	@DisplayName("blank means untargeted, not an error")
	void blankYieldsNoToken(String blank) {
		assertThat(OxylabsLocation.toTokens(blank)).isEmpty();
	}

	@Test
	@DisplayName("null means untargeted, not an error")
	void nullYieldsNoToken() {
		assertThat(OxylabsLocation.toTokens(null)).isEmpty();
	}

	@Test
	@DisplayName("a two-letter tail on a two-segment value is ambiguous and must not be guessed")
	void refusesAmbiguousRegionOrCity() {
		// "US-CA" almost certainly means California, not a city called "ca".
		// Emitting city-ca would be exactly the silent geography corruption the
		// translator exists to prevent.
		assertThatThrownBy(() -> OxylabsLocation.toTokens("US-CA"))
				.isInstanceOf(IllegalArgumentException.class)
				.hasMessageContaining("ambiguous");
	}

	@Test
	@DisplayName("a non-country first segment is rejected rather than mapped")
	void refusesNonCountryPrefix() {
		assertThatThrownBy(() -> OxylabsLocation.toTokens("USA-IL-CHICAGO"))
				.isInstanceOf(IllegalArgumentException.class)
				.hasMessageContaining("country code");
	}

	@Test
	@DisplayName("an unusable city name is rejected rather than mapped")
	void refusesUnusableCity() {
		assertThatThrownBy(() -> OxylabsLocation.toTokens("US-IL-CHIC@GO"))
				.isInstanceOf(IllegalArgumentException.class)
				.hasMessageContaining("city name");
	}

	@ParameterizedTest
	@CsvSource({ //
			"CC-US,                cc-US", //
			"CC-US-CITY-chicago,   cc-US-city-chicago", //
			"St-us_california-City-los_angeles, st-us_california-city-los_angeles" //
	})
	@DisplayName("token keys are lower-cased; Oxylabs only documents them in lower case")
	void lowerCasesTokenKeys(String raw, String expected) {
		assertThat(OxylabsLocation.toTokens(raw)).isEqualTo(expected);
	}

	@Test
	@DisplayName("token values keep their case - only cc/city/st are documented case-insensitive")
	void preservesTokenValues() {
		assertThat(OxylabsLocation.toTokens("cn-EU")).isEqualTo("cn-EU");
		assertThat(OxylabsLocation.toTokens("cc-US")).isEqualTo("cc-US");
	}

	@ParameterizedTest
	@ValueSource(strings = { "cc-US-city", "cc-US-city-", "cc-", "cc-US--city-chicago" })
	@DisplayName("a structurally broken token string is rejected, not passed through")
	void refusesMalformedTokens(String broken) {
		assertThatThrownBy(() -> OxylabsLocation.toTokens(broken))
				.isInstanceOf(IllegalArgumentException.class);
	}

	@ParameterizedTest
	@ValueSource(strings = { //
			"CN-SHANGHAI", // CN is both China and the continent key
			"ST-SAO_TOME", // ST is both Sao Tome and the US-state key
			"CC-WEST_ISLAND" // CC is both Cocos Islands and the country key
	})
	@DisplayName("an ISO code that collides with an Oxylabs key is rejected, never guessed")
	void refusesKeyCountryCollision(String raw) {
		// Reading "CN-SHANGHAI" as a continent token yields the nonsense cn-SHANGHAI;
		// reading it as legacy yields cc-CN-city-shanghai. Both are guesses, and a wrong
		// guess silently targets the wrong geography, so neither is made.
		assertThatThrownBy(() -> OxylabsLocation.toTokens(raw))
				.isInstanceOf(IllegalArgumentException.class)
				.hasMessageContaining("ambiguous");
	}

	@Test
	@DisplayName("the collision is escapable by writing the tokens out explicitly")
	void collisionHasAnExplicitEscape() {
		assertThat(OxylabsLocation.toTokens("cc-CN-city-shanghai")).isEqualTo("cc-CN-city-shanghai");
	}

	@Test
	@DisplayName("a genuine continent token is still recognised")
	void realContinentTokenSurvives() {
		assertThat(OxylabsLocation.toTokens("cn-EU")).isEqualTo("cn-EU");
		assertThat(OxylabsLocation.toTokens("cn-AS")).isEqualTo("cn-AS");
	}

	@Test
	@DisplayName("a hyphenated city is refused rather than silently truncated")
	void refusesHyphenatedCity() {
		// "US-NY-NEW-YORK" must not quietly become city-york.
		assertThatThrownBy(() -> OxylabsLocation.toTokens("US-NY-NEW-YORK"))
				.isInstanceOf(IllegalArgumentException.class)
				.hasMessageContaining("4 segments");
	}

	@ParameterizedTest
	@ValueSource(strings = { "-", "--", "US-", "-US", "US--CHICAGO", "US-IL-" })
	@DisplayName("empty segments are rejected with a clear error, never a crash")
	void refusesEmptySegments(String broken) {
		assertThatThrownBy(() -> OxylabsLocation.toTokens(broken))
				.isInstanceOf(IllegalArgumentException.class)
				.hasMessageContaining("empty segment");
	}

	@ParameterizedTest
	@ValueSource(strings = { "cc-US-cty-chicago", "cc-US-xx-yy", "cc-USA-city-chicago", "st-california" })
	@DisplayName("an unknown key or a value invalid for its key is never passed through as tokens")
	void refusesUnknownKeys(String bogus) {
		// These must not reach Oxylabs verbatim: an unrecognised token is exactly the
		// silent-geography-loss case. They fall through to the legacy mapper, which
		// rejects them.
		assertThatThrownBy(() -> OxylabsLocation.toTokens(bogus))
				.isInstanceOf(IllegalArgumentException.class);
	}

	@Test
	@DisplayName("repeated or leading spaces collapse to a single underscore")
	void collapsesWhitespace() {
		assertThat(OxylabsLocation.toTokens("US-IL-  CHICAGO")).isEqualTo("cc-US-city-chicago");
		assertThat(OxylabsLocation.toTokens("US-NY-NEW  YORK")).isEqualTo("cc-US-city-new_york");
	}

	@Test
	@DisplayName("spaces in a city name become underscores")
	void normalisesSpaces() {
		assertThat(OxylabsLocation.toTokens("BR-RJ-RIO DE JANEIRO")).isEqualTo("cc-BR-city-rio_de_janeiro");
	}

	@Test
	@DisplayName("cc and st are never combined - that is not a documented Oxylabs form")
	void neverCombinesCountryAndState() {
		String tokens = OxylabsLocation.toTokens("US-CO-COLORADO_SPRINGS");
		assertThat(tokens).startsWith("cc-").doesNotContain("-st-");
	}

	@Test
	@DisplayName("the error message points the operator at the explicit token form")
	void errorIsActionable() {
		assertThatThrownBy(() -> OxylabsLocation.toTokens("US-CA"))
				.hasMessageContaining("cc-US-city-");
		assertThatCode(() -> OxylabsLocation.toTokens("cc-US-city-los_angeles")).doesNotThrowAnyException();
	}
}
