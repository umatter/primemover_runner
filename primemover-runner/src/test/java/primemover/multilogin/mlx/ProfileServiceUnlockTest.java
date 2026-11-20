package primemover.multilogin.mlx;

import static org.junit.jupiter.api.Assertions.*;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.List;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.web.client.HttpServerErrorException;
import org.springframework.web.client.RestClientException;

import primemover.model.MultiLoginAgent;
import primemover.multilogin.mlx.api.LauncherApi;
import primemover.multilogin.mlx.model.Data;
import primemover.multilogin.mlx.model.StartBrowserProfile200Response;
import primemover.multilogin.mlx.model.Status;

/**
 * Experiment 240: Multilogin's cloud failed the lock release at the end of one
 * session of three bots, and every later start of those bots was refused with
 * LOCK_PROFILE_ERROR until the experiment ended.
 *
 * <p>
 * The launcher is a hand-written subclass, not a Mockito mock: Mockito cannot
 * mock the generated API classes on Java 25, which CI runs.
 */
class ProfileServiceUnlockTest {

	private static final String ID = "5b25dfc0-ff61-45b1-af5d-b92d5eb0acfe";

	/** Answers each start with the next scripted outcome; the last one repeats. */
	private static class ScriptedLauncher extends LauncherApi {
		final Deque<Object> script = new ArrayDeque<>();
		final List<String> started = new ArrayList<>();

		@Override
		public StartBrowserProfile200Response startBrowserProfile(String automationType, String headlessMode,
				String folderId, String profileId, String accept, String xStrictMode) throws RestClientException {
			started.add(profileId);
			Object next = script.size() > 1 ? script.poll() : script.peek();
			if (next instanceof RuntimeException) {
				throw (RuntimeException) next;
			}
			return (StartBrowserProfile200Response) next;
		}
	}

	private ScriptedLauncher launcher;
	private MultiLoginAgent agent;
	private ProfileService service;
	private final List<String> unlocked = new ArrayList<>();
	private boolean unlockAnswer = true;

	@BeforeEach
	void setUp() {
		launcher = new ScriptedLauncher();
		agent = new MultiLoginAgent();
		agent.setMultiLoginId(ID);
		service = new ProfileService(launcher, null, null, null, null, "folder");
		service.setProfileUnlocker(id -> {
			unlocked.add(id);
			return unlockAnswer;
		});
	}

	private static StartBrowserProfile200Response started() {
		return new StartBrowserProfile200Response().status(new Status().httpCode(200))
				.data(new Data().port("4444"));
	}

	@Test
	@DisplayName("a locked profile is unlocked by its id and started again")
	void lockedProfileIsUnlockedAndStarted() {
		launcher.script.add(MlxErrorsTest.locked());
		launcher.script.add(started());

		assertTrue(service.startProfile(agent, null).isSuccess());
		assertEquals(List.of(ID), unlocked);
		assertEquals(List.of(ID, ID), launcher.started);
	}

	@Test
	@DisplayName("a dead proxy is not answered with an unlock")
	void otherFailuresAreNotUnlocked() {
		launcher.script.add(MlxErrorsTest.proxyDead());

		assertThrows(RuntimeException.class, () -> service.startProfile(agent, null));
		assertTrue(unlocked.isEmpty());
		assertEquals(1, launcher.started.size());
	}

	@Test
	@DisplayName("a refused unlock fails the start with the original lock error, without a second start")
	void refusedUnlockKeepsTheLockError() {
		unlockAnswer = false;
		launcher.script.add(MlxErrorsTest.locked());

		HttpServerErrorException ex = assertThrows(HttpServerErrorException.class,
				() -> service.startProfile(agent, null));
		assertTrue(MlxErrors.isProfileLocked(ex));
		assertEquals(1, launcher.started.size());
	}

	@Test
	@DisplayName("a profile still locked after the unlock fails the start; one unlock per start")
	void stillLockedAfterUnlock() {
		launcher.script.add(MlxErrorsTest.locked());

		assertThrows(HttpServerErrorException.class, () -> service.startProfile(agent, null));
		assertEquals(1, unlocked.size());
		assertEquals(2, launcher.started.size());
	}

	@Test
	@DisplayName("without an unlocker (the switch off) nothing changes: the lock error surfaces")
	void switchedOff() {
		service.setProfileUnlocker(null);
		launcher.script.add(MlxErrorsTest.locked());

		assertThrows(HttpServerErrorException.class, () -> service.startProfile(agent, null));
		assertTrue(unlocked.isEmpty());
		assertEquals(1, launcher.started.size());
	}
}
