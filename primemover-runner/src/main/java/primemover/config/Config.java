package primemover.config;

import java.time.Duration;
import java.util.List;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.SpringBootConfiguration;
import org.springframework.boot.web.client.RestTemplateBuilder;
import org.springframework.context.annotation.Bean;
import org.springframework.http.converter.HttpMessageConverter;
import org.springframework.http.converter.json.MappingJackson2HttpMessageConverter;
import org.springframework.web.client.RestTemplate;

import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.ObjectMapper;

import primemover.capture.RecordingProxyFactory;
import primemover.runner.IJobManager;
import primemover.runner.JobListRunnerFactory;
import primemover.runner.JobManager;
import primemover.runner.TaskOperatorFactory;
import primemover.service.AgentService;
import primemover.service.BackendStorageService;
import primemover.service.CrawlerService;
import primemover.service.ICrawlerService;
import primemover.service.IStorageService;
import primemover.service.JobService;
import primemover.service.OutletService;
import primemover.service.TermsService;
import primemover.util.MultiLoginBrowserProfileFactory;
import primemover.util.MultiLoginDefaultTemplateLoader;

@SpringBootConfiguration
public class Config {
	private static Logger logger = LoggerFactory.getLogger(JobManager.class);
	
	@Value("${global.mode}")
	private String mode;
	
	@Value("${backend.requestTimeoutSeconds:60}")
	private Long requestTimeoutSeconds;
	
	public String getMode() {
		return mode;
	}
	
	@Bean(name="jobListRunnerFactory")
	JobListRunnerFactory getJobListRunnerFactory() {
		return new JobListRunnerFactory();
	}
	
	/*
	@Bean(name="fileStorageService")
	IStorageService getFileStorageServices() {
		return new FileStorageService();
	}
	*/
	
	@Bean(name="backendStorageService")
	IStorageService getBackendStorageServices() {
		return new BackendStorageService();
	}
	
	@Bean(name="AgentService")
	AgentService getAgentService() {
		return new AgentService();
	}
	
	@Bean(name="JobService")
	JobService getJobService() {
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
	
	@Bean("TermsService")
	TermsService getTermsService() {
		return new TermsService();
	}

	@Bean(name="TaskOperatorFactory")
	TaskOperatorFactory getTaskOperatorFactory() {
		return new TaskOperatorFactory();
	}
	
	@Bean(name="RecordingProxyFactory")
	RecordingProxyFactory getRecorderFactory() {
		return new RecordingProxyFactory();
	}	
	
	@Bean(name="jobmanager", initMethod = "setup")
	IJobManager getManager() {
		IJobManager jobManager = new JobManager();
	
		// jobManager will be called with spring's scheduler
		return jobManager;		
	}
	
	@Bean(name="MultiLoginDefaultTemplateLoader", initMethod="init")
	MultiLoginDefaultTemplateLoader getMultiLoginDefaultTemplateLoader() {
		return MultiLoginDefaultTemplateLoader.getInstance();
	}
	
	@Bean(name="MultiLoginBrowserProfileFactory")
	MultiLoginBrowserProfileFactory getMultiLoginBrowserProfileFactory() {
		return new MultiLoginBrowserProfileFactory();
	}
	
	
	@Bean(name="ServiceRestTemplate")
	public RestTemplate restTemplate() {
		//In Java code, it is like this: objectMapper.configure(com.fasterxml.jackson.databind.DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false);
		
		RestTemplate restTemplate = new RestTemplateBuilder()
				.setConnectTimeout(Duration.ofSeconds(10))
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
}
