package primemover.runner;

import org.springframework.beans.BeansException;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.ApplicationContext;
import org.springframework.context.ApplicationContextAware;

import primemover.config.StartupMode;

public class TaskOperatorFactory implements ApplicationContextAware {
	
	@Value("${global.mode}")
	private  StartupMode mode;
	private ApplicationContext ctx;
	private static TaskOperatorFactory instance = new TaskOperatorFactory();
	
	public ITaskOperator getItem() {
		ITaskOperator ret = null;
		switch (mode) {
		case DEVELOP:
			ret = new MockTaskOperator();
			break;
		case INTEGRATION:
			ret = new MockTaskOperator();
			break;
		case PRODUCTION:
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
