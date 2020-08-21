package primemover.runner;

import org.springframework.beans.BeansException;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.ApplicationContext;
import org.springframework.context.ApplicationContextAware;

public class TaskOperatorFactory implements ApplicationContextAware {
	
	@Value("${global.mode}")
	private String mode;
	private ApplicationContext ctx;
	private static TaskOperatorFactory instance = new TaskOperatorFactory();
	
	public ITaskOperator getItem() {
		ITaskOperator ret = null;
		switch (mode.toUpperCase()) {
		case "DEBUG":
			ret = new MockTaskOperator();
			break;
		default:
			throw new IllegalStateException("Invalid argument for application mode");
			//break;
		}

		ctx.getAutowireCapableBeanFactory().autowireBean(ret);
		
		return ret;
	}
	
	public static TaskOperatorFactory getInstance() {
		return instance;
	}


	@Override
	public void setApplicationContext(ApplicationContext applicationContext) throws BeansException {
		this.ctx = applicationContext;
	}
	
	
}
