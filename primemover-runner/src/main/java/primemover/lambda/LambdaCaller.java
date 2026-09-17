package primemover.lambda;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.slf4j.MDC;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.web.client.RestTemplateBuilder;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.client.HttpStatusCodeException;
import org.springframework.web.client.ResourceAccessException;
import org.springframework.web.client.RestTemplate;
import primemover.runner.JobDiagnostics;
import software.amazon.awssdk.auth.credentials.AwsBasicCredentials;
import software.amazon.awssdk.auth.credentials.StaticCredentialsProvider;
import software.amazon.awssdk.core.SdkBytes;
import software.amazon.awssdk.core.client.config.ClientOverrideConfiguration;
import software.amazon.awssdk.core.exception.ApiCallAttemptTimeoutException;
import software.amazon.awssdk.core.exception.ApiCallTimeoutException;
import software.amazon.awssdk.http.urlconnection.UrlConnectionHttpClient;
import software.amazon.awssdk.regions.Region;
import software.amazon.awssdk.services.lambda.LambdaClient;
import software.amazon.awssdk.services.lambda.model.InvokeRequest;
import software.amazon.awssdk.services.lambda.model.InvokeResponse;
import software.amazon.awssdk.services.lambda.model.LogType;

import java.time.Duration;
import java.util.Base64;

public class LambdaCaller implements AutoCloseable {
	private static Logger logger = LoggerFactory.getLogger(LambdaCaller.class);

	@Value("${awsS3.accessKey}")
	private String accessKey;
	@Value("${awsS3.secretKey}")
	private String secretKey;

	// Lambda execution mode: "local" or "aws".
	//
	// Defaults to "local", because that is what every current deployment runs and
	// because the failure modes are not symmetric. Losing the lambda.mode line from
	// application.properties used to route decisions to AWS silently: the jobs keep
	// succeeding, so nothing looks wrong, while the answers come from whatever image
	// was last pushed to ECR -- which today predates both the camelCase-selector and
	// the aria-hidden click-target fixes. Defaulting the other way fails loudly
	// instead, against a local server that is either running or obviously not.
	@Value("${lambda.mode:local}")
	private String lambdaMode;

	// Local Lambda server URL (used when lambda.mode=local)
	@Value("${lambda.local.url:http://localhost:5001/invoke}")
	private String localLambdaUrl;

	// Local Lambda server timeouts (used when lambda.mode=local). See
	// initLocalClient() for why these exist and how the values were chosen.
	@Value("${lambda.local.connectTimeoutSeconds:5}")
	private int localConnectTimeoutSeconds;
	@Value("${lambda.local.readTimeoutSeconds:480}")
	private int localReadTimeoutSeconds;

	@Value("${awsLambda.apiCallTimeoutSeconds:120}")
	private int apiCallTimeoutSeconds;
	@Value("${awsLambda.apiCallAttemptTimeoutSeconds:90}")
	private int apiCallAttemptTimeoutSeconds;

	private Region region = Region.EU_CENTRAL_1;

	// API call timeouts are configurable via awsLambda.* above. The transport-level
	// timeouts below are not: the SDK default socket timeout is 30s, far too short for
	// a Lambda that averages 40s and can reach 60s, so it must stay above them.
	private static final Duration SOCKET_TIMEOUT = Duration.ofSeconds(90);
	private static final Duration CONNECTION_TIMEOUT = Duration.ofSeconds(10);

	// Used when the lambda.local.*TimeoutSeconds properties are absent or
	// non-positive -- including when a LambdaCaller is built outside Spring, where an
	// uninjected int is 0 and 0 means "wait forever" to the underlying request
	// factory. There is deliberately no way to configure an unbounded wait.
	private static final int DEFAULT_LOCAL_CONNECT_TIMEOUT_SECONDS = 5;
	private static final int DEFAULT_LOCAL_READ_TIMEOUT_SECONDS = 480;

