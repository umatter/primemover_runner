package geosurf;

import java.time.Duration;
import java.util.ArrayList;
import java.util.List;

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

import primemover.capture.RecordingProxyFactory;
import primemover.runner.JobListRunnerFactory;
import primemover.runner.MockJobManager;
import primemover.runner.TaskOperatorFactory;
import primemover.service.AgentService;
import primemover.service.BackendStorageService;
import primemover.service.CrawlerService;
import primemover.service.FileStorageService;
import primemover.service.ICrawlerService;
import primemover.service.IStorageService;
import primemover.service.JobService;
import primemover.service.TokenGenerator;
import primemover.service.TokenRequestInterceptor;
import primemover.util.MultiLoginBrowserProfileFactory;

@SpringBootConfiguration
public class TestConfig {
	@Bean(name="jobmanager", initMethod = "setup")
	MockJobManager getManager() {
		return new MockJobManager();	
	}
	
	@Bean(name="jobListRunnerFactory")
	JobListRunnerFactory getJobListRunnerFactory() {
		return new JobListRunnerFactory();
	}
	
	@Bean(name="JobService")
	JobService getJobService() {
		return new JobService();
	}

	@Bean(name="AgentService")
	AgentService getAgentService() {
		return new AgentService();
	}
	
	@Bean(name="MultiLoginBrowserProfileFactory")
	MultiLoginBrowserProfileFactory getMultiLoginBrowserProfileFactory() {
		return new MultiLoginBrowserProfileFactory();
	}
	
	@Bean(name="fileStorageService")
	IStorageService getFileStorageServices() {
		return new FileStorageService();
	}
	
	@Bean(name="backendStorageService")
	IStorageService getBackendStorageServices() {
		return new BackendStorageService();
	}
	
	@Bean("CrawlerService")
	ICrawlerService getCrawlerService() {
		return new CrawlerService();
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
	
	@Bean(name="TaskOperatorFactory")
	TaskOperatorFactory getTaskOperatorFactory() {
		return new TaskOperatorFactory();
	}
	
	@Bean(name="RecordingProxyFactory")
	RecordingProxyFactory getRecorderFactory() {
		return new RecordingProxyFactory();
	}	
}
