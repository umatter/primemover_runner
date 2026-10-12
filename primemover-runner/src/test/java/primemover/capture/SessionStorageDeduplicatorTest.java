package primemover.capture;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Session storage stored only when it changed. A pointer must always lead to an
 * object that exists and holds the content in full: never to a job whose write
 * failed, and never to another pointer.
 */
class SessionStorageDeduplicatorTest {

	private static final String BLOB = "{\"SerpCache\":\"" + "x".repeat(5000) + "\"}";
	private static final String OTHER = "{\"SerpCache\":\"y\"}";

	@Test
	@DisplayName("the first job stores in full; an identical next job points at it")
	void identicalAfterAWrittenFullCopyIsAPointer() {
		SessionStorageDeduplicator dedup = new SessionStorageDeduplicator();

		assertThat(dedup.check(1L, "static-jobdata-visitjob-NULL-1", BLOB)).isEmpty();
		dedup.written(1L, true);
		Optional<SessionStorageRef> ref = dedup.check(2L, "static-jobdata-waitjob-NULL-2", BLOB);

		assertThat(ref).isPresent();
		assertThat(ref.get().getJobId()).isEqualTo(1L);
		assertThat(ref.get().getObject()).isEqualTo("static-jobdata-visitjob-NULL-1");
		assertThat(ref.get().getSha256()).hasSize(64);
	}

	@Test
	@DisplayName("pointers never chain: the third identical job also points at the first")
	void pointersDoNotChain() {
		SessionStorageDeduplicator dedup = new SessionStorageDeduplicator();
		dedup.check(1L, "static-jobdata-visitjob-NULL-1", BLOB);
		dedup.written(1L, true);
		dedup.check(2L, "static-jobdata-waitjob-NULL-2", BLOB);
		dedup.written(2L, true);

		assertThat(dedup.check(3L, "static-jobdata-waitjob-NULL-3", BLOB).map(SessionStorageRef::getJobId))
				.contains(1L);
	}

	@Test
	@DisplayName("a changed storage is stored in full and becomes the new reference")
	void changedIsStoredInFull() {
		SessionStorageDeduplicator dedup = new SessionStorageDeduplicator();
		dedup.check(1L, "static-jobdata-visitjob-NULL-1", BLOB);
		dedup.written(1L, true);

		assertThat(dedup.check(2L, "static-jobdata-visitjob-NULL-2", OTHER)).isEmpty();
		dedup.written(2L, true);
		assertThat(dedup.check(3L, "static-jobdata-waitjob-NULL-3", OTHER).map(SessionStorageRef::getJobId))
				.contains(2L);
	}

	@Test
	@DisplayName("a full copy whose write failed is never pointed at")
	void failedWriteIsNotAReference() {
		SessionStorageDeduplicator dedup = new SessionStorageDeduplicator();
		dedup.check(1L, "static-jobdata-visitjob-NULL-1", BLOB);
		dedup.written(1L, false);

		assertThat(dedup.check(2L, "static-jobdata-waitjob-NULL-2", BLOB)).isEmpty();
		dedup.written(2L, true);
		assertThat(dedup.check(3L, "static-jobdata-waitjob-NULL-3", BLOB).map(SessionStorageRef::getJobId))
				.contains(2L);
	}

	@Test
	@DisplayName("an unreadable (null) storage is neither a pointer nor a reference")
	void nullIsLeftAlone() {
		SessionStorageDeduplicator dedup = new SessionStorageDeduplicator();
		dedup.check(1L, "static-jobdata-visitjob-NULL-1", BLOB);
		dedup.written(1L, true);

		assertThat(dedup.check(2L, "static-jobdata-waitjob-NULL-2", null)).isEmpty();
		dedup.written(2L, true);
		assertThat(dedup.check(3L, "static-jobdata-waitjob-NULL-3", BLOB).map(SessionStorageRef::getJobId))
				.contains(1L);
	}

	@Test
	@DisplayName("the pointer serialises as the JSON readers follow")
	void pointerJson() {
		SessionStorageRef ref = new SessionStorageRef(717526L, "static-jobdata-waitjob-NULL-717526", "ab".repeat(32));

		assertThat(ref.toJson()).isEqualTo("{\"same_as_job_id\":717526,"
				+ "\"same_as_object\":\"static-jobdata-waitjob-NULL-717526\",\"sha256\":\"" + "ab".repeat(32) + "\"}");
	}
}
