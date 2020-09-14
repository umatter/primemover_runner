package hsg.bot.api;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.SpringBootConfiguration;
import org.springframework.boot.web.client.RestTemplateBuilder;
import org.springframework.context.annotation.Bean;
import org.springframework.web.client.RestTemplate;

import primemover.capture.RecordingProxyFactory;
import primemover.runner.TaskOperatorFactory;
import primemover.service.CrawlerService;
import primemover.service.FileStorageService;
import primemover.service.ICrawlerService;
import primemover.service.IStorageService;

@SpringBootConfiguration
public class TestConfig {
	
	@Value("${global.mode:DEVELOP}")
	private String mode;
	
	@Bean(name="storageService")
	IStorageService getStorageService() {
		return new FileStorageService();
	}
	
	@Bean("CrawlerService")
	ICrawlerService getCrawlerService() {
		return new CrawlerService();
	}
	
	@Bean
	public RestTemplate restTemplate() {
		return new RestTemplateBuilder().build();
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