	// Longest error-response body quoted into a log line and a LambdaException
	// message. That message ends up in the API's status_message, which the job
	// service truncates at 1800 characters.
	private static final int MAX_ERROR_BODY_CHARS = 500;

	// Header the local decision server reads its per-request correlation id from
	// (orchestrator/lambda_service/server.py::_correlation_id, which falls back to
	// X-Job-Id and then to a generated id). Without it the server's own request
	// logging is real but unjoinable: every line carries an id that exists nowhere
	// else, so a decision cannot be tied back to the job that asked for it.
	private static final String CORRELATION_ID_HEADER = "X-Correlation-Id";

	// Header the local decision server reads to pick which registered function to
	// dispatch to (orchestrator/lambda_service/server.py). A null/blank name omits
	// the header entirely, so the server falls back to its own registry default --
	// which is exactly today's single-function behaviour, and what an un-rebuilt
	// runner already gets for free.
	private static final String FUNCTION_NAME_HEADER = "X-Function-Name";

	// MDC keys the id is assembled from. correlationId is put there by
	// JobListRunner and extended with |j-<id> by TaskOperator for the duration of
	// each job, so on the production path it is already the canonical value; the
	// individual keys are the fallback for a caller invoked outside that scope.
	private static final String MDC_CORRELATION_ID = "correlationId";
	private static final String MDC_CRAWLER_ID = "crawler.id";
	private static final String MDC_JOBLIST_ID = "joblist.id";
	private static final String MDC_JOB_ID = "job.id";

	private LambdaClient awsClient = null;
	private RestTemplate localClient = null;

	// The timeouts the local client was actually built with, so a failure can name
	// them instead of quoting a property that may not have been injected.
	private Duration localConnectTimeout = Duration.ofSeconds(DEFAULT_LOCAL_CONNECT_TIMEOUT_SECONDS);
	private Duration localReadTimeout = Duration.ofSeconds(DEFAULT_LOCAL_READ_TIMEOUT_SECONDS);

	public LambdaCaller() {

	}

	private void initAwsClient() {
		if (awsClient != null) {
			return;
		}

		AwsBasicCredentials awsCreds = AwsBasicCredentials.create(this.accessKey, this.secretKey);

		ClientOverrideConfiguration clientConfig = ClientOverrideConfiguration.builder()
				.apiCallTimeout(Duration.ofSeconds(apiCallTimeoutSeconds))
				.apiCallAttemptTimeout(Duration.ofSeconds(apiCallAttemptTimeoutSeconds))
				.build();

		// Transport-level timeouts: the SDK default socket timeout (30s) is shorter than a
		// typical Lambda call, so it must be raised independently of the API call timeouts.
		UrlConnectionHttpClient.Builder httpClientBuilder = UrlConnectionHttpClient.builder()
				.socketTimeout(SOCKET_TIMEOUT)
				.connectionTimeout(CONNECTION_TIMEOUT);

		logger.debug("initializing AWS client with apiCallTimeout={}s, apiCallAttemptTimeout={}s, socketTimeout={}s",
				apiCallTimeoutSeconds, apiCallAttemptTimeoutSeconds, SOCKET_TIMEOUT.getSeconds());
		this.awsClient = LambdaClient.builder()
				.region(this.region)
				.credentialsProvider(StaticCredentialsProvider.create(awsCreds))
				.overrideConfiguration(clientConfig)
				.httpClientBuilder(httpClientBuilder)
				.build();
	}

