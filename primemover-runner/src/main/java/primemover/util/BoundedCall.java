package primemover.util;

import java.time.Duration;
import java.util.Map;
import java.util.concurrent.Callable;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;

import org.apache.commons.lang3.concurrent.BasicThreadFactory;
import org.slf4j.MDC;

/**
 * Runs one call against a browser that may be frozen, and stops waiting for it
 * after a bound.
 *
 * <p>
 * Why this exists. A frozen renderer does not fail a WebDriver command, it holds
 * it. On experiment 189 (2026-10-07) the recovery after a page-load timeout sent
 * {@code window.stop()} and then {@code about:blank} to such a browser, and each
 * call came back only when ChromeDriver gave up ("Timed out receiving message
 * from renderer: 58.5"); the HTTP client behind a RemoteWebDriver waits up to
 * three minutes, the Multilogin API client without any limit. Recovering a
 * session means trying several things in turn, so each of them gets a wall-time
 * bound instead of whatever the client allows.
 *
 * <p>
 * The call runs on a daemon thread and carries the caller's MDC, so its log
 * lines keep the crawler and joblist ids. When the bound runs out, or the caller
 * is interrupted (the processing-timeout abort), the caller gets the exception
 * at once while the call itself may still be blocked.
 */
public final class BoundedCall {

	private static final ExecutorService CALLS = Executors.newCachedThreadPool(
			new BasicThreadFactory.Builder().namingPattern("bounded-call-%d").daemon(true).build());

	private BoundedCall() {
	}

	/**
	 * Runs the call and waits at most {@code bound} for it. A call still running
	 * after that is interrupted, which lets an interruptible wait inside it end
	 * early; nothing it returns later is used.
	 *
	 * @throws java.util.concurrent.TimeoutException when the bound ran out
	 * @throws InterruptedException                  when the caller was
	 *                                               interrupted while waiting;
	 *                                               its interrupt flag is set
	 *                                               again
	 * @throws Exception                             what the call itself threw
	 */
	public static <T> T call(Callable<T> call, Duration bound) throws Exception {
		CompletableFuture<T> result = new CompletableFuture<>();
		Future<?> task = submit(call, result);
		try {
			return await(result, bound);
		} catch (java.util.concurrent.TimeoutException | InterruptedException e) {
			task.cancel(true);
			throw e;
		}
	}

	/**
	 * Starts the call and hands back its future, for a call whose result must
	 * not be lost when the caller stops waiting: a started browser that turns up
	 * after the bound still has to be released. Wait for it with
	 * {@link #await(CompletableFuture, Duration)}. Such a call is never
	 * interrupted, because cutting the client side of a request short could leave
	 * behind exactly what the caller means to release.
	 */
	public static <T> CompletableFuture<T> start(Callable<T> call) {
		CompletableFuture<T> result = new CompletableFuture<>();
		submit(call, result);
		return result;
	}

	/**
	 * Waits at most {@code bound} for a started call. Stopping to wait leaves the
	 * call running.
	 *
	 * @throws java.util.concurrent.TimeoutException when the bound ran out
	 * @throws InterruptedException                  when the caller was
	 *                                               interrupted while waiting;
	 *                                               its interrupt flag is set
	 *                                               again
	 * @throws Exception                             what the call itself threw
	 */
	public static <T> T await(CompletableFuture<T> started, Duration bound) throws Exception {
		try {
			return started.get(bound.toMillis(), TimeUnit.MILLISECONDS);
		} catch (ExecutionException e) {
			Throwable cause = e.getCause();
			if (cause instanceof Exception) {
				throw (Exception) cause;
			}
			throw e;
		} catch (InterruptedException e) {
			Thread.currentThread().interrupt();
			throw e;
		}
	}

	private static <T> Future<?> submit(Callable<T> call, CompletableFuture<T> result) {
		Map<String, String> mdc = MDC.getCopyOfContextMap();
		return CALLS.submit(() -> {
			if (mdc != null) {
				MDC.setContextMap(mdc);
			}
			try {
				result.complete(call.call());
			} catch (Throwable t) {
				result.completeExceptionally(t);
			} finally {
				MDC.clear();
			}
		});
	}
}
