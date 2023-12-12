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

	public ITaskOperator getInstance(Crawler crawler) {
		ITaskOperator ret = new TaskOperator();
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