	private void initLocalClient() {
		if (localClient != null) {
			return;
		}

		// A bare `new RestTemplate()` has NO connect and NO read timeout, which is not
		// the risk it sounds like on localhost: the decision function runs in a Flask
		// worker that calls OpenAI, and a worker that wedges (a hung socket, a stalled
		// upstream) never answers and never closes. Without a read timeout the calling
		// thread waits forever -- and that thread is one of runner.MaxParallelExecution
		// browser threads, so a handful of wedged decisions starves the pool and the
		// runner stops picking up queues while looking perfectly healthy.
		//
		// Read timeout: a slow-but-working decision must never be killed, or the fix
		// would cost more data than the bug -- so the budget is computed from the
		// decision function's own limits rather than guessed. It gives OpenAI
		// openai_api_timeout_secs=60 per call AND openai_api_max_retries=1, i.e. two
		// attempts, so ONE call site can legitimately take 120s. An earlier version of
		// this comment multiplied 60s by four call sites and stopped there; the retry
		// is the term it dropped, and with it the unbounded worst case is 4 x 120s =
		// 480s, above the 300s that was set here. That would not have hung a job: it
		// would have hard-failed a decision that was merely slow, which is worse.
		//
		// The decision function now abandons the remaining call sites once a failure
		// is classified fatal, bounding a legitimate invocation to roughly two call
		// sites' worth of retries: 2 x 120s = 240s of OpenAI wall time, plus page
		// parsing and response assembly over a full YouTube page (budget 60s) = ~300s
		// worst legitimate case. 480s therefore leaves ~60% headroom while still
		// bounding a wedged worker to 8 minutes rather than forever.
		//
		// Connect timeout: the server is on loopback, so connecting either succeeds
		// immediately or is not going to; 5s is generous.
		this.localConnectTimeout = Duration.ofSeconds(
				localConnectTimeoutSeconds > 0 ? localConnectTimeoutSeconds : DEFAULT_LOCAL_CONNECT_TIMEOUT_SECONDS);
		this.localReadTimeout = Duration.ofSeconds(
				localReadTimeoutSeconds > 0 ? localReadTimeoutSeconds : DEFAULT_LOCAL_READ_TIMEOUT_SECONDS);

		logger.debug("initializing local Lambda client with URL: {}, connectTimeout={}s, readTimeout={}s",
				localLambdaUrl, localConnectTimeout.getSeconds(), localReadTimeout.getSeconds());
		this.localClient = new RestTemplateBuilder()
				.connectTimeout(localConnectTimeout)
				.readTimeout(localReadTimeout)
				.build();
	}

	/**
	 * Invoke the Lambda function (unified interface for both local and AWS).
	 * Returns a LambdaInvokeResult that works with both modes.
	 */
	public LambdaInvokeResult invoke(String functionName, String param) throws LambdaException {
		if ("local".equalsIgnoreCase(lambdaMode)) {
			return invokeLocal(functionName, param);
		}
		if ("aws".equalsIgnoreCase(lambdaMode)) {
			return invokeAws(functionName, param);
		}
		// Deliberately not an else-branch onto AWS. This used to be
		// `else { invokeAws(...) }`, which meant a typo -- "locl", "Local " with a
		// trailing space, a commented-out line -- quietly sent every decision to AWS
		// and looked like success. An unrecognised mode is a configuration error, so
		// say so rather than picking a remote billed service on the caller's behalf.
		throw new LambdaException(
				"unknown lambda.mode '" + lambdaMode + "'; expected \"local\" or \"aws\"");
	}

