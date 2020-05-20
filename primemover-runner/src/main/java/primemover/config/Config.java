package primemover.config;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.SpringBootConfiguration;
import org.springframework.context.annotation.Bean;

import primemover.service.ITaskService;
import primemover.service.MockTaskService;
import primemover.service.TaskService;

@SpringBootConfiguration
public class Config {
	
	@Value("${global.mode}")
	private String mode;
	

	@Bean
	ITaskService getAgent() {
		ITaskService ret = null;
		
		switch (mode) {
		case "DEBUG":
			ret = new MockTaskService();
			break;
			
		case "PROD":
			ret = new TaskService();
			break;

		default:
			throw new IllegalStateException("Invalid argument for application mode");
			//break;
		}
		
		return ret;
	}
}
