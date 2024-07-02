package primemover.lambda;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.BeansException;
import org.springframework.context.ApplicationContext;
import org.springframework.context.ApplicationContextAware;

public class LambdaCallerFactory implements ApplicationContextAware {
	private static final Logger LOGGER = LoggerFactory.getLogger(LambdaCallerFactory.class);

	private ApplicationContext ctx;
	private boolean isInit = false;

	@Override
	public void setApplicationContext(ApplicationContext applicationContext) throws BeansException {
		LOGGER.debug("setting application context");
		this.ctx = applicationContext;

		isInit = true;
	}

	public LambdaCaller getInstance() {
		if (!isInit) {
			throw new IllegalStateException("set the appliation context before usage.");
		}

		LambdaCaller out = new LambdaCaller();
		ctx.getAutowireCapableBeanFactory().autowireBean(out);

		return out;
	}

}
