package primemover.runner;

import org.springframework.beans.BeansException;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.ApplicationContext;
import org.springframework.context.ApplicationContextAware;

import primemover.config.StartupMode;
import primemover.model.Crawler;

public class TaskOperatorFactory implements ApplicationContextAware {

	@Value("${global.mode}")
	private StartupMode mode;
	private ApplicationContext ctx;
	private static TaskOperatorFactory instance = new TaskOperatorFactory();

	public ITaskOperator getItem(Crawler crawler) {
		ITaskOperator ret = null;
		switch (mode) {
		case DEVELOP:
			ret = new TaskOperator();
			break;
		case INTEGRATION:
			ret = new TaskOperator();
			break;
		case PRODUCTION:
			ret = new TaskOperator();
			break;
		default:
			throw new IllegalStateException("Invalid argument for application mode");
		// break;
		}

		ctx.getAutowireCapableBeanFactory().autowireBean(ret);
		ret.setup(crawler);

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
