package primemover.config;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.SpringBootConfiguration;
import org.springframework.boot.web.client.RestTemplateBuilder;
import org.springframework.context.annotation.Bean;
import org.springframework.web.client.RestTemplate;

import primemover.runner.IJobManager;
import primemover.runner.ITaskOperator;
import primemover.runner.JobManager;
import primemover.runner.MockTaskOperator;
import primemover.runner.TaskOperator;
import primemover.runner.TaskOperatorFactory;
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
	
	@Bean(name="storageService")
	IStorageService getStorageService() {
		IStorageService ret = null;
		switch (mode.toUpperCase()) {
		case "DEBUG":
			ret = new FileStorageService();
			break;
		case "INT":
			ret = new BackendStorageService();
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

	@Bean(name="TaskServiceFactory")
	TaskOperatorFactory getTaskOperatorFactory() {
		return new TaskOperatorFactory();
	}
	
	@Bean(name="jobmanager", initMethod = "setup")
	IJobManager getManager() {
		IJobManager jobManager = new JobManager();
	
		// jobManager will be called with spring's scheduler
		return jobManager;		
	}
	
	@Bean
	public RestTemplate restTemplate() {
		return new RestTemplateBuilder().build();
	}
}
