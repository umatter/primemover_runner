package primemover.capture;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;
import primemover.model.proxy.HttpProxy;

import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * A captcha worker solving "from the bot's address" has to use the very session
 * the browser egresses through -- the same decorated instance, hence the same
 * sticky session id and exit -- not a fresh decoration of the proxy record.
 */
class RecordingProxyUpstreamSessionTest {

	@Test
	@DisplayName("the upstream session is the recorder's own chained session instance")
	void returnsTheRecordersSession() {
		SessionDecorator session = new SessionDecorator(new HttpProxy("gate.example", 8080, "user", "pw"));
		IRecorderProxy recorder = mock(IRecorderProxy.class);
		when(recorder.getUpstreamSession()).thenReturn(Optional.of(session));
		RecordingProxy proxy = new RecordingProxy();
		ReflectionTestUtils.setField(proxy, "recorderProxy", recorder);

		assertThat(proxy.getUpstreamSession()).containsSame(session);
	}

	@Test
	@DisplayName("no upstream reported: empty")
	void noUpstream() {
		IRecorderProxy recorder = mock(IRecorderProxy.class);
		when(recorder.getUpstreamSession()).thenReturn(Optional.empty());
		RecordingProxy proxy = new RecordingProxy();
		ReflectionTestUtils.setField(proxy, "recorderProxy", recorder);

		assertThat(proxy.getUpstreamSession()).isEmpty();
	}
}
