package hsg.bot.runner;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.SpringBootConfiguration;
import org.springframework.context.annotation.Bean;

import primemover.capture.RecordingProxyFactory;
import primemover.runner.TaskOperatorFactory;
import primemover.service.FileStorageService;
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

	@Bean(name="TaskServiceFactory")
	TaskOperatorFactory getTaskServiceFactory() {
		return new TaskOperatorFactory();
	}
	
	@Bean(name="RecordingProxyFactory")
	RecordingProxyFactory getRecorderFactory() {
		return new RecordingProxyFactory();
	}	
	
	/*
	@Bean(name="jobmanager", initMethod = "setup")
	JobManager getManager() {
		JobManager jobManager = new JobManager();
	
		// jobManager will be called with spring's scheduler
		return jobManager;		
	}
	*/
}