	/**
	 * Invoke the Lambda function via AWS Lambda service.
	 */
	private LambdaInvokeResult invokeAws(String functionName, String param) throws LambdaException {
		this.initAwsClient();

		if (this.awsClient == null) {
			throw new LambdaException("Lambda client is not initialized");
		}

		try {
			logger.info("calling AWS Lambda function: {}", functionName);
			SdkBytes payload = SdkBytes.fromUtf8String(param);

			// Setup an InvokeRequest with CloudWatch log capture
			InvokeRequest request = InvokeRequest.builder()
					.functionName(functionName)
					.payload(payload)
					.logType(LogType.TAIL) // Request last 4KB of CloudWatch logs
					.build();

			logger.debug("invoking AWS function");
			InvokeResponse res = this.awsClient.invoke(request);

			// Extract and log CloudWatch logs if available
			if (res.logResult() != null && !res.logResult().isEmpty()) {
				try {
					String decodedLogs = new String(Base64.getDecoder().decode(res.logResult()));
					logger.info("Lambda CloudWatch logs for '{}': \n{}", functionName, decodedLogs);
				} catch (Exception logEx) {
					logger.warn("Failed to decode Lambda CloudWatch logs", logEx);
				}
			}

			// Enhanced error logging
			if (res.functionError() != null && !res.functionError().isEmpty()) {
				logger.error("Lambda function error type: {}", res.functionError());
				logger.error("Lambda status code: {}", res.statusCode());

				// Log the response payload which may contain error details
				String responsePayload = res.payload().asUtf8String();
				logger.error("Lambda error payload: {}", responsePayload);

				// Log execution details
				if (res.executedVersion() != null) {
					logger.error("Lambda executed version: {}", res.executedVersion());
				}
			}

			// Convert to our unified result type
			return LambdaInvokeResult.builder()
					.statusCode(res.statusCode())
					.payload(res.payload().asUtf8String())
					.functionError(res.functionError())
					.build();

		} catch (ApiCallTimeoutException ex) {
			logger.error("Lambda invocation timed out after {}s (total timeout). Function: {}",
					apiCallTimeoutSeconds, functionName, ex);
			throw new LambdaException("Lambda invocation exceeded total timeout of " +
					apiCallTimeoutSeconds + "s", ex);
		} catch (ApiCallAttemptTimeoutException ex) {
			logger.error("Lambda invocation attempt timed out after {}s (per-attempt timeout). Function: {}",
					apiCallAttemptTimeoutSeconds, functionName, ex);
			throw new LambdaException("Lambda invocation attempt exceeded timeout of " +
					apiCallAttemptTimeoutSeconds + "s", ex);
		} catch (Exception ex) {
			logger.error("error invoking AWS function", ex);
			throw new LambdaException("Failure calling Lambda function", ex);
		}
	}

	/**
	 * Invoke the Lambda function via local HTTP server.
	 * The local server mirrors the AWS Lambda interface, dispatching to the
	 * named function (see {@link #FUNCTION_NAME_HEADER}) when the registry
	 * behind it serves more than one.
	 */
	private LambdaInvokeResult invokeLocal(String functionName, String param) throws LambdaException {
		this.initLocalClient();

		try {
			logger.info("calling local Lambda server at: {}", localLambdaUrl);

			HttpHeaders headers = new HttpHeaders();
			headers.setContentType(MediaType.APPLICATION_JSON);

			// Tie the server's log lines to this job. The decision server logs every
			// request under this id, so with it the two halves of one decision --
			// "runner asked" and "function answered/failed" -- join in Loki on the same
			// correlationId the runner's own lines carry.
			String correlationId = currentCorrelationId();
			if (correlationId != null) {
				headers.set(CORRELATION_ID_HEADER, correlationId);
			}

			// A null/blank name sends no header at all, so an old server (or a new one
			// with no registry configured) sees exactly what it always has.
			if (functionName != null && !functionName.isBlank()) {
				headers.set(FUNCTION_NAME_HEADER, functionName);
			}

			HttpEntity<String> request = new HttpEntity<>(param, headers);

			logger.debug("invoking local function with correlationId={}, functionName={}", correlationId,
					functionName);
			// A non-2xx never arrives here: RestTemplate's DefaultResponseErrorHandler
			// raises HttpStatusCodeException first. The status check that used to stand
			// on this spot was therefore dead code, and the lambda server's
			// {"error": ...} body -- the only place that says WHY the decision failed --
			// survived nowhere but inside Spring's own exception message. It is pulled
			// out in the catch below instead.
			ResponseEntity<String> response = localClient.postForEntity(localLambdaUrl, request, String.class);

			String responseBody = response.getBody();
			logger.debug("local Lambda response received, length: {}", responseBody != null ? responseBody.length() : 0);

			// Return as our unified result type
			return LambdaInvokeResult.builder()
					.statusCode(response.getStatusCode().value())
					.payload(responseBody != null ? responseBody : "")
					.functionError(null)
					.build();

		} catch (HttpStatusCodeException ex) {
			// The response body is the diagnosis: the lambda server answers a hard
			// failure with {"error": "<what went wrong>"}. Carry it into the exception
			// message so it reaches both the runner log and, via the job's
			// status_message, the API and the GUI.
			String body = shorten(ex.getResponseBodyAsString(), MAX_ERROR_BODY_CHARS);
			logger.error("local Lambda server at {} returned HTTP {}: {}", localLambdaUrl,
					ex.getStatusCode().value(), body, ex);
			throw new LambdaException("Local Lambda call to " + localLambdaUrl + " failed with HTTP "
					+ ex.getStatusCode().value() + (body == null ? "" : ": " + body), ex);
		} catch (ResourceAccessException ex) {
			// No response at all: connection refused (server down), or one of the
			// timeouts configured in initLocalClient() fired.
			logger.error("local Lambda server at {} did not answer (connectTimeout={}s, readTimeout={}s)",
					localLambdaUrl, localConnectTimeout.getSeconds(), localReadTimeout.getSeconds(), ex);
			throw new LambdaException("Local Lambda call to " + localLambdaUrl + " did not answer: "
					+ detailOf(ex) + " (connectTimeout=" + localConnectTimeout.getSeconds() + "s, readTimeout="
					+ localReadTimeout.getSeconds() + "s)", ex);
		} catch (Exception ex) {
			logger.error("error invoking local Lambda server at {}", localLambdaUrl, ex);
			throw new LambdaException(
					"Failure calling local Lambda function at " + localLambdaUrl + ": " + detailOf(ex), ex);
		}
	}

