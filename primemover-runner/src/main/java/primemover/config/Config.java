package primemover.config;

import java.util.List;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.SpringBootConfiguration;
import org.springframework.boot.web.client.RestTemplateBuilder;
import org.springframework.context.annotation.Bean;
import org.springframework.http.converter.HttpMessageConverter;
import org.springframework.http.converter.json.MappingJackson2HttpMessageConverter;
import org.springframework.web.client.RestTemplate;

import com.fasterxml.jackson.databind.DeserializationFeature;

import primemover.capture.RecordingProxyFactory;
import primemover.runner.IJobManager;
import primemover.runner.JobManager;
import primemover.runner.TaskOperatorFactory;
import primemover.service.AgentService;
import primemover.service.BackendStorageService;
import primemover.service.CrawlerService;
import primemover.service.FileStorageService;
import primemover.service.ICrawlerService;
import primemover.service.IStorageService;

@SpringBootConfiguration
public class Config {
	
	@Value("${global.mode}")
	private String mode;
	
	public String getMode() {
		return mode;
	}
	
	@Bean(name="fileStorageService")
	IStorageService getFileStorageServices() {
		return new FileStorageService();
	}
	
	@Bean(name="backendStorageService")
	IStorageService getBackendStorageServices() {
		return new BackendStorageService();
	}
	
	@Bean(name="AgentService")
	AgentService getAgentService() {
		return new AgentService();
	}
	
	@Bean("CrawlerService")
	ICrawlerService getCrawlerService() {
		return new CrawlerService();
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
	
	@Bean
	public RestTemplate restTemplate() {
		//In Java code, it is like this: objectMapper.configure(com.fasterxml.jackson.databind.DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false);
		
		RestTemplate restTemplate = new RestTemplateBuilder().build();
		List<HttpMessageConverter<?>> converters = restTemplate.getMessageConverters();
		
		for (Object converter : converters) {
			if (converter instanceof MappingJackson2HttpMessageConverter) {
				MappingJackson2HttpMessageConverter jconverter = (MappingJackson2HttpMessageConverter) converter;
				jconverter.getObjectMapper().configure(DeserializationFeature.ACCEPT_SINGLE_VALUE_AS_ARRAY, true);
			}
		}
         return restTemplate; 
	}
}
