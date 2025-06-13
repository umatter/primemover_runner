package primemover.config;

import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.module.SimpleModule;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.SpringBootConfiguration;
import org.springframework.boot.web.client.RestTemplateBuilder;
import org.springframework.context.annotation.Bean;
import org.springframework.http.client.ClientHttpRequestInterceptor;
import org.springframework.http.converter.HttpMessageConverter;
import org.springframework.http.converter.json.MappingJackson2HttpMessageConverter;
import org.springframework.retry.backoff.FixedBackOffPolicy;
import org.springframework.retry.policy.SimpleRetryPolicy;
import org.springframework.retry.support.RetryTemplate;
import org.springframework.util.CollectionUtils;
import org.springframework.web.client.RestTemplate;
import primemover.anticaptcha.CaptchaSolverFactory;
import primemover.anticaptcha.service.AntiCaptchaService;
import primemover.api.deserializer.RelaxedZonedDateTimeDeserializer;
import primemover.capture.*;
import primemover.lambda.LambdaCallerFactory;
import primemover.multilogin.ml6.MultiLoginBrowserProfileFactory;
import primemover.multilogin.ml6.MultiLoginDefaultTemplateLoader;
import primemover.runner.ITaskManager;
import primemover.runner.TaskExecutor;
import primemover.runner.TaskManager;
import primemover.runner.TaskOperatorFactory;
import primemover.runner.job.IJobDispatcher;
import primemover.runner.job.JobDispatcher;
import primemover.runner.joblist.JobListRunnerFactory;
import primemover.service.*;
import primemover.service.datawriter.*;
import primemover.service.token.TokenGenerator;
import primemover.service.token.TokenRequestInterceptor;
import primemover.util.OutletsProvider;
import primemover.util.SelectingHelper;

import java.time.Duration;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.TimeZone;

@SpringBootConfiguration
public class Config {
	private static Logger logger = LoggerFactory.getLogger(Config.class);

	@Value("${backend.requestTimeoutSeconds:60}")
	private Long requestTimeoutSeconds;

	@Value("${backend.connectionTimeoutSeconds:10}")
	private Long connectionTimeoutSeconds;

	@Bean
	public RetryTemplate retryTemplate() {
		RetryTemplate retryTemplate = new RetryTemplate();

		FixedBackOffPolicy fixedBackOffPolicy = new FixedBackOffPolicy();
		fixedBackOffPolicy.setBackOffPeriod(2000l);
		retryTemplate.setBackOffPolicy(fixedBackOffPolicy);

		SimpleRetryPolicy retryPolicy = new SimpleRetryPolicy();
		retryPolicy.setMaxAttempts(3);
		retryTemplate.setRetryPolicy(retryPolicy);

		return retryTemplate;
	}

	@Bean(name = "jobListRunnerFactory")
	JobListRunnerFactory getJobListRunnerFactory() {
		return new JobListRunnerFactory();
	}

	@Bean(name = "UrlVerificatorFactory")
	UrlVerificatorFactory getUrlVerificatorFactory() {
		return new UrlVerificatorFactory();
	}

	@Bean(name = "KeepAlivePingFactory")
	KeepAlivePingFactory getKeepAlivePingFactory() {
		return new KeepAlivePingFactory();
	}

	@Bean(name = "TaskExecutor", initMethod = "setup")
	TaskExecutor getTaskExecutor() {
		return new TaskExecutor();
	}

	@Bean(name = "tokenGenerator")
	TokenGenerator getTokenGenerator() {
		return new TokenGenerator();
	}

	@Bean(name = "TokenRequestInterceptor")
	TokenRequestInterceptor requestInterceptor() {
		return new TokenRequestInterceptor();
	}

