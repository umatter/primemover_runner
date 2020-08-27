package hsg.bot.api;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.SpringBootConfiguration;
import org.springframework.boot.web.client.RestTemplateBuilder;
import org.springframework.context.annotation.Bean;
import org.springframework.web.client.RestTemplate;

import primemover.runner.TaskOperatorFactory;
import primemover.service.CrawlerService;
import primemover.service.FileStorageService;
import primemover.service.ICrawlerService;
import primemover.service.IStorageService;

@SpringBootConfiguration
public class TestConfig {
	
	@Value("${global.mode}")
	private String mode;
	
	@Bean(name="storageService")
	IStorageService getStorageService() {
		IStorageService ret = null;
		switch (mode.toUpperCase()) {
		case "DEBUG":
			ret = new FileStorageService();
			break;
		default:
			throw new IllegalStateException("Invalid argument for application mode");
			//break;
		}
		
		return ret;
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
}
