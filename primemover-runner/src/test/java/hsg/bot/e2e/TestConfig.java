package hsg.bot.e2e;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.net.Proxy;
import java.time.Duration;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.SpringBootConfiguration;
import org.springframework.boot.web.client.RestTemplateBuilder;
import org.springframework.context.annotation.Bean;
import org.springframework.http.client.BufferingClientHttpRequestFactory;
import org.springframework.http.client.ClientHttpRequestInterceptor;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.http.converter.HttpMessageConverter;
import org.springframework.http.converter.json.MappingJackson2HttpMessageConverter;
import org.springframework.util.CollectionUtils;
import org.springframework.web.client.RestTemplate;

import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.ObjectMapper;

import primemover.anticaptcha.CaptchaSolverFactory;
import primemover.anticaptcha.service.AntiCaptchaService;
import primemover.capture.KeepAlivePingFactory;
import primemover.capture.ProxyDecoratorFactory;
import primemover.capture.RecordingProxy;
import primemover.capture.RecordingProxyFactory;
import primemover.capture.UrlVerificatorFactory;
import primemover.lambda.LambdaCallerFactory;
import primemover.model.LocationInfo;
import primemover.multilogin.MultiLoginBrowserProfileFactory;
import primemover.multilogin.MultiLoginDefaultTemplateLoader;
import primemover.runner.TaskExecutor;
import primemover.runner.TaskOperatorFactory;
import primemover.runner.job.JobDispatcher;
import primemover.runner.joblist.JobListRunnerFactory;
import primemover.service.AgentService;
import primemover.service.AwsS3StorageService;
import primemover.service.CrawlerService;
import primemover.service.FileStorageService;
import primemover.service.ICrawlerService;
import primemover.service.IJobService;
import primemover.service.IStorageService;
import primemover.service.IpService;
import primemover.service.IpServiceFactory;
import primemover.service.OutletService;
import primemover.service.TermsService;
import primemover.service.datawriter.WriterUtil;
import primemover.service.token.TokenGenerator;
import primemover.service.token.TokenRequestInterceptor;
import primemover.util.LoggingInterceptor;
import primemover.util.OutletsProvider;
import primemover.util.SelectingHelper;

@SpringBootConfiguration
public class TestConfig {

	@Value("${global.mode}")
	private String mode;

	@Value("${backend.requestTimeoutSeconds:60}")
	private Long requestTimeoutSeconds;

	@Value("${backend.connectionTimeoutSeconds:10}")
	private Long connectionTimeoutSeconds;

	@Value("${awsS3.finalsource.bucketName}")
	private String awsFinalSourceBucketName;
	@Value("${awsS3.finalsource.subFolder}")
	private String awsFinalSourceSubfolder;

	@Bean(name = "TaskExecutor", initMethod = "setup")
	TaskExecutor getTaskExecutor() {
		return new TaskExecutor();
	}

	@Bean(name = "jobListRunnerFactory")
	JobListRunnerFactory getJobListRunnerFactory() {
		return new JobListRunnerFactory();
	}

	@Bean(name = "AwsS3FinalSourceStorageService", initMethod = "init")
	IStorageService getAwsS3FinalSourceStorageServices() {
		AwsS3StorageService jobDataSerivce = new AwsS3StorageService(awsFinalSourceBucketName, awsFinalSourceSubfolder);
		jobDataSerivce.setWriteSelector((writer) -> WriterUtil.isPageSourceWriter(writer));
		return jobDataSerivce;
	}

	@Bean(name = "JobService")
	IJobService getJobService() {

		IJobService mock = mock(IJobService.class);
		when(mock.updateCaptchaStateOnJob(any(), any(), any())).thenReturn(true);
		when(mock.updateJoblistState(any(), any(), any(), any(), any(), any())).thenReturn(true);
		when(mock.updateJobState(any(), any(), any(), any(), any(), any())).thenReturn(true);

		return mock;
	}

	private IpService createIpServiceMock() {
		IpService mock = mock(IpService.class);
		when(mock.getLocationInfo()).thenReturn(new LocationInfo());
		return mock;
	}

	@Bean(name = "IpServiceFactory")
	public IpServiceFactory getIpServiceFactory() {
		IpService mockIpService = createIpServiceMock();

		IpServiceFactory mockIpServiceFactory = mock(IpServiceFactory.class);
		when(mockIpServiceFactory.getInstance((Proxy) any(), any())).thenReturn(mock(IpService.class));
		when(mockIpServiceFactory.getInstance((RecordingProxy) any())).thenReturn(mockIpService);

		return mockIpServiceFactory;
	}

	@Bean(name = "AgentService")
	AgentService getAgentService() {
		return new AgentService();
	}

	@Bean(name = "CaptchaSolverFactory")
	public CaptchaSolverFactory getCaptchaSolverFactory() {
		return new CaptchaSolverFactory();
	}

	@Bean("CrawlerService")
	ICrawlerService getCrawlerService() {
		return new CrawlerService();
	}

	@Bean("TermsService")
	TermsService getTermsService() {
		return new TermsService();
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

	/*
	 * @Bean(name="jobmanager", initMethod = "setup") JobManager getManager() {
	 * JobManager jobManager = new JobManager();
	 * 
	 * // jobManager will be called with spring's scheduler return jobManager; }
	 */
	@Bean("OutletService")
	OutletService getOutletService() {
		return new OutletService();
	}

	@Bean(name = "OutletsProvider", initMethod = "refresh")
	OutletsProvider getOutletsProvider() {

		OutletsProvider mockProvider = mock(OutletsProvider.class);
		when(mockProvider.getOutletsMapByDomain()).thenReturn(new HashMap<>());

		return mockProvider;
	}

	@Bean(name = "SelectingHelper")
	public SelectingHelper getSelectingHelper() {
		return new SelectingHelper();
	}

	@Bean(name = "RunJobHandler", initMethod = "setup")
	public JobDispatcher getRunJobHandler() {
		return new JobDispatcher();
	}

	@Bean(name = "MultiLoginDefaultTemplateLoader", initMethod = "init")
	MultiLoginDefaultTemplateLoader getMultiLoginDefaultTemplateLoader() {
		return MultiLoginDefaultTemplateLoader.getInstance();
	}

	@Bean(name = "MultiLoginBrowserProfileFactory")
	MultiLoginBrowserProfileFactory getMultiLoginBrowserProfileFactory() {
		return new MultiLoginBrowserProfileFactory();
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

	@Bean(name = "AntiCaptchaRestTemplate")
	public RestTemplate antiCaptchaRestTemplate() {
		// In Java code, it is like this:
		// objectMapper.configure(com.fasterxml.jackson.databind.DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES,
		// false);
		RestTemplate restTemplate = new RestTemplateBuilder()
				// .setBufferRequestBody(true)
				.setConnectTimeout(Duration.ofSeconds(connectionTimeoutSeconds))
				.setReadTimeout(Duration.ofSeconds(requestTimeoutSeconds))
				.requestFactory(() -> new BufferingClientHttpRequestFactory(new SimpleClientHttpRequestFactory()))
				.build();
		// enable
		// logging.level.org.apache.http=DEBUG
		// logging.level.httpclient.wire=DEBUG
		// restTemplate.setRequestFactory(new HttpComponentsClientHttpRequestFactory());

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
		interceptors.add(new LoggingInterceptor()); // will read the buffer, so only first request is logged
		restTemplate.setInterceptors(interceptors);

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
}
