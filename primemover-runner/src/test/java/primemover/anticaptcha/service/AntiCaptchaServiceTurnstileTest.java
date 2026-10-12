package primemover.anticaptcha.service;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestTemplate;

import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.jsonPath;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.method;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withServerError;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;

/**
 * The HTTP half of the Turnstile solve, against a mocked api.anti-captcha.com:
 * the task has to carry Bing's action and cData, and every way the service can
 * fail has to come back as "no token", never as an exception that ends the job.
 */
class AntiCaptchaServiceTurnstileTest {

	private static final String ENDPOINT = "https://api.anti-captcha.test";

	private AntiCaptchaService service;
	private MockRestServiceServer server;

	@BeforeEach
	void setUp() {
		RestTemplate restTemplate = new RestTemplate();
		server = MockRestServiceServer.bindTo(restTemplate).build();

		service = new AntiCaptchaService();
		ReflectionTestUtils.setField(service, "restTemplate", restTemplate);
		ReflectionTestUtils.setField(service, "apiKey", "test-client-key");
		ReflectionTestUtils.setField(service, "endpoint", ENDPOINT);
		ReflectionTestUtils.setField(service, "maxSolvingTimeoutSeconds", 5);
		ReflectionTestUtils.setField(service, "initialPollDelayMillis", 0L);
		ReflectionTestUtils.setField(service, "pollIntervalMillis", 0L);
	}

	@Test
	@DisplayName("creates a TurnstileTaskProxyless with Bing's action and cData and returns the token")
	void solvesWithActionAndCData() {
		server.expect(requestTo(ENDPOINT + "/createTask")).andExpect(method(HttpMethod.POST))
				.andExpect(jsonPath("$.clientKey").value("test-client-key"))
				.andExpect(jsonPath("$.task.type").value("TurnstileTaskProxyless"))
				.andExpect(jsonPath("$.task.websiteURL").value("https://www.bing.com/search?q=x"))
				.andExpect(jsonPath("$.task.websiteKey").value("0x4AAAAAABgNScUsbCFCjwIZ"))
				.andExpect(jsonPath("$.task.action").value("CC7527"))
				.andExpect(jsonPath("$.task.cData").value("AEE96B5FD39F4A399BEB7847BE9FD66C"))
				.andExpect(jsonPath("$.task.websiteSToken").doesNotExist())
				.andRespond(withSuccess("{\"errorId\":0,\"taskId\":7654321}", MediaType.APPLICATION_JSON));
		server.expect(requestTo(ENDPOINT + "/getTaskResult"))
				.andExpect(jsonPath("$.taskId").value(7654321))
				.andRespond(withSuccess("{\"errorId\":0,\"status\":\"processing\"}", MediaType.APPLICATION_JSON));
		server.expect(requestTo(ENDPOINT + "/getTaskResult"))
				.andRespond(withSuccess("{\"errorId\":0,\"status\":\"ready\",\"cost\":\"0.00200\","
						+ "\"solution\":{\"token\":\"0.tok-en_value\",\"userAgent\":\"Mozilla/5.0\"}}",
						MediaType.APPLICATION_JSON));

		Optional<String> token = service.solveTurnstileProxyless("https://www.bing.com/search?q=x",
				"0x4AAAAAABgNScUsbCFCjwIZ", "CC7527", "AEE96B5FD39F4A399BEB7847BE9FD66C");

		assertThat(token).contains("0.tok-en_value");
		server.verify();
	}

	@Test
	@DisplayName("a createTask error (e.g. zero balance) is no token, not an exception")
	void createTaskErrorIsNoToken() {
		server.expect(requestTo(ENDPOINT + "/createTask"))
				.andRespond(withSuccess("{\"errorId\":10,\"errorCode\":\"ERROR_ZERO_BALANCE\","
						+ "\"errorDescription\":\"Account has zero balance\"}", MediaType.APPLICATION_JSON));

		assertThat(service.solveTurnstileProxyless("https://www.bing.com/search?q=x", "0x4AAAAAABgNScUsbCFCjwIZ",
				"CC7527", "AEE96B5FD39F4A399BEB7847BE9FD66C")).isEmpty();
	}

	@Test
	@DisplayName("a task the workers could not solve is no token")
	void unsolvableTaskIsNoToken() {
		server.expect(requestTo(ENDPOINT + "/createTask"))
				.andRespond(withSuccess("{\"errorId\":0,\"taskId\":1}", MediaType.APPLICATION_JSON));
		server.expect(requestTo(ENDPOINT + "/getTaskResult"))
				.andRespond(withSuccess("{\"errorId\":12,\"errorCode\":\"ERROR_CAPTCHA_UNSOLVABLE\"}",
						MediaType.APPLICATION_JSON));

		assertThat(service.solveTurnstileProxyless("https://www.bing.com/search?q=x", "0x4AAAAAABgNScUsbCFCjwIZ",
				"CC7527", "AEE96B5FD39F4A399BEB7847BE9FD66C")).isEmpty();
	}

	@Test
	@DisplayName("an unreachable or failing service is no token")
	void serverErrorIsNoToken() {
		server.expect(requestTo(ENDPOINT + "/createTask")).andRespond(withServerError());

		assertThat(service.solveTurnstileProxyless("https://www.bing.com/search?q=x", "0x4AAAAAABgNScUsbCFCjwIZ",
				"CC7527", "AEE96B5FD39F4A399BEB7847BE9FD66C")).isEmpty();
	}
}
