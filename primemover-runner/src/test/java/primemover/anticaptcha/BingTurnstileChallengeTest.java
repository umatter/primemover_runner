package primemover.anticaptcha;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * The solver can only ask for a token that Bing will accept if it asks with the
 * parameters Bing's own page renders the widget with: the site key, and the
 * {@code action} and {@code cData} Bing binds into the token.
 */
class BingTurnstileChallengeTest {

	private static String fixture(String name) throws IOException {
		try (InputStream in = BingTurnstileChallengeTest.class.getResourceAsStream("/challenge/" + name)) {
			assertThat(in).as("fixture " + name).isNotNull();
			return new String(in.readAllBytes(), StandardCharsets.UTF_8);
		}
	}

	@Test
	@DisplayName("the real challenge page yields the widget parameters Bing renders it with")
	void parsesTheRealChallengePage() throws IOException {
		Optional<BingTurnstileChallenge> parsed = BingTurnstileChallenge
				.parse(fixture("bing-turnstile-exp199-job715588.html"));

		assertThat(parsed).isPresent();
		BingTurnstileChallenge challenge = parsed.get();
		assertThat(challenge.getSiteKey()).isEqualTo("0x4AAAAAABgNScUsbCFCjwIZ");
		assertThat(challenge.getVerifyEndpoint()).isEqualTo("https://www.bing.com/challenge/verify?partner=7&token=");
		assertThat(challenge.getAction()).isEqualTo("CC7527");
		assertThat(challenge.getCData()).isEqualTo("AEE96B5FD39F4A399BEB7847BE9FD66C");
		assertThat(challenge.getPartner()).isEqualTo("7");
	}

	@Test
	@DisplayName("an ordinary results page has no challenge to parse")
	void ordinaryResultsPageHasNone() throws IOException {
		assertThat(BingTurnstileChallenge.parse(fixture("bing-serp-ordinary-exp199-job715553.html"))).isEmpty();
	}

	@Test
	@DisplayName("a config without a site key is not a challenge we can solve")
	void configWithoutSiteKey() {
		String source = "<script>var CfConfig ={\"lang\":\"en\",\"verifyEndpoint\":"
				+ "\"https://www.bing.com/challenge/verify?partner=7\\u0026token=\"};</script>";

		assertThat(BingTurnstileChallenge.parse(source)).isEmpty();
	}

	@Test
	@DisplayName("missing action and cData fall back to the values Bing's page itself uses")
	void missingActionAndCDataUseBingsDefaults() {
		String source = "<script>var CfConfig ={\"siteKey\":\"0x4AAAAAABgNScUsbCFCjwIZ\",\"verifyEndpoint\":"
				+ "\"https://www.bing.com/challenge/verify?partner=7\\u0026token=\"};</script>";

		BingTurnstileChallenge challenge = BingTurnstileChallenge.parse(source).orElseThrow();

		assertThat(challenge.getAction()).isEqualTo("NA");
		assertThat(challenge.getCData()).isEqualTo("00000000-0000-0000-0000-000000000000");
		assertThat(challenge.getPartner()).isEqualTo("0");
	}

	@Test
	@DisplayName("null, blank and unparseable sources yield nothing")
	void nullBlankAndBroken() {
		assertThat(BingTurnstileChallenge.parse(null)).isEmpty();
		assertThat(BingTurnstileChallenge.parse("  ")).isEmpty();
		assertThat(BingTurnstileChallenge.parse("<script>var CfConfig ={\"siteKey\":};</script>")).isEmpty();
	}
}