	/**
	 * The canonical {@code exp-|cr-|q-|j-} id of the job being executed on this
	 * thread, or {@code null} when this call belongs to no job.
	 *
	 * <p>
	 * Read from the MDC rather than passed in as an argument: the id is already
	 * maintained there for the log lines, {@code invoke()} is called from several
	 * handlers, and a second copy threaded through the signature is a second thing
	 * to keep in sync. The individual-key fallback covers a caller running outside
	 * {@code JobListRunner}'s scope, where the composite key is not set.
	 *
	 * <p>
	 * Package-private for the test.
	 */
	static String currentCorrelationId() {
		String fromMdc = MDC.get(MDC_CORRELATION_ID);
		if (fromMdc != null && !fromMdc.isBlank()) {
			return fromMdc.strip();
		}

		return JobDiagnostics.correlationId(null, longFromMdc(MDC_CRAWLER_ID), longFromMdc(MDC_JOBLIST_ID),
				longFromMdc(MDC_JOB_ID));
	}

	private static Long longFromMdc(String key) {
		String value = MDC.get(key);
		if (value == null || value.isBlank()) {
			return null;
		}

		try {
			return Long.valueOf(value.strip());
		} catch (NumberFormatException ex) {
			// the MDC is free-form; a value that is not an id is simply not one
			return null;
		}
	}

	/**
	 * A single-line description of a throwable, falling back to its class name when
	 * it carries no message -- "did not answer: null" helps nobody.
	 */
	private static String detailOf(Throwable t) {
		String message = shorten(t.getMessage(), MAX_ERROR_BODY_CHARS);
		return message == null ? t.getClass().getSimpleName() : message;
	}

	/**
	 * Collapse a value to the single truncated line that may travel in an exception
	 * message: it ends up in the API's status_message, one field in one column.
	 *
	 * @return the collapsed text, or {@code null} when there is nothing to say
	 */
	private static String shorten(String value, int limit) {
		if (value == null) {
			return null;
		}

		String text = value.replaceAll("\\s+", " ").strip();
		if (text.isEmpty()) {
			return null;
		}

		return text.length() > limit ? text.substring(0, limit - 3) + "..." : text;
	}

	@Override
	public void close() throws Exception {
		if (this.awsClient != null) {
			this.awsClient.close();
			this.awsClient = null;
		}
		this.localClient = null;
	}
}