	@Bean(name = "ServiceRestTemplate")
	public RestTemplate restServiceTemplate(TokenRequestInterceptor requestInterceptor) {
		// In Java code, it is like this:
		// objectMapper.configure(com.fasterxml.jackson.databind.DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES,
		// false);
		logger.debug("setting up ServiceRestTemplate - Defaults");
		RestTemplate restTemplate = new RestTemplateBuilder()
				.connectTimeout(Duration.ofSeconds(connectionTimeoutSeconds))
				.readTimeout(Duration.ofSeconds(requestTimeoutSeconds)).build();

		List<HttpMessageConverter<?>> converters = restTemplate.getMessageConverters();
		for (Object converter : converters) {
			if (converter instanceof MappingJackson2HttpMessageConverter) {
				MappingJackson2HttpMessageConverter jconverter = (MappingJackson2HttpMessageConverter) converter;
				ObjectMapper mapper = jconverter.getObjectMapper();
				mapper.configure(DeserializationFeature.ACCEPT_SINGLE_VALUE_AS_ARRAY, true);
				mapper.setTimeZone(TimeZone.getDefault());

				SimpleModule module = new SimpleModule();
				module.addDeserializer(ZonedDateTime.class,
						new RelaxedZonedDateTimeDeserializer(ZoneId.systemDefault()));
				mapper.registerModule(module);
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
		logger.debug("setting up AntiCaptchaRestTemplate - Defaults");
		RestTemplate restTemplate = new RestTemplateBuilder()
				.connectTimeout(Duration.ofSeconds(connectionTimeoutSeconds))
				.readTimeout(Duration.ofSeconds(requestTimeoutSeconds)).build();

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

	@Bean(name = "CaptchaSolverFactory")
	public CaptchaSolverFactory getCaptchaSolverFactory() {
		return new CaptchaSolverFactory();
	}

	@Bean(name = "IpServiceFactory")
	public IpServiceFactory getIpServiceFactory() {
		return new IpServiceFactory();
	}

	@Value("${awsS3.jobdata.bucketName}")
	private String awsJobDataBucketName;
	@Value("${awsS3.jobdata.subFolder}")
	private String awsJobDataSubfolder;

	@Bean(name = "AwsS3PageDataStorageService", initMethod = "init")
	IStorageService getAwsS3PageDataStorageServices() {
		AwsS3StorageService jobDataSerivce = new AwsS3StorageService(awsJobDataBucketName, awsJobDataSubfolder);
		jobDataSerivce.setWriteSelector((writer) -> !WriterUtil.isPageSourceWriter(writer)); // don't write
																								// PageSource-data
		return jobDataSerivce;
	}

	@Value("${awsS3.finalsource.bucketName}")
	private String awsFinalSourceBucketName;
	@Value("${awsS3.finalsource.subFolder}")
	private String awsFinalSourceSubfolder;

	@Bean(name = "AwsS3FinalSourceStorageService", initMethod = "init")
	IStorageService getAwsS3FinalSourceStorageServices() {
		AwsS3StorageService jobDataSerivce = new AwsS3StorageService(awsFinalSourceBucketName, awsFinalSourceSubfolder);
		jobDataSerivce.setWriteSelector((writer) -> WriterUtil.isPageSourceWriter(writer)); // only write
																							// PageSource-data
		return jobDataSerivce;
	}

	@Bean(name = "AgentService")
	AgentService getAgentService() {
		return new AgentService();
	}

	@Bean(name = "JobService")
	IJobService getJobService() {
		return new JobService();
	}

	@Bean("CrawlerService")
	ICrawlerService getCrawlerService() {
		return new CrawlerService();
	}

	@Bean("OutletService")
	OutletService getOutletService() {
		return new OutletService();
	}

	@Bean(name = "OutletsProvider", initMethod = "refresh")
	OutletsProvider getOutletsProvider() {
		return new OutletsProvider();
	}

	@Bean("TermsService")
	TermsService getTermsService() {
		return new TermsService();
	}

	@Bean(name = "TaskOperatorFactory")
	TaskOperatorFactory getTaskOperatorFactory() {
		return new TaskOperatorFactory();
	}

	@Bean(name = "RecordingProxyFactory")
	RecordingProxyFactory getRecorderFactory() {
		return new RecordingProxyFactory();
	}

	@Bean(name = "jobmanager", initMethod = "setup")
	ITaskManager getManager() {
		ITaskManager jobManager = new TaskManager();

		// jobManager will be called with spring's scheduler
		return jobManager;
	}

	@Bean(name = "MultiLoginDefaultTemplateLoader", initMethod = "init")
	MultiLoginDefaultTemplateLoader getMultiLoginDefaultTemplateLoader() {
		return MultiLoginDefaultTemplateLoader.getInstance();
	}

	@Bean(name = "MultiLoginBrowserProfileFactory")
	MultiLoginBrowserProfileFactory getMultiLoginBrowserProfileFactory() {
		return new MultiLoginBrowserProfileFactory();
	}

	@Bean(name = "SelectingHelper")
	public SelectingHelper getSelectingHelper() {
		return new SelectingHelper();
	}

	@Bean(name = "RunJobHandler", initMethod = "setup")
	public IJobDispatcher getRunJobHandler() {
		return new JobDispatcher();
	}

	@Bean(name = "PageSourceWriterFactory")
	public IDataWriterFactory getPageSourceWriterFactory() {
		return new PageSourceWriterFactory();
	}

	@Bean(name = "JobDataWriterFactory")
	public IDataWriterFactory getJobDataWriterFactory() {
		return new JobDataWriterFactory();
	}

	@Bean(name = "CaptchaDetectionWriterFactory")
	public IDataWriterFactory getCaptchaDetectionWriterFactory() {
		return new CaptchaDetectionWriterFactory();
	}

	@Bean(name = "ProxyDecoratorFactory")
	public ProxyDecoratorFactory getProxyDecoratorFactory() {
		return new ProxyDecoratorFactory();
	}

	@Bean(name = "LambdaCallerFactory")
	public LambdaCallerFactory getLambdaCallerFactory() {
		return new LambdaCallerFactory();
	}

	@Bean(name = "RecordingProxy")
	public IRecorderProxy getRecordingProxy() {
		return new NoRecordingProxy();
		// return new WireMockRecorderProxy();
		// return new BrowserUpRecorderProxy();
	}
}
