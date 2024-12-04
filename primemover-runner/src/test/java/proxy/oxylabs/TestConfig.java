package proxy.oxylabs;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.time.Duration;
import java.util.ArrayList;
import java.util.List;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.web.client.RestTemplateBuilder;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Profile;
import org.springframework.http.client.ClientHttpRequestInterceptor;
import org.springframework.http.converter.HttpMessageConverter;
import org.springframework.http.converter.json.MappingJackson2HttpMessageConverter;
import org.springframework.util.CollectionUtils;
import org.springframework.web.client.RestTemplate;

import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.ObjectMapper;

import primemover.anticaptcha.CaptchaSolverFactory;
import primemover.anticaptcha.service.AntiCaptchaService;
import primemover.capture.IRecorderProxy;
import primemover.capture.KeepAlivePingFactory;
import primemover.capture.ProxyDecoratorFactory;
import primemover.capture.RecordingProxyFactory;
import primemover.capture.UrlVerificatorFactory;
import primemover.capture.wiremock.WireMockRecorderProxy;
import primemover.lambda.LambdaCallerFactory;
import primemover.multilogin.MultiLoginBrowserProfileFactory;
import primemover.multilogin.MultiLoginDefaultTemplateLoader;
import primemover.runner.TaskExecutor;
import primemover.runner.TaskOperatorFactory;
import primemover.runner.job.JobDispatcher;
import primemover.runner.joblist.JobListRunnerFactory;
import primemover.service.AgentService;
import primemover.service.FileStorageService;
import primemover.service.IJobService;
import primemover.service.IStorageService;
import primemover.service.IpServiceFactory;
import primemover.service.TermsService;
import primemover.service.token.TokenGenerator;
import primemover.service.token.TokenRequestInterceptor;
import primemover.util.SelectingHelper;

@Profile("test")
@Configuration
public class TestConfig {

	@Value("${backend.requestTimeoutSeconds:60}")
	private Long requestTimeoutSeconds;

	@Value("${backend.connectionTimeoutSeconds:10}")
	private Long connectionTimeoutSeconds;

	@Value("${global.mode}")
	private String mode;

	@Bean(name = "JobService")
	IJobService getJobService() {
		IJobService mock = mock(IJobService.class);
		when(mock.updateCaptchaStateOnJob(any(), any(), any())).thenReturn(true);
		when(mock.updateJoblistState(any(), any(), any(), any(), any(), any())).thenReturn(true);
		when(mock.updateJobState(any(), any(), any(), any(), any(), any())).thenReturn(true);

		return mock;
	}

	@Bean(name = "IpServiceFactory")
	public IpServiceFactory getIpServiceFactory() {
		return new IpServiceFactory();
	}

	@Bean(name = "CaptchaSolverFactory")
	public CaptchaSolverFactory getCaptchaSolverFactory() {
		return new CaptchaSolverFactory();
	}

	@Bean(name = "AgentService")
	AgentService getAgentService() {
		return new AgentService();
	}

	@Bean("TermsService")
	TermsService getTermsService() {
		return new TermsService();
	}

	@Bean(name = "MultiLoginBrowserProfileFactory")
	MultiLoginBrowserProfileFactory getMultiLoginBrowserProfileFactory() {
		return new MultiLoginBrowserProfileFactory();
	}

	@Bean(name = "storageService")
	IStorageService getStorageService() {
		return new FileStorageService();
	}

	@Bean(name = "TaskServiceFactory")
	TaskOperatorFactory getTaskServiceFactory() {
		return new TaskOperatorFactory();
	}

	@Bean(name = "RecordingProxyFactory")
	RecordingProxyFactory getRecorderFactory() {
		return new RecordingProxyFactory();
	}

	@Bean(name = "SelectingHelper")
	public SelectingHelper getSelectingHelper() {
		return new SelectingHelper();
	}

	@Bean(name = "RunJobHandler", initMethod = "setup")
	public JobDispatcher getRunJobHandler() {
		return new JobDispatcher();
	}

	@Bean(name = "tokenGenerator")
	TokenGenerator getTokenGenerator() {
		return new TokenGenerator();
	}

