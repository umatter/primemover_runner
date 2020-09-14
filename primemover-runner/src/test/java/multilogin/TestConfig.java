package multilogin;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.SpringBootConfiguration;
import org.springframework.boot.web.client.RestTemplateBuilder;
import org.springframework.context.annotation.Bean;
import org.springframework.web.client.RestTemplate;

import primemover.runner.TaskOperatorFactory;
import primemover.service.AgentService;
import primemover.service.CrawlerService;
import primemover.service.ICrawlerService;

@SpringBootConfiguration
public class TestConfig {
	
	@Value("${global.mode}")
	private String mode;
	

	@Bean(name="AgentService")
	AgentService getAgentService() {
		return new AgentService();
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
