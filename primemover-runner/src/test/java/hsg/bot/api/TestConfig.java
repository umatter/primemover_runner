package hsg.bot.api;

import java.util.List;

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
import primemover.runner.TaskOperatorFactory;
import primemover.service.AgentService;
import primemover.service.CrawlerService;
import primemover.service.FileStorageService;
import primemover.service.IStorageService;
import primemover.service.JobService;
import primemover.service.MockCrawlerService;
import primemover.service.OutletService;
import primemover.service.TermsService;
import primemover.util.MultiLoginBrowserProfileFactory;

@SpringBootConfiguration
public class TestConfig {
	
	@Value("${global.mode:DEVELOP}")
	private String mode;
	
	@Bean(name="storageService")
	IStorageService getStorageService() {
		return new FileStorageService();
	}
	
	@Bean(name="JobService")
	JobService getJobService() {
		return new JobService();
	}
	
	
	@Bean("CrawlerService")
	CrawlerService getCrawlerService() {
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
	
	@Bean(name="MultiLoginBrowserProfileFactory")
	MultiLoginBrowserProfileFactory getMultiLoginBrowserProfileFactory() {
		return new MultiLoginBrowserProfileFactory();
	}
	
	@Bean(name="jsonMapper")
	ObjectMapper getJsonMapper() {
		return new ObjectMapper();
	}
	
	@Bean("MockCrawlerService")
	MockCrawlerService getMockCrawlerService() {
		return new MockCrawlerService();
	}
	
	@Bean(name="AgentService")
	AgentService getAgentService() {
		return new AgentService();
	}
	
	@Bean("rest Template")
	public RestTemplate restTemplate() {
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
         return restTemplate; 
	}

	@Bean(name="TaskServiceFactory")
	TaskOperatorFactory getTaskServiceFactory() {
		return new TaskOperatorFactory();
	}
	
	@Bean(name="RecordingProxyFactory")
	RecordingProxyFactory getRecorderFactory() {
		return new RecordingProxyFactory();
	}	
}