	@Bean(name = "TokenRequestInterceptor")
	TokenRequestInterceptor requestInterceptor() {
		return new TokenRequestInterceptor();
	}

	@Bean("ServiceRestTemplate")
	public RestTemplate restTemplate(TokenRequestInterceptor requestInterceptor) {
		// In Java code, it is like this:
		// objectMapper.configure(com.fasterxml.jackson.databind.DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES,
		// false);

		RestTemplate restTemplate = new RestTemplateBuilder().build();
		List<HttpMessageConverter<?>> converters = restTemplate.getMessageConverters();

		for (Object converter : converters) {
			if (converter instanceof MappingJackson2HttpMessageConverter) {
				MappingJackson2HttpMessageConverter jconverter = (MappingJackson2HttpMessageConverter) converter;
				ObjectMapper mapper = jconverter.getObjectMapper();
				mapper.configure(DeserializationFeature.ACCEPT_SINGLE_VALUE_AS_ARRAY, true);
			}
		}

		List<ClientHttpRequestInterceptor> interceptors = restTemplate.getInterceptors();
		if (CollectionUtils.isEmpty(interceptors)) {
			interceptors = new ArrayList<>();
		}
		interceptors.add(requestInterceptor);
		restTemplate.setInterceptors(interceptors);

		return restTemplate;
	}

	@Bean(name = "MultiLoginDefaultTemplateLoader", initMethod = "init")
	MultiLoginDefaultTemplateLoader getMultiLoginDefaultTemplateLoader() {
		return MultiLoginDefaultTemplateLoader.getInstance();
	}

	@Bean(name = "AntiCaptchaRestTemplate")
	public RestTemplate antiCaptchaRestTemplate() {
		// In Java code, it is like this:
		// objectMapper.configure(com.fasterxml.jackson.databind.DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES,
		// false);
		RestTemplate restTemplate = new RestTemplateBuilder()
				.setConnectTimeout(Duration.ofSeconds(connectionTimeoutSeconds))
				.setReadTimeout(Duration.ofSeconds(requestTimeoutSeconds)).build();

		List<HttpMessageConverter<?>> converters = restTemplate.getMessageConverters();
		for (Object converter : converters) {
			if (converter instanceof MappingJackson2HttpMessageConverter) {
				MappingJackson2HttpMessageConverter jconverter = (MappingJackson2HttpMessageConverter) converter;
				ObjectMapper mapper = jconverter.getObjectMapper();
				mapper.configure(DeserializationFeature.ACCEPT_SINGLE_VALUE_AS_ARRAY, true);
			}
		}

		return restTemplate;
	}

	@Bean(name = "AntiCaptchaService")
	public AntiCaptchaService getAntiCaptchaService() {
		return new AntiCaptchaService();
	}

	@Bean(name = "ProxyDecoratorFactory")
	public ProxyDecoratorFactory getProxyDecoratorFactory() {
		return new ProxyDecoratorFactory();
	}

	@Bean(name = "UrlVerificatorFactory")
	UrlVerificatorFactory getUrlVerificatorFactory() {
		return new UrlVerificatorFactory();
	}

	@Bean(name = "KeepAlivePingFactory")
	KeepAlivePingFactory getKeepAlivePingFactory() {
		return new KeepAlivePingFactory();
	}

	@Bean(name = "LambdaCallerFactory")
	public LambdaCallerFactory getLambdaCallerFactory() {
		return new LambdaCallerFactory();
	}

	@Bean(name = "RecordingProxy")
	public IRecorderProxy getRecordingProxy() {
		// return new NoRecordingProxy();
		return new WireMockRecorderProxy();
		// return new BrowserUpRecorderProxy();
	}

	@Bean(name = "TaskExecutor", initMethod = "setup")
	TaskExecutor getTaskExecutor() {
		return new TaskExecutor();
	}

	@Bean(name = "jobListRunnerFactory")
	JobListRunnerFactory getJobListRunnerFactory() {
		return new JobListRunnerFactory();
	}

}
