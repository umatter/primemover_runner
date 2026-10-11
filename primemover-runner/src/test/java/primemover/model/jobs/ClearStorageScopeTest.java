package primemover.model.jobs;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class ClearStorageScopeTest {

	@Test
	@DisplayName("the documented scopes parse, case-insensitively")
	void parsesTheDocumentedScopes() {
		assertThat(ClearStorageScope.parse("none")).isEqualTo(ClearStorageScope.NONE);
		assertThat(ClearStorageScope.parse("local")).isEqualTo(ClearStorageScope.LOCAL);
		assertThat(ClearStorageScope.parse("session")).isEqualTo(ClearStorageScope.SESSION);
		assertThat(ClearStorageScope.parse(" ALL ")).isEqualTo(ClearStorageScope.ALL);
	}

	@Test
	@DisplayName("a missing or misspelt scope fails rather than defaulting")
	void refusesToGuess() {
		// Defaulting to NONE would turn a typo into a bot that keeps its storage while the
		// experiment record says it was cleared -- a silent second control arm, which is the
		// failure this whole privacy factor exists to avoid.
		for (String bad : new String[] {null, "", "   ", "all_storage", "clear", "true"}) {
			assertThatThrownBy(() -> ClearStorageScope.parse(bad))
					.as("scope %s", bad)
					.isInstanceOf(IllegalArgumentException.class);
		}
	}
}
