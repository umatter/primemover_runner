package primemover.capture;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.config.AutowireCapableBeanFactory;
import org.springframework.context.ApplicationContext;
import org.springframework.test.util.ReflectionTestUtils;

import java.net.InetAddress;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * A recorder whose setup throws after its listener is up is stopped before the
 * exception leaves the factory: the caller gets no recorder it could stop. The
 * same leak as a failed browser start, which on ex63 on 2026-10-08 had left
 * 1,399 listening sockets and 4,329 threads in the runner JVM.
 */
class RecordingProxyFactoryTest {

	private IRecorderProxy listener;
	private KeepAlivePingFactory pings;
	private RecordingProxyFactory factory;

	@BeforeEach
	void setUp() {
		listener = mock(IRecorderProxy.class);
		when(listener.initProxy(any())).thenReturn(true);
		when(listener.getAddress()).thenReturn(InetAddress.getLoopbackAddress());
		when(listener.getHttpPort()).thenReturn(40000);

		pings = mock(KeepAlivePingFactory.class);

		AutowireCapableBeanFactory beans = mock(AutowireCapableBeanFactory.class);
		doAnswer(invocation -> {
			Object bean = invocation.getArgument(0);
			ReflectionTestUtils.setField(bean, "recorderProxy", listener);
			ReflectionTestUtils.setField(bean, "keepAlivePingFactory", pings);
			ReflectionTestUtils.setField(bean, "verifyConnections", false);
			ReflectionTestUtils.setField(bean, "pingConnections", true);
			return null;
		}).when(beans).autowireBean(any(RecordingProxy.class));
		ApplicationContext spring = mock(ApplicationContext.class);
		when(spring.getAutowireCapableBeanFactory()).thenReturn(beans);

		factory = new RecordingProxyFactory();
		factory.setApplicationContext(spring);
	}

	@Test
	@DisplayName("a setup that fails after the listener started stops it, and the failure still reaches the caller")
	void failedSetupStopsTheListener() {
		RuntimeException pingFailed = new IllegalStateException("keep-alive ping could not start");
		when(pings.getInstance(any())).thenThrow(pingFailed);

		assertThatThrownBy(() -> factory.createRecordingProxy(null)).isSameAs(pingFailed);

		verify(listener).initProxy(null);
		verify(listener, times(1)).closeProxy(true);
	}

	@Test
	@DisplayName("a stop that fails as well does not hide the setup failure")
	void failedStopKeepsTheCause() {
		RuntimeException pingFailed = new IllegalStateException("keep-alive ping could not start");
		when(pings.getInstance(any())).thenThrow(pingFailed);
		doThrow(new IllegalStateException("abort failed")).when(listener).closeProxy(anyBoolean());

		assertThatThrownBy(() -> factory.createRecordingProxy(null)).isSameAs(pingFailed);
		assertThat(pingFailed.getSuppressed()).hasSize(1);
	}

	@Test
	@DisplayName("a setup that succeeds leaves the recorder running")
	void successfulSetupIsUntouched() {
		KeepAlivePing ping = mock(KeepAlivePing.class);
		when(pings.getInstance(any())).thenReturn(ping);

		RecordingProxy recorder = factory.createRecordingProxy(null);

		assertThat(recorder).isNotNull();
		verify(ping).start();
		verify(listener, never()).closeProxy(anyBoolean());
	}
}
