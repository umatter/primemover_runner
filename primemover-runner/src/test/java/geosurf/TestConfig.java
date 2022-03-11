package geosurf;

import java.time.Duration;
import java.util.ArrayList;
import java.util.List;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.SpringBootConfiguration;
import org.springframework.boot.web.client.RestTemplateBuilder;
import org.springframework.context.annotation.Bean;
import org.springframework.http.client.ClientHttpRequestInterceptor;
import org.springframework.http.converter.HttpMessageConverter;
import org.springframework.http.converter.json.MappingJackson2HttpMessageConverter;
import org.springframework.util.CollectionUtils;
import org.springframework.web.client.RestTemplate;

import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.ObjectMapper;

import primemover.captcha.CaptchaSolver;
import primemover.capture.RecordingProxyFactory;
import primemover.runner.TaskOperatorFactory;
import primemover.runner.jobHandling.JobDispatcher;
import primemover.service.AgentService;
import primemover.service.FileStorageService;
import primemover.service.IStorageService;
import primemover.service.IpServiceFactory;
import primemover.service.JobService;
import primemover.service.OutletService;
import primemover.service.TermsService;
import primemover.service.TokenGenerator;
import primemover.service.TokenRequestInterceptor;
import primemover.service.anticaptcha.AntiCaptchaService;
import primemover.util.MultiLoginBrowserProfileFactory;
import primemover.util.MultiLoginDefaultTemplateLoader;
import primemover.util.OutletsProvider;
import primemover.util.SelectingHelper;

@SpringBootConfiguration
public class TestConfig {	
	
	@Value("${backend.requestTimeoutSeconds:60}")
	private Long requestTimeoutSeconds;
	
	@Value("${backend.connectionTimeoutSeconds:10}")
	private Long connectionTimeoutSeconds;

	@Value("${global.mode}")
	private String mode;
	
	@Bean(name="JobService")
	JobService getJobService() {
		return new JobService();
	}

	@Bean(name="IpServiceFactory")
	public IpServiceFactory getIpServiceFactory() {
		return new IpServiceFactory();
	}
	
	@Bean(name="CaptchaSolver")
	public CaptchaSolver getCaptchaSolver() {
		return new CaptchaSolver();
	}
	
	@Bean(name="AgentService")
	AgentService getAgentService() {
		return new AgentService();
	}
	
	@Bean("TermsService")
	TermsService getTermsService() {
		return new TermsService();
	}
	
	@Bean(name="MultiLoginBrowserProfileFactory")
	MultiLoginBrowserProfileFactory getMultiLoginBrowserProfileFactory() {
		return new MultiLoginBrowserProfileFactory();
	}
	
	@Bean(name="storageService")
	IStorageService getStorageService() {
		return new FileStorageService();
	}

	@Bean(name="TaskServiceFactory")
	TaskOperatorFactory getTaskServiceFactory() {
		return new TaskOperatorFactory();
	}
	
	@Bean(name="RecordingProxyFactory")
	RecordingProxyFactory getRecorderFactory() {
		return new RecordingProxyFactory();
	}	
	
	/*
	@Bean(name="jobmanager", initMethod = "setup")
	JobManager getManager() {
		JobManager jobManager = new JobManager();
	
		// jobManager will be called with spring's scheduler
		return jobManager;		
	}
	*/
	@Bean("OutletService")
	OutletService getOutletService() {
		return new OutletService();
	}
	
	@Bean(name="OutletsProvider", initMethod = "refresh")
	OutletsProvider getOutletsProvider() {
		return new OutletsProvider();
	}
	
	@Bean(name="SelectingHelper")
	public SelectingHelper getSelectingHelper() {
		return new SelectingHelper();
	}
	

	@Bean(name="RunJobHandler")
	public JobDispatcher getRunJobHandler() {
		return new JobDispatcher();
	}

	@Bean(name="tokenGenerator")
	TokenGenerator getTokenGenerator() {
		return new TokenGenerator();
	}
	
	@Bean(name="TokenRequestInterceptor")	
	TokenRequestInterceptor requestInterceptor() {
		return new TokenRequestInterceptor();
	}
	@Bean("ServiceRestTemplate")
	public RestTemplate restTemplate(TokenRequestInterceptor requestInterceptor) {
		//In Java code, it is like this: objectMapper.configure(com.fasterxml.jackson.databind.DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false);
		
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
	
	@Bean(name="MultiLoginDefaultTemplateLoader", initMethod="init")
	MultiLoginDefaultTemplateLoader getMultiLoginDefaultTemplateLoader() {
		return MultiLoginDefaultTemplateLoader.getInstance();
	}
	
	@Bean(name="AntiCaptchaRestTemplate")
	public RestTemplate antiCaptchaRestTemplate() {
		//In Java code, it is like this: objectMapper.configure(com.fasterxml.jackson.databind.DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false);
		RestTemplate restTemplate = new RestTemplateBuilder()
				.setConnectTimeout(Duration.ofSeconds(connectionTimeoutSeconds))
				.setReadTimeout(Duration.ofSeconds(requestTimeoutSeconds))
				.build();
		
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
	
	@Bean(name="AntiCaptchaService")
	public AntiCaptchaService getAntiCaptchaService() {
		return new AntiCaptchaService();
	}
}
