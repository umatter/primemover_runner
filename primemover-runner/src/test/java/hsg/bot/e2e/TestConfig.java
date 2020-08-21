package hsg.bot.e2e;

import org.springframework.boot.SpringBootConfiguration;
import org.springframework.boot.web.client.RestTemplateBuilder;
import org.springframework.context.annotation.Bean;
import org.springframework.web.client.RestTemplate;

import primemover.runner.TaskOperatorFactory;
import primemover.service.BackendStorageService;
import primemover.service.CrawlerService;
import primemover.service.ICrawlerService;
import primemover.service.IStorageService;

@SpringBootConfiguration
public class TestConfig {
	
	@Bean(name="storageService")
	IStorageService getStorageService() {
		return new BackendStorageService();
	}
	
	@Bean("CrawlerService")
	ICrawlerService getCrawlerService() {
		return new CrawlerService();
	}
	
	@Bean
	public RestTemplate restTemplate() {
		return new RestTemplateBuilder().build();
	}
	

	@Bean(name="TaskOperatorFactory")
	TaskOperatorFactory getTaskOperatorFactory() {
		return new TaskOperatorFactory();
	}
}
