package primemover.service;

import org.springframework.beans.BeansException;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.ApplicationContext;
import org.springframework.context.ApplicationContextAware;

public class TaskServiceFactory implements ApplicationContextAware {
	
	@Value("${global.mode}")
	private String mode;
	private ApplicationContext ctx;
	private static TaskServiceFactory instance = new TaskServiceFactory();
	
	public ITaskService getItem() {
		ITaskService ret = null;
		switch (mode.toUpperCase()) {
		case "DEBUG":
			ret = new MockTaskService();
			break;
		default:
			throw new IllegalStateException("Invalid argument for application mode");
			//break;
		}

		ctx.getAutowireCapableBeanFactory().autowireBean(ret);
		
		return ret;
	}
	
	public static TaskServiceFactory getInstance() {
		return instance;
	}


	@Override
	public void setApplicationContext(ApplicationContext applicationContext) throws BeansException {
		this.ctx = applicationContext;
	}
	
	
}
