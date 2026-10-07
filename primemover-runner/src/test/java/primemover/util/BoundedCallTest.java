package primemover.util;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * A frozen browser holds a WebDriver call for as long as the client allows
 * (experiment 189, 2026-10-07), so every recovery step is bounded in wall time.
 */
class BoundedCallTest {

	@Test
	@DisplayName("a call that answers in time returns its result")
	void answersInTime() throws Exception {
		assertThat(BoundedCall.call(() -> "ok", Duration.ofSeconds(5))).isEqualTo("ok");
	}

	@Test
	@DisplayName("the call's own exception reaches the caller unwrapped")
	void ownExceptionIsUnwrapped() {
		assertThatThrownBy(() -> BoundedCall.call(() -> {
			throw new IllegalStateException("no such window");
		}, Duration.ofSeconds(5))).isInstanceOf(IllegalStateException.class).hasMessage("no such window");
	}

	@Test
	@DisplayName("a call that hangs is given up on at the bound and interrupted")
	void hangingCallIsCutAtTheBound() throws Exception {
		CountDownLatch interrupted = new CountDownLatch(1);
		long started = System.nanoTime();

		assertThatThrownBy(() -> BoundedCall.call(() -> {
			try {
				Thread.sleep(30_000);
			} catch (InterruptedException e) {
				interrupted.countDown();
			}
			return null;
		}, Duration.ofMillis(200))).isInstanceOf(java.util.concurrent.TimeoutException.class);

		assertThat(Duration.ofNanos(System.nanoTime() - started)).isLessThan(Duration.ofSeconds(5));
		assertThat(interrupted.await(5, TimeUnit.SECONDS)).isTrue();
	}

	@Test
	@DisplayName("a started call that finishes after the caller gave up still hands over its result")
	void lateResultIsHandedOver() throws Exception {
		CountDownLatch release = new CountDownLatch(1);
		CompletableFuture<String> started = BoundedCall.start(() -> {
			release.await(5, TimeUnit.SECONDS);
			return "browser";
		});

		assertThatThrownBy(() -> BoundedCall.await(started, Duration.ofMillis(100)))
				.isInstanceOf(java.util.concurrent.TimeoutException.class);

		AtomicReference<String> late = new AtomicReference<>();
		CompletableFuture<Void> handedOver = started.thenAccept(late::set);
		release.countDown();
		handedOver.get(5, TimeUnit.SECONDS);

		assertThat(late.get()).isEqualTo("browser");
	}

	@Test
	@DisplayName("an interrupted caller stops waiting at once and keeps its interrupt flag")
	void interruptedCallerStops() {
		Thread.currentThread().interrupt();
		try {
			assertThatThrownBy(() -> BoundedCall.call(() -> {
				Thread.sleep(30_000);
				return null;
			}, Duration.ofSeconds(30))).isInstanceOf(InterruptedException.class);
			assertThat(Thread.currentThread().isInterrupted()).isTrue();
		} finally {
			Thread.interrupted();
		}
	}
}
