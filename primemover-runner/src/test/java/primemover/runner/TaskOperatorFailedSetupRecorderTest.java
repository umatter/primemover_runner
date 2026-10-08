package primemover.runner;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.openqa.selenium.Proxy;
import org.openqa.selenium.WebDriver;
import org.openqa.selenium.remote.CapabilityType;
import org.openqa.selenium.remote.DesiredCapabilities;
import org.springframework.beans.factory.config.AutowireCapableBeanFactory;
import org.springframework.context.ApplicationContext;
import org.springframework.test.util.ReflectionTestUtils;
import primemover.anticaptcha.CaptchaSolverFactory;
import primemover.anticaptcha.ICaptchaSolver;
import primemover.capture.IRecorderProxy;
import primemover.capture.NoRecordingProxy;
import primemover.capture.ProxyDecoratorFactory;
import primemover.capture.RecordingProxy;
import primemover.capture.RecordingProxyFactory;
import primemover.capture.browserup.BrowserUpRecorderProxy;
import primemover.model.Crawler;
import primemover.model.MultiLoginAgent;
import primemover.model.MultiLoginVersion;
import primemover.model.proxy.HttpProxy;
import primemover.runner.context.MultiloginContext;
import primemover.service.IpServiceFactory;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.net.ServerSocket;
import java.time.Duration;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;
import java.util.function.Supplier;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.Mockito.RETURNS_DEEP_STUBS;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.spy;
import static org.mockito.Mockito.timeout;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * The recorder of a session setup that failed is stopped.
 *
 * <p>
 * Every startup round of {@link TaskOperator#prepareExeuction} creates a recorder
 * -- a BrowserUp/LittleProxy listener on an ephemeral port with its own Netty
 * threads -- and then starts the browser. When the start threw, no context held
 * the recorder and nothing ever stopped it. On ex63 on 2026-10-08, after about
 * 440 failed setups overnight (Multilogin launcher 400
 * GET_PROXY_CONNECTION_IP_ERROR, PROFILE_ALREADY_RUNNING), the runner JVM held
 * 4,329 threads and 1,399 listening sockets with about 22 browsers running; the
 * VM, same code and few failures, 291 threads and 218 sockets.
 *
 * <p>
 * No browser, no network: the context builder is a mock. The last test runs a
 * real BrowserUp recorder on a local ephemeral port.
 */
class TaskOperatorFailedSetupRecorderTest {

	private static final String LAUNCHER_400 = "400 Bad Request: {\"status\":{\"error_code\":\"GET_PROXY_CONNECTION_IP_ERROR\"}}";

	private TaskOperator operator;
	private IContextBuilder builder;
	private ICaptchaSolver captchaSolver;
	private RecordingProxyFactory recorderFactory;
	private Crawler crawler;

	/** The recorders the factory handed out, in round order. */
	private final List<RecordingProxy> created = Collections.synchronizedList(new ArrayList<>());

	/** "create N" / "stop N" in the order they happened. */
	private final List<String> events = Collections.synchronizedList(new ArrayList<>());

	/** What a recorder mock does when it is stopped; set per test before the run. */
	private Runnable onStop = () -> {
	};

	private static Crawler crawler() {
		Crawler crawler = new Crawler();
		crawler.setId(4711L);
		crawler.setAgent(new MultiLoginAgent(17L, "bot", null, null, "profile-uuid", MultiLoginVersion.MLX, null));
		return crawler;
	}

	private RecordingProxy mockRecorder() {
		RecordingProxy recorder = mock(RecordingProxy.class);
		int round = created.size() + 1;
		doAnswer(invocation -> {
			events.add("stop " + round + (Boolean.TRUE.equals(invocation.getArgument(0)) ? "" : " (graceful)"));
			onStop.run();
			return null;
		}).when(recorder).tearDown(anyBoolean());
		doAnswer(invocation -> {
			events.add("stop " + round + " (graceful)");
			return null;
		}).when(recorder).tearDown();
		return recorder;
	}

	/** A started browser of the given recorder, as the real builder returns it. */
	private MultiloginContext started(RecordingProxy recorder) {
		MultiloginContext ctx = new MultiloginContext();
		ctx.setCrawler(crawler);
		ctx.setRecordingProxy(recorder);
		ctx.setDriver(mock(WebDriver.class, RETURNS_DEEP_STUBS));
		return ctx;
	}

	@BeforeEach
	void setUp() {
		operator = new TaskOperator();
		builder = mock(IContextBuilder.class);
		captchaSolver = mock(ICaptchaSolver.class);
		crawler = crawler();

		recorderFactory = mock(RecordingProxyFactory.class);
		when(recorderFactory.createRecordingProxy(any())).thenAnswer(invocation -> {
			RecordingProxy recorder = mockRecorder();
			created.add(recorder);
			events.add("create " + created.size());
			return recorder;
		});

		CaptchaSolverFactory solvers = mock(CaptchaSolverFactory.class);
		when(solvers.getCaptchaSolver()).thenReturn(captchaSolver);

		ReflectionTestUtils.setField(operator, "recorderFactory", recorderFactory);
		ReflectionTestUtils.setField(operator, "contextBuilders", Map.of("MlxContextBuilder", builder));
		ReflectionTestUtils.setField(operator, "captchaSolverFac", solvers);
		ReflectionTestUtils.setField(operator, "ipServiceFactory", mock(IpServiceFactory.class));
		ReflectionTestUtils.setField(operator, "maxRetryCountInit", 3);
		ReflectionTestUtils.setField(operator, "pageLoadTimeoutSeconds", 60);
		// no backoff between rounds in a test
		ReflectionTestUtils.setField(operator, "startupRetryDelaySeconds", 0);
		ReflectionTestUtils.setField(operator, "rateLimitBackoffSeconds", 0);
	}

	@AfterEach
	void clearInterrupt() {
		Thread.interrupted();
	}

	private MultiloginContext context() {
		return (MultiloginContext) ReflectionTestUtils.getField(operator, "context");
	}

	private void browserStartFails() {
		when(builder.buildContext(any(), any(), any(), any())).thenThrow(new RuntimeException(LAUNCHER_400));
	}

	// ---- mocked recorders ----------------------------------------------------

	@Test
	@DisplayName("a browser start that fails in every round stops each round's recorder once, before the next round")
	void everyRoundFails() {
		browserStartFails();

		assertThat(operator.prepareExeuction(crawler)).isFalse();

		assertThat(events).containsExactly("create 1", "stop 1", "create 2", "stop 2", "create 3", "stop 3");
		for (RecordingProxy recorder : created) {
			verify(recorder, times(1)).tearDown(true);
			verify(recorder, never()).tearDown(false);
			verify(recorder, never()).tearDown();
		}
		assertThat(context()).isNull();
	}

	@Test
	@DisplayName("a start that succeeds in round 2 stops round 1's recorder and keeps round 2's for the session")
	void secondRoundSucceeds() {
		when(builder.buildContext(any(), any(), any(), any())).thenThrow(new RuntimeException(LAUNCHER_400))
				.thenAnswer(invocation -> started(invocation.getArgument(2)));

		assertThat(operator.prepareExeuction(crawler)).isTrue();

		assertThat(events).containsExactly("create 1", "stop 1", "create 2");
		assertThat(created).hasSize(2);
		verify(created.get(0), times(1)).tearDown(true);
		verify(created.get(1), never()).tearDown(anyBoolean());
		verify(created.get(1), never()).tearDown();
		assertThat(context().getRecordingProxy()).isSameAs(created.get(1));

		// the session's recorder ends with the session, as before: once, gracefully
		operator.tearDown(false);
		verify(created.get(1), times(1)).tearDown();
		assertThat(events).containsExactly("create 1", "stop 1", "create 2", "stop 2 (graceful)");
	}

	@Test
	@DisplayName("a recorder a context already holds is left to the next round's start, not stopped twice")
	void contextHoldsTheRecorder() {
		when(builder.buildContext(any(), any(), any(), any()))
				.thenAnswer(invocation -> started(invocation.getArgument(2)));
		// the browser started, then its set-up failed: the context holds round 1's recorder
		when(captchaSolver.setupDriver(any())).thenThrow(new RuntimeException("setupDriver failed")).thenReturn(true);

		assertThat(operator.prepareExeuction(crawler)).isTrue();

		// round 2 stops it at its start, as it always did -- and only then
		assertThat(events).containsExactly("create 1", "stop 1 (graceful)", "create 2");
		verify(created.get(0), never()).tearDown(anyBoolean());
		assertThat(context().getRecordingProxy()).isSameAs(created.get(1));
	}

	@Test
	@DisplayName("a stop that throws is logged; the setup goes on with its next round")
	void stopThrows() {
		browserStartFails();
		onStop = () -> {
			throw new IllegalStateException("Proxy server has not been started");
		};

		assertThat(operator.prepareExeuction(crawler)).isFalse();

		verify(builder, times(3)).buildContext(any(), any(), any(), any());
		assertThat(created).hasSize(3);
		for (RecordingProxy recorder : created) {
			verify(recorder, times(1)).tearDown(true);
		}
	}

	@Test
	@DisplayName("a stop that hangs holds a round up for its bound, no longer")
	void stopHangs() {
		browserStartFails();
		CountDownLatch never = new CountDownLatch(1);
		onStop = () -> {
			try {
				never.await(30, TimeUnit.SECONDS);
			} catch (InterruptedException e) {
				Thread.currentThread().interrupt();
			}
		};
		ReflectionTestUtils.setField(operator, "recorderStopBound", Duration.ofMillis(200));

		try {
			long start = System.nanoTime();
			assertThat(operator.prepareExeuction(crawler)).isFalse();
			Duration took = Duration.ofNanos(System.nanoTime() - start);

			// three rounds, each held at most 200 ms by its stop
			assertThat(took).isLessThan(Duration.ofSeconds(3));
			verify(builder, times(3)).buildContext(any(), any(), any(), any());
			for (RecordingProxy recorder : created) {
				verify(recorder, timeout(1000).times(1)).tearDown(true);
			}
		} finally {
			never.countDown();
		}
	}

	@Test
	@DisplayName("a cut-off (interrupted) worker still stops the recorder, and stays interrupted")
	void interruptedWorker() {
		browserStartFails();
		Thread.currentThread().interrupt();

		assertThat(operator.prepareExeuction(crawler)).isFalse();

		// the interrupt ends the retries at the backoff, after the stop
		assertThat(events).containsExactly("create 1", "stop 1");
		assertThat(Thread.currentThread().isInterrupted()).isTrue();
	}

	// ---- recorder.enabled=false ----------------------------------------------

	/**
	 * A factory as Spring wires it: each recorder gets a fresh implementation from
	 * {@code implementation} (the prototype bean), no connectivity check, no ping.
	 */
	private static RecordingProxyFactory realFactory(Supplier<IRecorderProxy> implementation) {
		AutowireCapableBeanFactory beans = mock(AutowireCapableBeanFactory.class);
		doAnswer(invocation -> {
			Object bean = invocation.getArgument(0);
			ReflectionTestUtils.setField(bean, "recorderProxy", implementation.get());
			ReflectionTestUtils.setField(bean, "verifyConnections", false);
			ReflectionTestUtils.setField(bean, "pingConnections", false);
			return null;
		}).when(beans).autowireBean(any(RecordingProxy.class));

		ApplicationContext spring = mock(ApplicationContext.class);
		when(spring.getAutowireCapableBeanFactory()).thenReturn(beans);

		RecordingProxyFactory factory = new RecordingProxyFactory();
		factory.setApplicationContext(spring);
		return factory;
	}

	@Test
	@DisplayName("recorder.enabled=false: the no-op recorder of a failed round is closed harmlessly, the session's is kept")
	void noRecordingProxyUnaffected() {
		HttpProxy upstream = new HttpProxy("127.0.0.1", 3128, "user", "secret");
		crawler.setProxy(upstream);

		ApplicationContext spring = mock(ApplicationContext.class, RETURNS_DEEP_STUBS);
		ProxyDecoratorFactory decorators = new ProxyDecoratorFactory();
		decorators.setApplicationContext(spring);
		List<NoRecordingProxy> implementations = new ArrayList<>();
		ReflectionTestUtils.setField(operator, "recorderFactory", realFactory(() -> {
			NoRecordingProxy none = spy(new NoRecordingProxy());
			ReflectionTestUtils.setField(none, "sessionDecoratorFactory", decorators);
			implementations.add(none);
			return none;
		}));

		List<DesiredCapabilities> capabilities = new ArrayList<>();
		when(builder.buildContext(any(), any(), any(), any())).thenThrow(new RuntimeException(LAUNCHER_400))
				.thenAnswer(invocation -> {
					capabilities.add(invocation.getArgument(1));
					return started(invocation.getArgument(2));
				});

		assertThat(operator.prepareExeuction(crawler)).isTrue();

		assertThat(implementations).hasSize(2);
		verify(implementations.get(0), times(1)).closeProxy(true);
		verify(implementations.get(1), never()).closeProxy(anyBoolean());

		// the browser still goes straight to the upstream, as without the fix
		Proxy proxy = (Proxy) capabilities.get(0).getCapability(CapabilityType.PROXY);
		assertThat(proxy.getHttpProxy()).isEqualTo("user:secret@127.0.0.1:3128");
	}

	// ---- a real recorder -----------------------------------------------------

	private static boolean portFree(int port) {
		try (ServerSocket probe = new ServerSocket()) {
			probe.setReuseAddress(false);
			probe.bind(new InetSocketAddress(port));
			return true;
		} catch (IOException e) {
			return false;
		}
	}

	private static long littleProxyThreads() {
		return Thread.getAllStackTraces().keySet().stream().filter(Thread::isAlive)
				.filter(t -> t.getName().startsWith("LittleProxy-")).count();
	}

	private static boolean eventually(Supplier<Boolean> condition) throws InterruptedException {
		long deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(10);
		while (System.nanoTime() < deadline) {
			if (condition.get()) {
				return true;
			}
			Thread.sleep(50);
		}
		return condition.get();
	}

	@Test
	@DisplayName("a real BrowserUp recorder of a failed setup closes its listener and ends its threads")
	void realRecorderListenerClosed() throws Exception {
		ReflectionTestUtils.setField(operator, "recorderFactory", realFactory(() -> {
			BrowserUpRecorderProxy browserUp = new BrowserUpRecorderProxy();
			ReflectionTestUtils.setField(browserUp, "recordingEnabled", true);
			ReflectionTestUtils.setField(browserUp, "certType", "PKCS12");
			ReflectionTestUtils.setField(browserUp, "certPath", "cert/2024/ca-cert.p12");
			ReflectionTestUtils.setField(browserUp, "certAlias", "primemoverrunner");
			ReflectionTestUtils.setField(browserUp, "certPassword", "");
			ReflectionTestUtils.setField(browserUp, "gracefullTeardownSeconds", 1);
			ReflectionTestUtils.setField(browserUp, "connectTimeoutSeconds", 5);
			ReflectionTestUtils.setField(browserUp, "iddleConnectionTimeoutSeconds", 5);
			ReflectionTestUtils.setField(browserUp, "requestTimeoutSeconds", 5);
			return browserUp;
		}));

		long threadsBefore = littleProxyThreads();
		List<Integer> ports = new ArrayList<>();
		AtomicInteger listening = new AtomicInteger();
		AtomicLong threadsWhileListening = new AtomicLong();
		when(builder.buildContext(any(), any(), any(), any())).thenAnswer(invocation -> {
			RecordingProxy recorder = invocation.getArgument(2);
			int port = recorder.getHttpPort();
			ports.add(port);
			if (!portFree(port)) {
				listening.incrementAndGet(); // the check sees a live listener
			}
			threadsWhileListening.accumulateAndGet(littleProxyThreads(), Math::max);
			throw new RuntimeException(LAUNCHER_400);
		});

		assertThat(operator.prepareExeuction(crawler)).isFalse();

		assertThat(ports).hasSize(3).doesNotContainNull();
		assertThat(listening.get()).as("each round's recorder was listening while the browser started").isEqualTo(3);
		assertThat(threadsWhileListening.get()).as("a live recorder runs LittleProxy threads")
				.isGreaterThan(threadsBefore);
		for (int port : ports) {
			assertThat(eventually(() -> portFree(port))).as("listener on port %d closed", port).isTrue();
		}
		assertThat(eventually(() -> littleProxyThreads() <= threadsBefore))
				.as("LittleProxy threads released (before: %d, now: %d)", threadsBefore, littleProxyThreads())
				.isTrue();
	}
}
